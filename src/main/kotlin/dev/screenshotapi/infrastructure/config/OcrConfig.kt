package dev.screenshotapi.infrastructure.config

import kotlinx.serialization.Serializable

/**
 * OCR Configuration
 * GitHub Issue #3: Implement PaddleOCR service layer with ProcessBuilder
 */
@Serializable
data class OcrConfig(
    val enabled: Boolean = true,
    val paddleOcr: PaddleOcrConfig = PaddleOcrConfig(),
    val bedrock: BedrockOcrConfig = BedrockOcrConfig(),
    val general: GeneralOcrConfig = GeneralOcrConfig()
)

@Serializable
data class PaddleOcrConfig(
    val pythonPath: String = "python3",
    val paddleOcrPath: String = "/app/ocr/paddleocr",
    val workingDirectory: String = "/tmp/ocr",
    val timeoutSeconds: Long = 30,
    val maxConcurrentJobs: Int = 4,
    val supportedLanguages: List<String> = listOf(
        "en", "ch", "ta", "te", "ka", "ja", "ko", 
        "hi", "ar", "fr", "es", "pt", "de", "it", "ru"
    )
)

@Serializable
data class BedrockOcrConfig(
    val enabled: Boolean = false,
    val region: String = "us-east-1",
    val primaryModel: String = "claude3Haiku",
    val enableFallback: Boolean = true,
    val timeoutSeconds: Long = 10 // Much faster than PaddleOCR
)

@Serializable
data class GeneralOcrConfig(
    val maxImageSize: Long = 10 * 1024 * 1024, // 10MB
    val maxProcessingTime: Long = 60, // seconds
    val retryAttempts: Int = 2,
    val cleanupTempFiles: Boolean = true,
    val enableDebugLogging: Boolean = false
)

/**
 * OCR Engine Configuration
 */
@Serializable
data class OcrEngineConfig(
    val engine: String,
    val enabled: Boolean = true,
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val timeoutSeconds: Long = 30,
    val maxRetries: Int = 3,
    val rateLimit: Int = 100 // requests per minute
)

/**
 * Load OCR configuration from environment variables
 */
fun loadOcrConfig(): OcrConfig {
    return OcrConfig(
        enabled = System.getenv("OCR_ENABLED")?.toBoolean() ?: true,
        paddleOcr = PaddleOcrConfig(
            pythonPath = System.getenv("OCR_PYTHON_PATH") ?: "python3",
            paddleOcrPath = System.getenv("OCR_PADDLEOCR_PATH") ?: "/app/ocr/paddleocr",
            workingDirectory = System.getenv("OCR_WORKING_DIRECTORY") ?: "/Users/luiscarbonel/Desktop/dev/git/dev-screenshot/screenshot-api/tmp/ocr",
            timeoutSeconds = System.getenv("OCR_TIMEOUT_SECONDS")?.toLong() ?: 120,
            maxConcurrentJobs = System.getenv("OCR_MAX_CONCURRENT_JOBS")?.toInt() ?: 4,
            supportedLanguages = System.getenv("OCR_SUPPORTED_LANGUAGES")?.split(",") ?: listOf(
                "en", "ch", "ta", "te", "ka", "ja", "ko", 
                "hi", "ar", "fr", "es", "pt", "de", "it", "ru"
            )
        ),
        bedrock = BedrockOcrConfig(
            enabled = System.getenv("BEDROCK_OCR_ENABLED")?.toBoolean() ?: false,
            region = System.getenv("AWS_REGION") ?: "us-east-1",
            primaryModel = System.getenv("BEDROCK_PRIMARY_MODEL") ?: "claude3Haiku",
            enableFallback = System.getenv("BEDROCK_ENABLE_FALLBACK")?.toBoolean() ?: true,
            timeoutSeconds = System.getenv("BEDROCK_TIMEOUT_SECONDS")?.toLong() ?: 10
        ),
        general = GeneralOcrConfig(
            maxImageSize = System.getenv("OCR_MAX_IMAGE_SIZE")?.toLong() ?: 10 * 1024 * 1024,
            maxProcessingTime = System.getenv("OCR_MAX_PROCESSING_TIME")?.toLong() ?: 60,
            retryAttempts = System.getenv("OCR_RETRY_ATTEMPTS")?.toInt() ?: 2,
            cleanupTempFiles = System.getenv("OCR_CLEANUP_TEMP_FILES")?.toBoolean() ?: true,
            enableDebugLogging = System.getenv("OCR_DEBUG_LOGGING")?.toBoolean() ?: false
        )
    )
}