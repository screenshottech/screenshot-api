package dev.screenshotapi.core.usecases.feedback

import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.UserFeedback
import dev.screenshotapi.core.ports.output.UserFeedbackRepository
import org.slf4j.LoggerFactory

/**
 * Use case for retrieving user feedback
 */
class GetUserFeedbackUseCase(
    private val feedbackRepository: UserFeedbackRepository
) {
    private val logger = LoggerFactory.getLogger(GetUserFeedbackUseCase::class.java)

    suspend fun invoke(request: GetUserFeedbackRequest): GetUserFeedbackResponse {
        logger.debug("Getting feedback for user: ${request.userId}")

        return try {
            val feedback = when {
                request.status != null -> {
                    feedbackRepository.findByUserIdAndStatus(
                        userId = request.userId,
                        status = request.status,
                        page = request.page,
                        size = request.size
                    )
                }
                else -> {
                    feedbackRepository.findByUserId(
                        userId = request.userId,
                        page = request.page,
                        size = request.size
                    )
                }
            }

            val totalCount = feedbackRepository.countByUserId(request.userId)

            GetUserFeedbackResponse.Success(
                feedback = feedback,
                totalCount = totalCount,
                page = request.page,
                size = request.size,
                hasMore = (request.page * request.size) < totalCount
            )

        } catch (e: Exception) {
            logger.error("Error getting feedback for user: ${request.userId}", e)
            GetUserFeedbackResponse.Error("Failed to get feedback: ${e.message}")
        }
    }
}

/**
 * Request for getting user feedback
 */
data class GetUserFeedbackRequest(
    val userId: String,
    val status: FeedbackStatus? = null,
    val page: Int = 1,
    val size: Int = 20
)

/**
 * Response from getting user feedback
 */
sealed class GetUserFeedbackResponse {
    data class Success(
        val feedback: List<UserFeedback>,
        val totalCount: Long,
        val page: Int,
        val size: Int,
        val hasMore: Boolean
    ) : GetUserFeedbackResponse()
    
    data class Error(val message: String) : GetUserFeedbackResponse()
}