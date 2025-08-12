package dev.screenshotapi.core.usecases.feedback

import dev.screenshotapi.core.domain.entities.FeedbackType
import dev.screenshotapi.core.ports.output.FeedbackStats
import dev.screenshotapi.core.ports.output.SatisfactionMetrics
import dev.screenshotapi.core.ports.output.UserFeedbackRepository
import org.slf4j.LoggerFactory

/**
 * Use case for getting feedback analytics for admin dashboard
 */
class GetFeedbackAnalyticsUseCase(
    private val feedbackRepository: UserFeedbackRepository
) {
    private val logger = LoggerFactory.getLogger(GetFeedbackAnalyticsUseCase::class.java)

    suspend fun invoke(request: GetFeedbackAnalyticsRequest): GetFeedbackAnalyticsResponse {
        logger.debug("Getting feedback analytics")

        return try {
            val feedbackStats = feedbackRepository.getFeedbackStats()
            
            val satisfactionMetrics = feedbackRepository.getSatisfactionMetrics(
                feedbackType = request.feedbackType,
                days = request.days
            )

            GetFeedbackAnalyticsResponse.Success(
                stats = feedbackStats,
                satisfaction = satisfactionMetrics
            )

        } catch (e: Exception) {
            logger.error("Error getting feedback analytics", e)
            GetFeedbackAnalyticsResponse.Error("Failed to get feedback analytics: ${e.message}")
        }
    }
}

/**
 * Request for getting feedback analytics
 */
data class GetFeedbackAnalyticsRequest(
    val feedbackType: FeedbackType? = null,
    val days: Int = 30
)

/**
 * Response from getting feedback analytics
 */
sealed class GetFeedbackAnalyticsResponse {
    data class Success(
        val stats: FeedbackStats,
        val satisfaction: SatisfactionMetrics
    ) : GetFeedbackAnalyticsResponse()
    
    data class Error(val message: String) : GetFeedbackAnalyticsResponse()
}