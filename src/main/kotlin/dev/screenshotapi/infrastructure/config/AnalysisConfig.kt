package dev.screenshotapi.infrastructure.config

import kotlinx.serialization.Serializable

/**
 * Analysis Configuration for OCR and AI Analysis processing
 * Centralizes retry logic, timeouts, and processing parameters
 */
@Serializable
data class AnalysisConfig(
    val processing: ProcessingConfig = ProcessingConfig(),
    val retry: AnalysisRetryConfig = AnalysisRetryConfig(),
    val timeout: AnalysisTimeoutConfig = AnalysisTimeoutConfig(),
    val validation: ValidationConfig = ValidationConfig(),
    val performance: PerformanceConfig = PerformanceConfig()
) {
    companion object {
        fun load(): AnalysisConfig = loadAnalysisConfig()
    }
}

/**
 * Processing Configuration
 */
@Serializable
data class ProcessingConfig(
    val maxConcurrentJobs: Int = 10,
    val batchSize: Int = 5,
    val enableParallelProcessing: Boolean = true,
    val enableResultCaching: Boolean = true,
    val cacheExpirationMinutes: Int = 60
)

/**
 * Analysis-specific retry configuration
 * More granular than general BedrockConfig retry settings
 */
@Serializable
data class AnalysisRetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 2000,
    val maxDelayMs: Long = 60000,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.15,
    val retryableErrorCodes: List<String> = listOf(
        "OCR_PROCESSING_FAILED",
        "EXTERNAL_SERVICE_ERROR",
        "RATE_LIMIT_EXCEEDED",
        "PROCESSING_ERROR"
    ),
    val nonRetryableErrorCodes: List<String> = listOf(
        "VALIDATION_ERROR",
        "AUTHENTICATION_ERROR",
        "CONFIGURATION_ERROR",
        "INSUFFICIENT_CREDITS_ERROR",
        "JOB_NOT_FOUND_ERROR",
        "INVALID_JOB_STATUS_ERROR"
    ),
    val enableExponentialBackoff: Boolean = true,
    val enableJitter: Boolean = true,
    val retryOnImageDownloadFailure: Boolean = true,
    val retryOnExternalServiceFailure: Boolean = true
)

/**
 * Analysis-specific timeout configuration
 */
@Serializable
data class AnalysisTimeoutConfig(
    val imageDownloadTimeoutMs: Long = 30000,    // 30 seconds
    val analysisProcessingTimeoutMs: Long = 180000, // 3 minutes
    val resultFormattingTimeoutMs: Long = 10000,  // 10 seconds
    val totalJobTimeoutMs: Long = 300000,         // 5 minutes total
    val healthCheckTimeoutMs: Long = 5000         // 5 seconds
)

/**
 * Validation Configuration
 */
@Serializable
data class ValidationConfig(
    val maxImageSizeMB: Int = 10,
    val supportedImageFormats: List<String> = listOf("png", "jpg", "jpeg", "webp"),
    val maxUrlLength: Int = 2048,
    val enableStrictValidation: Boolean = true,
    val validateImageDimensions: Boolean = true,
    val maxImageWidth: Int = 4096,
    val maxImageHeight: Int = 4096
)

/**
 * Performance Configuration
 */
@Serializable
data class PerformanceConfig(
    val enableMetrics: Boolean = true,
    val enableDetailedLogging: Boolean = false,
    val logProcessingTimes: Boolean = true,
    val logTokenUsage: Boolean = true,
    val enablePerformanceAlerts: Boolean = true,
    val slowJobThresholdMs: Long = 120000,        // 2 minutes
    val memoryWarningThresholdMB: Int = 512
)

/**
 * Analysis Type Specific Configuration
 */
@Serializable
data class AnalysisTypeConfig(
    val basicOcr: BasicOcrConfig = BasicOcrConfig(),
    val uxAnalysis: UxAnalysisConfig = UxAnalysisConfig(),
    val contentSummary: ContentSummaryConfig = ContentSummaryConfig(),
    val general: GeneralAnalysisConfig = GeneralAnalysisConfig()
)

@Serializable
data class BasicOcrConfig(
    val maxRetries: Int = 2,
    val timeoutMs: Long = 60000,
    val enableTextStructuring: Boolean = true,
    val confidenceThreshold: Double = 0.7
)

@Serializable
data class UxAnalysisConfig(
    val maxRetries: Int = 3,
    val timeoutMs: Long = 120000,
    val enableDetailedAnalysis: Boolean = true,
    val includeAccessibilityCheck: Boolean = true
)

@Serializable
data class ContentSummaryConfig(
    val maxRetries: Int = 3,
    val timeoutMs: Long = 90000,
    val maxSummaryLength: Int = 500,
    val enableSentimentAnalysis: Boolean = true
)

@Serializable
data class GeneralAnalysisConfig(
    val maxRetries: Int = 2,
    val timeoutMs: Long = 180000,
    val enableFlexibleParsing: Boolean = true
)

/**
 * Load Analysis configuration from environment variables
 */
fun loadAnalysisConfig(): AnalysisConfig {
    return AnalysisConfig(
        processing = ProcessingConfig(
            maxConcurrentJobs = System.getenv("ANALYSIS_MAX_CONCURRENT_JOBS")?.toInt() ?: 10,
            batchSize = System.getenv("ANALYSIS_BATCH_SIZE")?.toInt() ?: 5,
            enableParallelProcessing = System.getenv("ANALYSIS_ENABLE_PARALLEL")?.toBoolean() ?: true,
            enableResultCaching = System.getenv("ANALYSIS_ENABLE_CACHING")?.toBoolean() ?: true,
            cacheExpirationMinutes = System.getenv("ANALYSIS_CACHE_EXPIRATION_MINUTES")?.toInt() ?: 60
        ),
        retry = AnalysisRetryConfig(
            maxAttempts = System.getenv("ANALYSIS_RETRY_MAX_ATTEMPTS")?.toInt() ?: 3,
            baseDelayMs = System.getenv("ANALYSIS_RETRY_BASE_DELAY_MS")?.toLong() ?: 2000,
            maxDelayMs = System.getenv("ANALYSIS_RETRY_MAX_DELAY_MS")?.toLong() ?: 60000,
            backoffMultiplier = System.getenv("ANALYSIS_RETRY_BACKOFF_MULTIPLIER")?.toDouble() ?: 2.0,
            jitterFactor = System.getenv("ANALYSIS_RETRY_JITTER_FACTOR")?.toDouble() ?: 0.15,
            enableExponentialBackoff = System.getenv("ANALYSIS_RETRY_ENABLE_EXPONENTIAL_BACKOFF")?.toBoolean() ?: true,
            enableJitter = System.getenv("ANALYSIS_RETRY_ENABLE_JITTER")?.toBoolean() ?: true,
            retryOnImageDownloadFailure = System.getenv("ANALYSIS_RETRY_ON_IMAGE_DOWNLOAD_FAILURE")?.toBoolean() ?: true,
            retryOnExternalServiceFailure = System.getenv("ANALYSIS_RETRY_ON_EXTERNAL_SERVICE_FAILURE")?.toBoolean() ?: true
        ),
        timeout = AnalysisTimeoutConfig(
            imageDownloadTimeoutMs = System.getenv("ANALYSIS_IMAGE_DOWNLOAD_TIMEOUT_MS")?.toLong() ?: 30000,
            analysisProcessingTimeoutMs = System.getenv("ANALYSIS_PROCESSING_TIMEOUT_MS")?.toLong() ?: 180000,
            resultFormattingTimeoutMs = System.getenv("ANALYSIS_RESULT_FORMATTING_TIMEOUT_MS")?.toLong() ?: 10000,
            totalJobTimeoutMs = System.getenv("ANALYSIS_TOTAL_JOB_TIMEOUT_MS")?.toLong() ?: 300000,
            healthCheckTimeoutMs = System.getenv("ANALYSIS_HEALTH_CHECK_TIMEOUT_MS")?.toLong() ?: 5000
        ),
        validation = ValidationConfig(
            maxImageSizeMB = System.getenv("ANALYSIS_MAX_IMAGE_SIZE_MB")?.toInt() ?: 10,
            maxUrlLength = System.getenv("ANALYSIS_MAX_URL_LENGTH")?.toInt() ?: 2048,
            enableStrictValidation = System.getenv("ANALYSIS_ENABLE_STRICT_VALIDATION")?.toBoolean() ?: true,
            validateImageDimensions = System.getenv("ANALYSIS_VALIDATE_IMAGE_DIMENSIONS")?.toBoolean() ?: true,
            maxImageWidth = System.getenv("ANALYSIS_MAX_IMAGE_WIDTH")?.toInt() ?: 4096,
            maxImageHeight = System.getenv("ANALYSIS_MAX_IMAGE_HEIGHT")?.toInt() ?: 4096
        ),
        performance = PerformanceConfig(
            enableMetrics = System.getenv("ANALYSIS_ENABLE_METRICS")?.toBoolean() ?: true,
            enableDetailedLogging = System.getenv("ANALYSIS_ENABLE_DETAILED_LOGGING")?.toBoolean() ?: false,
            logProcessingTimes = System.getenv("ANALYSIS_LOG_PROCESSING_TIMES")?.toBoolean() ?: true,
            logTokenUsage = System.getenv("ANALYSIS_LOG_TOKEN_USAGE")?.toBoolean() ?: true,
            enablePerformanceAlerts = System.getenv("ANALYSIS_ENABLE_PERFORMANCE_ALERTS")?.toBoolean() ?: true,
            slowJobThresholdMs = System.getenv("ANALYSIS_SLOW_JOB_THRESHOLD_MS")?.toLong() ?: 120000,
            memoryWarningThresholdMB = System.getenv("ANALYSIS_MEMORY_WARNING_THRESHOLD_MB")?.toInt() ?: 512
        )
    )
}

/**
 * Load Analysis Type specific configuration from environment variables
 */
fun loadAnalysisTypeConfig(): AnalysisTypeConfig {
    return AnalysisTypeConfig(
        basicOcr = BasicOcrConfig(
            maxRetries = System.getenv("ANALYSIS_BASIC_OCR_MAX_RETRIES")?.toInt() ?: 2,
            timeoutMs = System.getenv("ANALYSIS_BASIC_OCR_TIMEOUT_MS")?.toLong() ?: 60000,
            enableTextStructuring = System.getenv("ANALYSIS_BASIC_OCR_ENABLE_TEXT_STRUCTURING")?.toBoolean() ?: true,
            confidenceThreshold = System.getenv("ANALYSIS_BASIC_OCR_CONFIDENCE_THRESHOLD")?.toDouble() ?: 0.7
        ),
        uxAnalysis = UxAnalysisConfig(
            maxRetries = System.getenv("ANALYSIS_UX_ANALYSIS_MAX_RETRIES")?.toInt() ?: 3,
            timeoutMs = System.getenv("ANALYSIS_UX_ANALYSIS_TIMEOUT_MS")?.toLong() ?: 120000,
            enableDetailedAnalysis = System.getenv("ANALYSIS_UX_ANALYSIS_ENABLE_DETAILED")?.toBoolean() ?: true,
            includeAccessibilityCheck = System.getenv("ANALYSIS_UX_ANALYSIS_INCLUDE_ACCESSIBILITY")?.toBoolean() ?: true
        ),
        contentSummary = ContentSummaryConfig(
            maxRetries = System.getenv("ANALYSIS_CONTENT_SUMMARY_MAX_RETRIES")?.toInt() ?: 3,
            timeoutMs = System.getenv("ANALYSIS_CONTENT_SUMMARY_TIMEOUT_MS")?.toLong() ?: 90000,
            maxSummaryLength = System.getenv("ANALYSIS_CONTENT_SUMMARY_MAX_LENGTH")?.toInt() ?: 500,
            enableSentimentAnalysis = System.getenv("ANALYSIS_CONTENT_SUMMARY_ENABLE_SENTIMENT")?.toBoolean() ?: true
        ),
        general = GeneralAnalysisConfig(
            maxRetries = System.getenv("ANALYSIS_GENERAL_MAX_RETRIES")?.toInt() ?: 2,
            timeoutMs = System.getenv("ANALYSIS_GENERAL_TIMEOUT_MS")?.toLong() ?: 180000,
            enableFlexibleParsing = System.getenv("ANALYSIS_GENERAL_ENABLE_FLEXIBLE_PARSING")?.toBoolean() ?: true
        )
    )
}

/**
 * Utility functions for retry logic
 */
object AnalysisRetryUtils {
    fun shouldRetry(errorCode: String, attempt: Int, config: AnalysisRetryConfig): Boolean {
        if (attempt >= config.maxAttempts) return false
        if (errorCode in config.nonRetryableErrorCodes) return false
        return errorCode in config.retryableErrorCodes
    }
    
    fun calculateDelay(attempt: Int, config: AnalysisRetryConfig): Long {
        val baseDelay = config.baseDelayMs
        val exponentialDelay = if (config.enableExponentialBackoff) {
            (baseDelay * Math.pow(config.backoffMultiplier, attempt.toDouble())).toLong()
        } else {
            baseDelay
        }
        
        val cappedDelay = minOf(exponentialDelay, config.maxDelayMs)
        
        return if (config.enableJitter) {
            val jitter = (cappedDelay * config.jitterFactor * Math.random()).toLong()
            cappedDelay + jitter
        } else {
            cappedDelay
        }
    }
}