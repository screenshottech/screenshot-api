package dev.screenshotapi.core.usecases.feedback

import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.UserFeedback
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.ports.output.UserFeedbackRepository

class ResolveFeedbackUseCase(
    private val feedbackRepository: UserFeedbackRepository
) {
    suspend fun invoke(request: ResolveFeedbackRequest): UserFeedback {
        if (request.feedbackId.isBlank()) {
            throw ValidationException.Required("feedbackId")
        }
        if (request.adminId.isBlank()) {
            throw ValidationException.Required("adminId")
        }

        val existingFeedback = feedbackRepository.findById(request.feedbackId)
            ?: throw ResourceNotFoundException("Feedback", request.feedbackId)

        if (existingFeedback.status == FeedbackStatus.RESOLVED) {
            throw ValidationException.Custom("Feedback is already resolved", "status")
        }

        return feedbackRepository.updateStatus(
            feedbackId = request.feedbackId,
            status = FeedbackStatus.RESOLVED,
            adminId = request.adminId,
            adminNotes = request.resolutionNotes
        ) ?: throw RuntimeException("Failed to resolve feedback")
    }
}

data class ResolveFeedbackRequest(
    val feedbackId: String,
    val adminId: String,
    val resolutionNotes: String? = null
)