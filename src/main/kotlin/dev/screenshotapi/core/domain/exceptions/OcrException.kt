package dev.screenshotapi.core.domain.exceptions

/**
 * OCR Domain Exceptions
 * GitHub Issue #2: OCR Domain Architecture
 */

sealed class OcrException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Engine-specific processing failures
     */
    class ProcessingException(
        engine: String,
        message: String,
        cause: Throwable? = null
    ) : OcrException("OCR processing failed with $engine: $message", cause)
    
    /**
     * Image validation and format issues
     */
    class InvalidImageException(
        message: String,
        cause: Throwable? = null
    ) : OcrException("Invalid image: $message", cause)
    
    /**
     * Configuration and setup issues
     */
    class ConfigurationException(
        message: String,
        cause: Throwable? = null
    ) : OcrException("OCR configuration error: $message", cause)
    
    /**
     * Service unavailability
     */
    class ServiceUnavailableException(
        engine: String,
        message: String,
        cause: Throwable? = null
    ) : OcrException("OCR service $engine unavailable: $message", cause)
    
    /**
     * Timeout during processing
     */
    class TimeoutException(
        processingTime: Long,
        maxTime: Long,
        cause: Throwable? = null
    ) : OcrException("OCR processing timeout: ${processingTime}s exceeded limit ${maxTime}s", cause)
    
    /**
     * Insufficient credits for OCR tier
     */
    class InsufficientCreditsException(
        userId: String,
        tier: String,
        requiredCredits: Int,
        availableCredits: Int
    ) : OcrException("Insufficient credits for $tier OCR: requires $requiredCredits, available $availableCredits")
    
    /**
     * Unsupported language
     */
    class UnsupportedLanguageException(
        language: String,
        engine: String
    ) : OcrException("Language '$language' not supported by $engine")
    
    /**
     * Rate limiting exceeded
     */
    class RateLimitExceededException(
        userId: String,
        message: String
    ) : OcrException("Rate limit exceeded for user $userId: $message")
}