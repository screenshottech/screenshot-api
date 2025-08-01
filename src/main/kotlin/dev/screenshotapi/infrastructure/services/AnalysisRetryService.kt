package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.infrastructure.config.AnalysisConfig
import dev.screenshotapi.infrastructure.config.AnalysisRetryUtils
import dev.screenshotapi.core.domain.exceptions.AnalysisException
import dev.screenshotapi.core.domain.entities.AnalysisType
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Analysis Retry Service - Handles retry logic for analysis operations
 * Uses externalized configuration for retry parameters
 */
class AnalysisRetryService(
    private val analysisConfig: AnalysisConfig
) {
    private val logger = LoggerFactory.getLogger(AnalysisRetryService::class.java)
    
    /**
     * Execute an operation with retry logic based on analysis configuration
     */
    suspend fun <T> executeWithRetry(
        operation: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        block: suspend (attempt: Int) -> T
    ): T {
        val retryConfig = analysisConfig.retry
        var lastException: Exception? = null
        
        for (attempt in 1..retryConfig.maxAttempts) {
            try {
                logger.debug("Executing $operation, attempt $attempt/$${retryConfig.maxAttempts}")
                return block(attempt)
            } catch (e: AnalysisException) {
                lastException = e
                
                if (!shouldRetryException(e, attempt)) {
                    logger.warn("Not retrying $operation after attempt $attempt: ${e.message}")
                    throw e
                }
                
                if (attempt < retryConfig.maxAttempts) {
                    val delayMs = AnalysisRetryUtils.calculateDelay(attempt - 1, retryConfig)
                    logger.info(
                        "Retrying $operation in ${delayMs}ms after attempt $attempt. " +
                        "Error: ${e.message}. JobId: $analysisJobId, Type: $analysisType"
                    )
                    delay(delayMs)
                } else {
                    logger.error("Max retry attempts ($${retryConfig.maxAttempts}) reached for $operation")
                }
            } catch (e: Exception) {
                lastException = e
                logger.error("Unexpected exception in $operation, attempt $attempt", e)
                
                // For non-AnalysisException, only retry if it's a known retryable type
                if (!shouldRetryGenericException(e, attempt)) {
                    throw e
                }
                
                if (attempt < retryConfig.maxAttempts) {
                    val delayMs = AnalysisRetryUtils.calculateDelay(attempt - 1, retryConfig)
                    logger.info("Retrying $operation in ${delayMs}ms after unexpected error")
                    delay(delayMs)
                }
            }
        }
        
        throw lastException ?: RuntimeException("Operation failed after $${retryConfig.maxAttempts} attempts")
    }
    
    /**
     * Determines if an AnalysisException should be retried
     */
    private fun shouldRetryException(exception: AnalysisException, attempt: Int): Boolean {
        val retryConfig = analysisConfig.retry
        
        // Check attempt limit
        if (attempt >= retryConfig.maxAttempts) {
            return false
        }
        
        // Check if this exception type is retryable
        if (!exception.retryable) {
            return false
        }
        
        // Check specific error codes
        val errorCode = exception::class.simpleName ?: "UNKNOWN"
        return AnalysisRetryUtils.shouldRetry(errorCode, attempt, retryConfig)
    }
    
    /**
     * Determines if a generic Exception should be retried
     */
    private fun shouldRetryGenericException(exception: Exception, attempt: Int): Boolean {
        val retryConfig = analysisConfig.retry
        
        if (attempt >= retryConfig.maxAttempts) {
            return false
        }
        
        // Only retry specific types of generic exceptions
        return when {
            exception::class.simpleName?.contains("Timeout") == true -> true
            exception::class.simpleName?.contains("Connection") == true -> true
            exception::class.simpleName?.contains("Socket") == true -> true
            exception.message?.contains("timeout", ignoreCase = true) == true -> true
            exception.message?.contains("connection", ignoreCase = true) == true -> true
            else -> false
        }
    }
    
    /**
     * Get retry configuration for a specific analysis type
     */
    fun getRetryConfigForAnalysisType(analysisType: AnalysisType): RetryConfiguration {
        val baseConfig = analysisConfig.retry
        
        // Could be extended to have type-specific overrides
        return RetryConfiguration(
            maxAttempts = baseConfig.maxAttempts,
            baseDelayMs = baseConfig.baseDelayMs,
            maxDelayMs = baseConfig.maxDelayMs,
            backoffMultiplier = baseConfig.backoffMultiplier,
            jitterFactor = baseConfig.jitterFactor
        )
    }
    
    /**
     * Check if an error code is retryable
     */
    fun isRetryableError(errorCode: String): Boolean {
        return errorCode in analysisConfig.retry.retryableErrorCodes
    }
    
    /**
     * Check if an error code is explicitly non-retryable
     */
    fun isNonRetryableError(errorCode: String): Boolean {
        return errorCode in analysisConfig.retry.nonRetryableErrorCodes
    }
    
    data class RetryConfiguration(
        val maxAttempts: Int,
        val baseDelayMs: Long,
        val maxDelayMs: Long,
        val backoffMultiplier: Double,
        val jitterFactor: Double
    )
}

/**
 * Extension function to create AnalysisException with retry context
 */
fun AnalysisException.withRetryContext(attempt: Int, maxAttempts: Int): AnalysisException {
    return when (this) {
        is AnalysisException.ProcessingError -> AnalysisException.ProcessingError(
            message = "${this.message} (attempt $attempt/$maxAttempts)",
            analysisJobId = this.analysisJobId,
            analysisType = this.analysisType,
            cause = this.cause,
            retryable = this.retryable
        )
        is AnalysisException.ExternalServiceError -> AnalysisException.ExternalServiceError(
            message = "${this.message} (attempt $attempt/$maxAttempts)",
            analysisJobId = this.analysisJobId,
            analysisType = this.analysisType,
            cause = this.cause,
            serviceName = this.serviceName,
            retryable = this.retryable
        )
        else -> this
    }
}