package dev.screenshotapi.infrastructure.adapters.output.persistence

import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.FeedbackType
import dev.screenshotapi.core.domain.entities.UserFeedback
import dev.screenshotapi.core.ports.output.DailySatisfaction
import dev.screenshotapi.core.ports.output.FeedbackStats
import dev.screenshotapi.core.ports.output.SatisfactionMetrics
import dev.screenshotapi.core.ports.output.UserFeedbackRepository
import kotlinx.datetime.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of UserFeedbackRepository for development and testing
 */
class InMemoryUserFeedbackRepository : UserFeedbackRepository {

    private val feedbackStorage = ConcurrentHashMap<String, UserFeedback>()

    override suspend fun save(feedback: UserFeedback): UserFeedback {
        feedbackStorage[feedback.id] = feedback
        return feedback
    }

    override suspend fun findById(id: String): UserFeedback? {
        return feedbackStorage[id]
    }

    override suspend fun findAll(page: Int, size: Int): List<UserFeedback> {
        return feedbackStorage.values
            .sortedByDescending { it.createdAt }
            .drop((page - 1) * size)
            .take(size)
    }

    override suspend fun findByUserId(userId: String, page: Int, size: Int): List<UserFeedback> {
        return feedbackStorage.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
            .drop((page - 1) * size)
            .take(size)
    }

    override suspend fun findByUserIdAndStatus(
        userId: String, 
        status: FeedbackStatus, 
        page: Int, 
        size: Int
    ): List<UserFeedback> {
        return feedbackStorage.values
            .filter { it.userId == userId && it.status == status }
            .sortedByDescending { it.createdAt }
            .drop((page - 1) * size)
            .take(size)
    }

    override suspend fun findByType(feedbackType: FeedbackType, page: Int, size: Int): List<UserFeedback> {
        return feedbackStorage.values
            .filter { it.feedbackType == feedbackType }
            .sortedByDescending { it.createdAt }
            .drop((page - 1) * size)
            .take(size)
    }

    override suspend fun findByStatus(status: FeedbackStatus, page: Int, size: Int): List<UserFeedback> {
        return feedbackStorage.values
            .filter { it.status == status }
            .sortedByDescending { it.createdAt }
            .drop((page - 1) * size)
            .take(size)
    }

    override suspend fun findByDateRange(
        startDate: Instant, 
        endDate: Instant, 
        page: Int, 
        size: Int
    ): List<UserFeedback> {
        return feedbackStorage.values
            .filter { it.createdAt >= startDate && it.createdAt <= endDate }
            .sortedByDescending { it.createdAt }
            .drop((page - 1) * size)
            .take(size)
    }

    override suspend fun findCriticalFeedback(page: Int, size: Int): List<UserFeedback> {
        return feedbackStorage.values
            .filter { it.isCritical() && !it.isResolved() }
            .sortedByDescending { it.createdAt }
            .drop((page - 1) * size)
            .take(size)
    }

    override suspend fun updateStatus(
        feedbackId: String, 
        status: FeedbackStatus, 
        adminId: String?, 
        adminNotes: String?
    ): UserFeedback? {
        val feedback = feedbackStorage[feedbackId] ?: return null
        val updated = feedback.updateStatus(status, adminId, adminNotes)
        feedbackStorage[feedbackId] = updated
        return updated
    }

    override suspend fun delete(id: String): Boolean {
        return feedbackStorage.remove(id) != null
    }

    override suspend fun count(): Long {
        return feedbackStorage.size.toLong()
    }

    override suspend fun countByUserId(userId: String): Long {
        return feedbackStorage.values.count { it.userId == userId }.toLong()
    }

    override suspend fun countByStatus(status: FeedbackStatus): Long {
        return feedbackStorage.values.count { it.status == status }.toLong()
    }

    override suspend fun countByType(feedbackType: FeedbackType): Long {
        return feedbackStorage.values.count { it.feedbackType == feedbackType }.toLong()
    }

    override suspend fun getFeedbackStats(): FeedbackStats {
        val allFeedback = feedbackStorage.values.toList()
        
        val totalFeedback = allFeedback.size.toLong()
        val pendingFeedback = allFeedback.count { it.status == FeedbackStatus.PENDING }.toLong()
        val resolvedFeedback = allFeedback.count { it.isResolved() }.toLong()
        val criticalFeedback = allFeedback.count { it.isCritical() && !it.isResolved() }.toLong()
        
        val ratingsWithValues = allFeedback.mapNotNull { it.rating }
        val averageRating = if (ratingsWithValues.isNotEmpty()) ratingsWithValues.average() else null
        
        val feedbackByType = FeedbackType.values().associateWith { type ->
            allFeedback.count { it.feedbackType == type }.toLong()
        }
        
        val feedbackByStatus = FeedbackStatus.values().associateWith { status ->
            allFeedback.count { it.status == status }.toLong()
        }
        
        // Recent trends (last 7 days)
        val recentTrends = mutableMapOf<String, Long>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        for (i in 0..6) {
            val date = today.minus(i, DateTimeUnit.DAY)
            val dateStr = date.toString()
            val startOfDay = date.atStartOfDayIn(TimeZone.UTC)
            val endOfDay = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC)
            
            val count = allFeedback.count { 
                it.createdAt >= startOfDay && it.createdAt < endOfDay 
            }.toLong()
            recentTrends[dateStr] = count
        }

        return FeedbackStats(
            totalFeedback = totalFeedback,
            pendingFeedback = pendingFeedback,
            resolvedFeedback = resolvedFeedback,
            criticalFeedback = criticalFeedback,
            averageRating = averageRating,
            feedbackByType = feedbackByType,
            feedbackByStatus = feedbackByStatus,
            recentTrends = recentTrends
        )
    }

    override suspend fun getSatisfactionMetrics(
        feedbackType: FeedbackType?, 
        days: Int
    ): SatisfactionMetrics {
        val sinceDate = Clock.System.now().minus(days, DateTimeUnit.DAY, TimeZone.UTC)
        
        val relevantFeedback = feedbackStorage.values
            .filter { it.createdAt >= sinceDate }
            .filter { feedbackType == null || it.feedbackType == feedbackType }
            .mapNotNull { feedback -> feedback.rating?.let { rating -> feedback to rating } }
        
        val ratings = relevantFeedback.map { it.second }
        
        val averageRating = if (ratings.isNotEmpty()) ratings.average() else null
        val totalRatings = ratings.size.toLong()
        
        val ratingDistribution = ratings.groupingBy { it }.eachCount().mapValues { it.value.toLong() }
        
        // Daily satisfaction trend
        val satisfactionTrend = mutableListOf<DailySatisfaction>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        
        for (i in 0 until days) {
            val date = today.minus(i, DateTimeUnit.DAY)
            val dateStr = date.toString()
            val startOfDay = date.atStartOfDayIn(TimeZone.UTC)
            val endOfDay = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC)
            
            val dailyRatings = relevantFeedback
                .filter { it.first.createdAt >= startOfDay && it.first.createdAt < endOfDay }
                .map { it.second }
            
            val dailyAverage = if (dailyRatings.isNotEmpty()) dailyRatings.average() else null
            
            satisfactionTrend.add(DailySatisfaction(
                date = dateStr,
                averageRating = dailyAverage,
                totalRatings = dailyRatings.size.toLong()
            ))
        }

        // Simple NPS calculation
        val npsScore = if (ratings.isNotEmpty()) {
            val promoters = ratings.count { it >= 4 }
            val detractors = ratings.count { it <= 2 }
            ((promoters - detractors).toDouble() / ratings.size) * 100
        } else null

        return SatisfactionMetrics(
            averageRating = averageRating,
            totalRatings = totalRatings,
            ratingDistribution = ratingDistribution,
            satisfactionTrend = satisfactionTrend.reversed(),
            npsScore = npsScore
        )
    }

    // Utility method to populate with sample data for development
    fun populateWithSampleData() {
        val sampleFeedback = listOf(
            UserFeedback.create(
                id = "feedback-1",
                userId = "user-1",
                feedbackType = FeedbackType.SATISFACTION,
                message = "Great service! The screenshot quality is excellent.",
                rating = 5,
                subject = "Excellent service"
            ),
            UserFeedback.create(
                id = "feedback-2", 
                userId = "user-2",
                feedbackType = FeedbackType.FEATURE_REQUEST,
                message = "Could you add support for mobile device simulation?",
                subject = "Mobile simulation request"
            ),
            UserFeedback.create(
                id = "feedback-3",
                userId = "user-3", 
                feedbackType = FeedbackType.BUG_REPORT,
                message = "Screenshots are sometimes missing for complex JavaScript sites.",
                rating = 2,
                subject = "JavaScript rendering issue"
            ),
            UserFeedback.create(
                id = "feedback-4",
                userId = "user-1",
                feedbackType = FeedbackType.CONVERSION_EXPERIENCE,
                message = "The upgrade process was smooth and the AI analysis features are worth it!",
                rating = 5,
                subject = "Smooth upgrade experience"
            )
        )
        
        sampleFeedback.forEach { feedback ->
            feedbackStorage[feedback.id] = feedback
        }
    }
}