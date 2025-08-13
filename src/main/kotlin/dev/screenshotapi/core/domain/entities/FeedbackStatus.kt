package dev.screenshotapi.core.domain.entities

/**
 * Status of user feedback for tracking and management
 */
enum class FeedbackStatus(
    val displayName: String,
    val description: String,
    val isResolved: Boolean = false
) {
    PENDING("Pending", "Feedback submitted and awaiting review", false),
    REVIEWED("Reviewed", "Feedback has been reviewed by team", false),
    IN_PROGRESS("In Progress", "Feedback is being acted upon", false),
    RESOLVED("Resolved", "Feedback has been addressed", true),
    CLOSED("Closed", "Feedback has been closed without action", true),
    ACKNOWLEDGED("Acknowledged", "Feedback has been acknowledged", false);

    companion object {
        /**
         * Get feedback status by name, case-insensitive
         */
        fun fromString(value: String): FeedbackStatus? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }

        /**
         * Get all active (unresolved) statuses
         */
        fun getActiveStatuses(): List<FeedbackStatus> = 
            values().filter { !it.isResolved }

        /**
         * Get all resolved statuses
         */
        fun getResolvedStatuses(): List<FeedbackStatus> = 
            values().filter { it.isResolved }

        /**
         * Default status for new feedback
         */
        fun getDefaultStatus(): FeedbackStatus = PENDING
    }
}