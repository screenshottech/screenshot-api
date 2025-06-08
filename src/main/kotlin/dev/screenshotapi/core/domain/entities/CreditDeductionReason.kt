package dev.screenshotapi.core.domain.entities

/**
 * Enum representing different reasons for credit deductions.
 * This is a core business concept that defines what services consume credits.
 */
enum class CreditDeductionReason(
    val displayName: String,
    val description: String
) {
    SCREENSHOT("Screenshot", "Credit deducted for screenshot generation"),
    OCR("OCR", "Credit deducted for optical character recognition"),
    PDF_GENERATION("PDF Generation", "Credit deducted for PDF document generation"),
    BATCH_PROCESSING("Batch Processing", "Credit deducted for batch operations"),
    API_CALL("API Call", "Credit deducted for general API usage"),
    PREMIUM_FEATURE("Premium Feature", "Credit deducted for premium functionality");
    
    companion object {
        /**
         * Get reason by name, case-insensitive
         */
        fun fromString(value: String): CreditDeductionReason? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }
        
        /**
         * Get all available reasons as a list of strings
         */
        fun getAllReasons(): List<String> {
            return values().map { it.name }
        }
    }
}