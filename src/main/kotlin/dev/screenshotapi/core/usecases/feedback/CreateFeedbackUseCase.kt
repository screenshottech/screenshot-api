package dev.screenshotapi.core.usecases.feedback

import dev.screenshotapi.core.domain.entities.FeedbackType
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.entities.UserFeedback
import dev.screenshotapi.core.ports.output.UserFeedbackRepository
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Use case for creating user feedback
 */
class CreateFeedbackUseCase(
    private val feedbackRepository: UserFeedbackRepository,
    private val logUsageUseCase: LogUsageUseCase
) {
    private val logger = LoggerFactory.getLogger(CreateFeedbackUseCase::class.java)

    suspend fun invoke(request: CreateFeedbackRequest): CreateFeedbackResponse {
        logger.info("Creating feedback for user: ${request.userId}, type: ${request.feedbackType}")

        return try {
            // Validate input
            val validationError = validateRequest(request)
            if (validationError != null) {
                logger.warn("Invalid feedback request: $validationError")
                return CreateFeedbackResponse.Error(validationError)
            }

            // Create feedback entity
            val feedbackId = generateFeedbackId()
            val feedback = UserFeedback.create(
                id = feedbackId,
                userId = request.userId,
                feedbackType = request.feedbackType,
                message = request.message,
                rating = request.rating,
                subject = request.subject,
                metadata = request.metadata,
                userAgent = request.userAgent,
                ipAddress = request.ipAddress
            )

            // Save feedback
            val savedFeedback = feedbackRepository.save(feedback)

            // Log usage event
            logUsageUseCase.invoke(
                LogUsageUseCase.Request(
                    userId = request.userId,
                    action = UsageLogAction.FEEDBACK_SUBMITTED,
                    creditsUsed = 0, // No credits for feedback
                    metadata = mapOf(
                        "feedbackId" to feedbackId,
                        "feedbackType" to request.feedbackType.name,
                        "rating" to (request.rating?.toString() ?: "null"),
                        "critical" to savedFeedback.isCritical().toString()
                    )
                )
            )

            logger.info("Successfully created feedback: $feedbackId for user: ${request.userId}")
            CreateFeedbackResponse.Success(savedFeedback)

        } catch (e: Exception) {
            logger.error("Error creating feedback for user: ${request.userId}", e)
            CreateFeedbackResponse.Error("Failed to create feedback: ${e.message}")
        }
    }

    private fun validateRequest(request: CreateFeedbackRequest): String? {
        if (request.userId.isBlank()) {
            return "User ID is required"
        }

        if (request.message.isBlank()) {
            return "Feedback message is required"
        }

        if (request.message.length > 5000) {
            return "Feedback message is too long (max 5000 characters)"
        }

        if (request.subject != null && request.subject.length > 255) {
            return "Subject is too long (max 255 characters)"
        }

        if (request.rating != null && (request.rating < 1 || request.rating > 5)) {
            return "Rating must be between 1 and 5"
        }

        return null
    }

    private fun generateFeedbackId(): String {
        return "feedback_${UUID.randomUUID()}"
    }
}

/**
 * Request for creating feedback
 */
data class CreateFeedbackRequest(
    val userId: String,
    val feedbackType: FeedbackType,
    val message: String,
    val rating: Int? = null,
    val subject: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val ipAddress: String? = null
)

/**
 * Response from creating feedback
 */
sealed class CreateFeedbackResponse {
    data class Success(val feedback: UserFeedback) : CreateFeedbackResponse()
    data class Error(val message: String) : CreateFeedbackResponse()
}
