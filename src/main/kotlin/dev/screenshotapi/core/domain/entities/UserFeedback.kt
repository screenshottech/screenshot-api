package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

/**
 * User feedback entity for collecting and managing user insights
 * 
 * This entity captures user feedback across different categories to help
 * understand product-market fit, user satisfaction, and improvement areas.
 */
data class UserFeedback(
    val id: String,
    val userId: String,
    val feedbackType: FeedbackType,
    val rating: Int?, // 1-5 star rating, nullable for non-rating feedback
    val subject: String?, // Optional subject line for categorization
    val message: String, // Main feedback content
    val metadata: Map<String, String> = emptyMap(), // Context like page, feature, etc.
    val status: FeedbackStatus = FeedbackStatus.PENDING,
    val userAgent: String? = null, // Browser/client information
    val ipAddress: String? = null, // For abuse prevention
    val adminNotes: String? = null, // Internal notes from team
    val resolvedBy: String? = null, // Admin who resolved the feedback
    val resolvedAt: Instant? = null, // When feedback was resolved
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        /**
         * Create new feedback with default values
         */
        fun create(
            id: String,
            userId: String,
            feedbackType: FeedbackType,
            message: String,
            rating: Int? = null,
            subject: String? = null,
            metadata: Map<String, String> = emptyMap(),
            userAgent: String? = null,
            ipAddress: String? = null
        ): UserFeedback {
            val now = kotlinx.datetime.Clock.System.now()
            return UserFeedback(
                id = id,
                userId = userId,
                feedbackType = feedbackType,
                rating = rating,
                subject = subject,
                message = message,
                metadata = metadata,
                status = FeedbackStatus.PENDING,
                userAgent = userAgent,
                ipAddress = ipAddress,
                adminNotes = null,
                resolvedBy = null,
                resolvedAt = null,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    /**
     * Update feedback status with admin information
     */
    fun updateStatus(
        newStatus: FeedbackStatus,
        adminId: String? = null,
        adminNotes: String? = null
    ): UserFeedback {
        val now = kotlinx.datetime.Clock.System.now()
        return this.copy(
            status = newStatus,
            adminNotes = adminNotes ?: this.adminNotes,
            resolvedBy = if (newStatus.isResolved) adminId else this.resolvedBy,
            resolvedAt = if (newStatus.isResolved) now else this.resolvedAt,
            updatedAt = now
        )
    }

    /**
     * Add or update metadata
     */
    fun withMetadata(key: String, value: String): UserFeedback {
        return this.copy(
            metadata = this.metadata + (key to value),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    /**
     * Check if feedback is business-critical (requires immediate attention)
     */
    fun isCritical(): Boolean {
        return feedbackType in FeedbackType.getCriticalTypes() || 
               (rating != null && rating <= 2) // 1-2 star ratings are critical
    }

    /**
     * Check if feedback is resolved
     */
    fun isResolved(): Boolean = status.isResolved

    /**
     * Get feedback priority based on type and rating
     */
    fun getPriority(): FeedbackPriority {
        return when {
            isCritical() -> FeedbackPriority.HIGH
            feedbackType == FeedbackType.FEATURE_REQUEST -> FeedbackPriority.MEDIUM
            rating != null && rating >= 4 -> FeedbackPriority.LOW // Positive feedback
            else -> FeedbackPriority.MEDIUM
        }
    }
}

/**
 * Priority levels for feedback processing
 */
enum class FeedbackPriority(val displayName: String) {
    HIGH("High Priority"),
    MEDIUM("Medium Priority"), 
    LOW("Low Priority")
}