package dev.screenshotapi.infrastructure.adapters.output.ocr

import dev.screenshotapi.core.domain.entities.OcrRequest
import dev.screenshotapi.core.domain.entities.OcrResult
import dev.screenshotapi.core.domain.entities.OcrEngine
import dev.screenshotapi.core.domain.entities.OcrTier
import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.services.OcrService
import dev.screenshotapi.core.domain.services.OcrEngineCapabilities
import dev.screenshotapi.infrastructure.resilience.CircuitBreaker
import dev.screenshotapi.infrastructure.resilience.CircuitBreakerOpenException
import dev.screenshotapi.infrastructure.resilience.circuitBreaker
import dev.screenshotapi.infrastructure.services.AnalysisRetryService
import dev.screenshotapi.infrastructure.services.MetricsService
import dev.screenshotapi.core.domain.exceptions.AnalysisException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * Resilient Bedrock OCR Service with Circuit Breaker pattern
 * 
 * This service wraps the AwsBedrockOcrService with:
 * - Circuit breaker for fault tolerance
 * - Retry logic with exponential backoff
 * - Fallback mechanisms
 * - Detailed metrics and monitoring
 */
class ResilientBedrockOcrService(
    private val bedrockService: AwsBedrockOcrService,
    private val retryService: AnalysisRetryService,
    private val metricsService: MetricsService,
    private val fallbackService: OcrService? = null
) : OcrService {
    
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    // Circuit breaker specifically for AWS Bedrock
    private val circuitBreaker = circuitBreaker("aws-bedrock-ocr") {
        failureThreshold(5)      // Open after 5 failures
        successThreshold(3)      // Close after 3 successes in half-open
        waitDuration(60.seconds) // Wait 60s before attempting half-open
        slowCallThreshold(30.seconds) // Calls > 30s are considered failures
    }
    
    override suspend fun extractText(request: OcrRequest): OcrResult {
        val startTime = Clock.System.now()
        var attemptCount = 0
        
        return retryService.executeWithRetry(
            operation = "aws-bedrock-ocr",
            analysisJobId = request.id,
            analysisType = request.analysisType ?: AnalysisType.BASIC_OCR
        ) { attempt ->
            attemptCount = attempt
            
            try {
                // Record circuit breaker metrics
                val cbMetrics = circuitBreaker.getMetrics()
                metricsService.setGauge("circuit_breaker_state", cbMetrics.state.ordinal.toLong(), mapOf(
                    "name" to cbMetrics.name
                ))
                metricsService.setGauge("circuit_breaker_failure_count", cbMetrics.failureCount.toLong(), mapOf(
                    "name" to cbMetrics.name
                ))
                
                // Execute with circuit breaker protection
                val result = circuitBreaker.execute {
                    // Add timeout to prevent hanging calls
                    withTimeout(30.seconds) {
                        bedrockService.extractText(request)
                    }
                }
                
                // Record success metrics
                val processingTime = (Clock.System.now() - startTime).inWholeMilliseconds
                metricsService.incrementCounter("bedrock_ocr_success", mapOf(
                    "attempt" to attempt.toString(),
                    "analysis_type" to (request.analysisType?.name ?: "UNKNOWN")
                ))
                metricsService.recordHistogram("bedrock_ocr_processing_time", processingTime)
                
                logger.info(
                    "Bedrock OCR successful: id=${request.id}, attempt=$attempt, " +
                    "processingTime=${processingTime}ms, tokens=${result.metadata["tokensUsed"] ?: "unknown"}"
                )
                
                result
                
            } catch (e: CircuitBreakerOpenException) {
                // Circuit breaker is open, try fallback if available
                logger.warn("Circuit breaker open for Bedrock OCR, attempting fallback")
                
                metricsService.incrementCounter("bedrock_ocr_circuit_breaker_open", mapOf(
                    "analysis_type" to (request.analysisType?.name ?: "UNKNOWN")
                ))
                
                return@executeWithRetry tryFallbackService(request) 
                    ?: throw AnalysisException.ExternalServiceError(
                        message = "AWS Bedrock OCR service is unavailable and no fallback configured",
                        analysisJobId = request.id,
                        analysisType = request.analysisType ?: AnalysisType.BASIC_OCR,
                        cause = e,
                        serviceName = "aws-bedrock",
                        retryable = true
                    )
                
            } catch (e: Exception) {
                val processingTime = (Clock.System.now() - startTime).inWholeMilliseconds
                
                // Determine if this error should be retried
                val retryable = isRetryableError(e)
                
                metricsService.incrementCounter("bedrock_ocr_error", mapOf(
                    "attempt" to attempt.toString(),
                    "analysis_type" to (request.analysisType?.name ?: "UNKNOWN"),
                    "error_type" to e::class.simpleName.orEmpty(),
                    "retryable" to retryable.toString()
                ))
                
                logger.error(
                    "Bedrock OCR failed: id=${request.id}, attempt=$attempt, " +
                    "processingTime=${processingTime}ms, error=${e.message}",
                    e
                )
                
                // Convert to analysis-specific exception
                throw mapToAnalysisException(e, request, retryable)
            }
        }
    }
    
    private suspend fun tryFallbackService(request: OcrRequest): OcrResult? {
        return fallbackService?.let { fallback ->
            try {
                logger.info("Using fallback OCR service for request ${request.id}")
                
                val result = fallback.extractText(request)
                
                metricsService.incrementCounter("bedrock_ocr_fallback_success", mapOf(
                    "analysis_type" to (request.analysisType?.name ?: "UNKNOWN")
                ))
                
                // Mark result as coming from fallback
                result.copy(
                    metadata = result.metadata + mapOf(
                        "fallback_used" to "true",
                        "original_service" to "aws-bedrock",
                        "fallback_service" to fallback::class.simpleName.orEmpty()
                    )
                )
                
            } catch (e: Exception) {
                logger.error("Fallback OCR service also failed for request ${request.id}", e)
                
                metricsService.incrementCounter("bedrock_ocr_fallback_failure", mapOf(
                    "analysis_type" to (request.analysisType?.name ?: "UNKNOWN"),
                    "error_type" to e::class.simpleName.orEmpty()
                ))
                
                null
            }
        }
    }
    
    private fun isRetryableError(exception: Exception): Boolean {
        return when {
            // Network-related errors are usually retryable
            exception.message?.contains("timeout", ignoreCase = true) == true -> true
            exception.message?.contains("connection", ignoreCase = true) == true -> true
            exception.message?.contains("network", ignoreCase = true) == true -> true
            
            // AWS-specific retryable errors
            exception.message?.contains("ThrottlingException", ignoreCase = true) == true -> true
            exception.message?.contains("ServiceUnavailableException", ignoreCase = true) == true -> true
            exception.message?.contains("InternalServerException", ignoreCase = true) == true -> true
            exception.message?.contains("TooManyRequestsException", ignoreCase = true) == true -> true
            
            // HTTP status codes that are retryable
            exception.message?.contains("429") == true -> true // Rate limited
            exception.message?.contains("500") == true -> true // Internal server error
            exception.message?.contains("502") == true -> true // Bad gateway
            exception.message?.contains("503") == true -> true // Service unavailable
            exception.message?.contains("504") == true -> true // Gateway timeout
            
            else -> false
        }
    }
    
    private fun mapToAnalysisException(
        exception: Exception,
        request: OcrRequest,
        retryable: Boolean
    ): AnalysisException {
        return when {
            exception.message?.contains("timeout", ignoreCase = true) == true -> {
                AnalysisException.ExternalServiceError(
                    message = "AWS Bedrock OCR request timed out",
                    analysisJobId = request.id,
                    analysisType = request.analysisType ?: AnalysisType.BASIC_OCR,
                    cause = exception,
                    serviceName = "aws-bedrock",
                    retryable = true
                )
            }
            
            exception.message?.contains("authentication", ignoreCase = true) == true ||
            exception.message?.contains("authorization", ignoreCase = true) == true ||
            exception.message?.contains("access denied", ignoreCase = true) == true -> {
                AnalysisException.AuthenticationError(
                    message = "AWS Bedrock authentication failed: ${exception.message}",
                    analysisJobId = request.id,
                    analysisType = request.analysisType ?: AnalysisType.BASIC_OCR,
                    cause = exception,
                    provider = "aws-bedrock"
                )
            }
            
            exception.message?.contains("throttl", ignoreCase = true) == true ||
            exception.message?.contains("rate limit", ignoreCase = true) == true ||
            exception.message?.contains("429") == true -> {
                AnalysisException.RateLimitExceeded(
                    message = "AWS Bedrock rate limit exceeded: ${exception.message}",
                    analysisJobId = request.id,
                    analysisType = request.analysisType ?: AnalysisType.BASIC_OCR,
                    retryAfterSeconds = extractRetryAfterSeconds(exception)
                )
            }
            
            else -> {
                AnalysisException.ExternalServiceError(
                    message = "AWS Bedrock OCR failed: ${exception.message}",
                    analysisJobId = request.id,
                    analysisType = request.analysisType ?: AnalysisType.BASIC_OCR,
                    cause = exception,
                    serviceName = "aws-bedrock",
                    retryable = retryable
                )
            }
        }
    }
    
    private fun extractRetryAfterSeconds(exception: Exception): Int? {
        // Try to extract retry-after from exception message or headers
        val message = exception.message ?: return null
        
        // Look for patterns like "retry after 60 seconds" or "Retry-After: 60"
        val retryAfterRegex = Regex("""retry[- ]?after[:\s]*(\d+)""", RegexOption.IGNORE_CASE)
        val match = retryAfterRegex.find(message)
        
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    
    /**
     * Get circuit breaker health information
     */
    fun getCircuitBreakerHealth(): CircuitBreakerHealth {
        val metrics = circuitBreaker.getMetrics()
        return CircuitBreakerHealth(
            name = metrics.name,
            state = metrics.state,
            failureCount = metrics.failureCount,
            successCount = metrics.successCount,
            lastFailureTime = metrics.lastFailureTime,
            nextAttemptTime = metrics.nextAttemptTime,
            isHealthy = metrics.state != dev.screenshotapi.infrastructure.resilience.CircuitBreakerState.OPEN
        )
    }
    
    /**
     * Manually reset circuit breaker (for admin operations)
     */
    suspend fun resetCircuitBreaker() {
        logger.info("Manually resetting AWS Bedrock circuit breaker")
        circuitBreaker.reset()
        
        metricsService.incrementCounter("circuit_breaker_manual_reset", mapOf(
            "name" to "aws-bedrock-ocr"
        ))
    }
    
    /**
     * Check if service is available (circuit breaker is not open)
     */
    fun isServiceAvailable(): Boolean {
        return circuitBreaker.getState() != dev.screenshotapi.infrastructure.resilience.CircuitBreakerState.OPEN
    }
    
    /**
     * Check if specific OCR engine is available
     * For Bedrock service, we only support CLAUDE_VISION
     */
    override suspend fun isEngineAvailable(engine: OcrEngine): Boolean {
        return when (engine) {
            OcrEngine.CLAUDE_VISION -> isServiceAvailable()
            else -> false
        }
    }
    
    /**
     * Get recommended engine for given tier
     * Bedrock service always recommends CLAUDE_VISION for AI tiers
     */
    override fun getRecommendedEngine(tier: OcrTier): OcrEngine {
        return when (tier) {
            OcrTier.AI_ELITE, OcrTier.AI_PREMIUM -> OcrEngine.CLAUDE_VISION
            else -> OcrEngine.CLAUDE_VISION // Bedrock only supports Claude
        }
    }
    
    /**
     * Get processing capabilities for engine
     */
    override fun getEngineCapabilities(engine: OcrEngine): OcrEngineCapabilities {
        return when (engine) {
            OcrEngine.CLAUDE_VISION -> OcrEngineCapabilities(
                engine = OcrEngine.CLAUDE_VISION,
                supportedLanguages = listOf("en", "es", "fr", "de", "it", "pt", "ja", "ko", "zh"),
                supportsStructuredData = true,
                supportsTables = true,
                supportsForms = true,
                supportsHandwriting = true,
                averageAccuracy = 0.95,
                averageProcessingTime = 2.5, // seconds
                costPerRequest = 0.003, // ~$3 per 1000 images with Claude 3 Haiku
                maxImageSize = 5 * 1024 * 1024, // 5MB
                isLocal = false,
                requiresApiKey = true
            )
            else -> throw IllegalArgumentException("Engine $engine not supported by Bedrock service")
        }
    }
}

/**
 * Circuit Breaker Health information for monitoring
 */
data class CircuitBreakerHealth(
    val name: String,
    val state: dev.screenshotapi.infrastructure.resilience.CircuitBreakerState,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: kotlinx.datetime.Instant?,
    val nextAttemptTime: kotlinx.datetime.Instant?,
    val isHealthy: Boolean
)