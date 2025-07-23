package dev.screenshotapi.core.domain.entities

/**
 * Enum representing different types of jobs that can be processed by the system.
 * This is a core business concept that defines the various processing capabilities.
 */
enum class JobType(
    val displayName: String,
    val description: String,
    val defaultCredits: Int
) {
    SCREENSHOT("Screenshot", "Website screenshot generation", 1),
    OCR("OCR Processing", "Basic optical character recognition", 2),
    UX_ANALYSIS("UX Analysis", "AI-powered user experience analysis", 3),
    CONTENT_SUMMARY("Content Summary", "AI-powered content analysis and summarization", 3),
    GENERAL_ANALYSIS("General Analysis", "AI-powered general image analysis", 3),
    PDF_GENERATION("PDF Generation", "PDF document creation", 1),
    BATCH_SCREENSHOT("Batch Screenshot", "Multiple screenshots processing", 1), // per item
    IMAGE_PROCESSING("Image Processing", "Image manipulation and optimization", 1),
    URL_ANALYSIS("URL Analysis", "Website metadata and content analysis", 1);
    
    companion object {
        /**
         * Get job type by name, case-insensitive
         */
        fun fromString(value: String): JobType? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }
        
        /**
         * Get all available job types
         */
        fun getAllTypes(): List<JobType> {
            return values().toList()
        }
        
        /**
         * Get credit cost for a job type
         */
        fun getCreditCost(jobType: JobType, quantity: Int = 1): Int {
            return jobType.defaultCredits * quantity
        }
        
        /**
         * Get the appropriate credit deduction reason for a job type
         */
        fun getDeductionReason(jobType: JobType): CreditDeductionReason {
            return when(jobType) {
                SCREENSHOT -> CreditDeductionReason.SCREENSHOT
                OCR -> CreditDeductionReason.OCR
                UX_ANALYSIS, CONTENT_SUMMARY, GENERAL_ANALYSIS -> CreditDeductionReason.AI_ANALYSIS
                PDF_GENERATION -> CreditDeductionReason.PDF_GENERATION
                BATCH_SCREENSHOT -> CreditDeductionReason.BATCH_PROCESSING
                IMAGE_PROCESSING -> CreditDeductionReason.PREMIUM_FEATURE
                URL_ANALYSIS -> CreditDeductionReason.API_CALL
            }
        }
    }
}