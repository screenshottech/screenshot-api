package dev.screenshotapi.core.ports.output

import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.FeedbackType
import dev.screenshotapi.core.domain.entities.UserFeedback
import kotlinx.datetime.Instant

/**
 * Repository interface for user feedback management
 */
interface UserFeedbackRepository {
    
    /**
     * Save user feedback
     */
    suspend fun save(feedback: UserFeedback): UserFeedback
    
    /**
     * Find feedback by ID
     */
    suspend fun findById(id: String): UserFeedback?
    
    /**
     * Find all feedback with pagination
     */
    suspend fun findAll(
        page: Int = 1,
        size: Int = 20
    ): List<UserFeedback>
    
    /**
     * Find all feedback by user ID with pagination
     */
    suspend fun findByUserId(
        userId: String, 
        page: Int = 1, 
        size: Int = 20
    ): List<UserFeedback>
    
    /**
     * Find feedback by user ID and status
     */
    suspend fun findByUserIdAndStatus(
        userId: String, 
        status: FeedbackStatus,
        page: Int = 1, 
        size: Int = 20
    ): List<UserFeedback>
    
    /**
     * Find feedback by type
     */
    suspend fun findByType(
        feedbackType: FeedbackType,
        page: Int = 1, 
        size: Int = 20
    ): List<UserFeedback>
    
    /**
     * Find feedback by status
     */
    suspend fun findByStatus(
        status: FeedbackStatus,
        page: Int = 1, 
        size: Int = 20
    ): List<UserFeedback>
    
    /**
     * Find feedback by date range
     */
    suspend fun findByDateRange(
        startDate: Instant,
        endDate: Instant,
        page: Int = 1, 
        size: Int = 20
    ): List<UserFeedback>
    
    /**
     * Find critical feedback (high priority items)
     */
    suspend fun findCriticalFeedback(
        page: Int = 1, 
        size: Int = 20
    ): List<UserFeedback>
    
    /**
     * Update feedback status
     */
    suspend fun updateStatus(
        feedbackId: String, 
        status: FeedbackStatus,
        adminId: String? = null,
        adminNotes: String? = null
    ): UserFeedback?
    
    /**
     * Delete feedback by ID
     */
    suspend fun delete(id: String): Boolean
    
    /**
     * Count total feedback
     */
    suspend fun count(): Long
    
    /**
     * Count total feedback by user
     */
    suspend fun countByUserId(userId: String): Long
    
    /**
     * Count feedback by status
     */
    suspend fun countByStatus(status: FeedbackStatus): Long
    
    /**
     * Count feedback by type
     */
    suspend fun countByType(feedbackType: FeedbackType): Long
    
    /**
     * Get feedback statistics for admin dashboard
     */
    suspend fun getFeedbackStats(): FeedbackStats
    
    /**
     * Get user satisfaction metrics (average ratings by type)
     */
    suspend fun getSatisfactionMetrics(
        feedbackType: FeedbackType? = null,
        days: Int = 30
    ): SatisfactionMetrics
}

/**
 * Feedback statistics for admin dashboard
 */
data class FeedbackStats(
    val totalFeedback: Long,
    val pendingFeedback: Long,
    val resolvedFeedback: Long,
    val criticalFeedback: Long,
    val averageRating: Double?,
    val feedbackByType: Map<FeedbackType, Long>,
    val feedbackByStatus: Map<FeedbackStatus, Long>,
    val recentTrends: Map<String, Long> // Daily counts for last 7 days
)

/**
 * User satisfaction metrics
 */
data class SatisfactionMetrics(
    val averageRating: Double?,
    val totalRatings: Long,
    val ratingDistribution: Map<Int, Long>, // Rating (1-5) to count
    val satisfactionTrend: List<DailySatisfaction>, // Daily satisfaction over time
    val npsScore: Double? // Net Promoter Score if applicable
)

/**
 * Daily satisfaction data point
 */
data class DailySatisfaction(
    val date: String, // YYYY-MM-DD
    val averageRating: Double?,
    val totalRatings: Long
)