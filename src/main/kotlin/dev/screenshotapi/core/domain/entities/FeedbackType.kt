package dev.screenshotapi.core.domain.entities

/**
 * Types of feedback that users can provide
 */
enum class FeedbackType(
    val displayName: String,
    val description: String
) {
    GENERAL("General Feedback", "General comments about the platform"),
    FEATURE_REQUEST("Feature Request", "Suggestions for new features or improvements"),
    BUG_REPORT("Bug Report", "Reports of issues or bugs encountered"),
    SATISFACTION("Satisfaction", "Overall satisfaction with the service"),
    CONVERSION_EXPERIENCE("Conversion Experience", "Feedback about upgrading or pricing"),
    UX_IMPROVEMENT("UX Improvement", "User experience and interface feedback"),
    API_FEEDBACK("API Feedback", "Feedback about API usage and documentation"),
    PERFORMANCE("Performance", "Feedback about speed and reliability");

    companion object {
        /**
         * Get feedback type by name, case-insensitive
         */
        fun fromString(value: String): FeedbackType? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }

        /**
         * Get all feedback types suitable for user selection
         */
        fun getAllTypes(): List<FeedbackType> = values().toList()

        /**
         * Get business-critical feedback types for priority handling
         */
        fun getCriticalTypes(): List<FeedbackType> = listOf(
            BUG_REPORT,
            PERFORMANCE,
            CONVERSION_EXPERIENCE
        )
    }
}