package dev.screenshotapi.core.usecases.feedback

import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.UserFeedback
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.ports.output.UserFeedbackRepository

class UpdateFeedbackStatusUseCase(
    private val feedbackRepository: UserFeedbackRepository
) {
    suspend fun invoke(request: UpdateFeedbackStatusRequest): UserFeedback {
        if (request.feedbackId.isBlank()) {
            throw ValidationException.Required("feedbackId")
        }
        if (request.adminId.isBlank()) {
            throw ValidationException.Required("adminId")
        }

        val existingFeedback = feedbackRepository.findById(request.feedbackId)
            ?: throw ResourceNotFoundException("Feedback", request.feedbackId)

        if (!isValidStatusTransition(existingFeedback.status, request.status)) {
            throw ValidationException.Custom(
                "Invalid status transition from ${existingFeedback.status} to ${request.status}",
                "status"
            )
        }

        return feedbackRepository.updateStatus(
            feedbackId = request.feedbackId,
            status = request.status,
            adminId = request.adminId,
            adminNotes = request.adminNotes
        ) ?: throw RuntimeException("Failed to update feedback")
    }

    private fun isValidStatusTransition(from: FeedbackStatus, to: FeedbackStatus): Boolean {
        return when (from) {
            FeedbackStatus.PENDING -> true
            FeedbackStatus.REVIEWED -> to != FeedbackStatus.PENDING
            FeedbackStatus.IN_PROGRESS -> to != FeedbackStatus.PENDING
            FeedbackStatus.RESOLVED -> to == FeedbackStatus.RESOLVED
            FeedbackStatus.CLOSED -> to == FeedbackStatus.CLOSED
            FeedbackStatus.ACKNOWLEDGED -> true
        }
    }
}

data class UpdateFeedbackStatusRequest(
    val feedbackId: String,
    val status: FeedbackStatus,
    val adminId: String,
    val adminNotes: String? = null
)