package dev.screenshotapi.core.usecases.feedback

import dev.screenshotapi.core.domain.entities.FeedbackPriority
import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.FeedbackType
import dev.screenshotapi.core.domain.entities.UserFeedback
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.ports.output.UserFeedbackRepository

class GetAllFeedbackUseCase(
    private val feedbackRepository: UserFeedbackRepository
) {
    suspend fun invoke(request: GetAllFeedbackRequest): GetAllFeedbackResponse {
        if (request.page < 1) {
            throw ValidationException.InvalidFormat("page", "must be greater than 0")
        }
        if (request.size < 1 || request.size > 100) {
            throw ValidationException.InvalidFormat("size", "must be between 1 and 100")
        }

        // Since priority filtering is applied in-memory, we may need to fetch more items
        // to account for filtering. For now, we'll fetch a reasonable batch and apply filters.
        val batchSize = if (request.priority != null) (request.size * 3).coerceAtMost(300) else request.size

        val rawFeedback = when {
            request.status != null && request.type != null -> {
                feedbackRepository.findByStatus(request.status, 1, batchSize)
                    .filter { it.feedbackType == request.type }
            }
            request.status != null -> {
                feedbackRepository.findByStatus(request.status, 1, batchSize)
            }
            request.type != null -> {
                feedbackRepository.findByType(request.type, 1, batchSize)
            }
            request.critical -> {
                feedbackRepository.findCriticalFeedback(1, batchSize)
            }
            else -> {
                feedbackRepository.findAll(1, batchSize)
            }
        }

        // Apply priority filter if specified
        val filteredFeedback = if (request.priority != null) {
            rawFeedback.filter { feedback ->
                request.priority.contains(feedback.getPriority())
            }
        } else {
            rawFeedback
        }

        // Apply pagination to filtered results
        val startIndex = (request.page - 1) * request.size
        val feedback = filteredFeedback.drop(startIndex).take(request.size)

        // For priority filtering, we need to get the accurate total count by applying all filters
        val totalCount = if (request.priority != null) {
            // When priority filtering is involved, we need to count all filtered results
            // This is not the most efficient approach, but it ensures accuracy
            // Using a reasonable large number instead of Int.MAX_VALUE to avoid potential memory issues
            val maxCountBatch = 10000
            val allFilteredFeedback = when {
                request.status != null && request.type != null -> {
                    feedbackRepository.findByStatus(request.status, 1, maxCountBatch)
                        .filter { it.feedbackType == request.type }
                        .filter { request.priority.contains(it.getPriority()) }
                }
                request.status != null -> {
                    feedbackRepository.findByStatus(request.status, 1, maxCountBatch)
                        .filter { request.priority.contains(it.getPriority()) }
                }
                request.type != null -> {
                    feedbackRepository.findByType(request.type, 1, maxCountBatch)
                        .filter { request.priority.contains(it.getPriority()) }
                }
                request.critical -> {
                    feedbackRepository.findCriticalFeedback(1, maxCountBatch)
                        .filter { request.priority.contains(it.getPriority()) }
                }
                else -> {
                    feedbackRepository.findAll(1, maxCountBatch)
                        .filter { request.priority.contains(it.getPriority()) }
                }
            }
            allFilteredFeedback.size.toLong()
        } else {
            when {
                request.status != null -> feedbackRepository.countByStatus(request.status)
                request.type != null -> feedbackRepository.countByType(request.type)
                else -> feedbackRepository.count()
            }
        }

        return GetAllFeedbackResponse(
            feedback = feedback,
            totalCount = totalCount,
            page = request.page,
            size = request.size,
            hasMore = (request.page * request.size) < totalCount
        )
    }
}

data class GetAllFeedbackRequest(
    val status: FeedbackStatus? = null,
    val type: FeedbackType? = null,
    val priority: List<FeedbackPriority>? = null,
    val critical: Boolean = false,
    val page: Int = 1,
    val size: Int = 20
)

data class GetAllFeedbackResponse(
    val feedback: List<UserFeedback>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
    val hasMore: Boolean
)
