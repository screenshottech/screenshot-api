package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotStatus as DomainStatus
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException

/**
 * Use case for retrieving the status of a screenshot job (pure domain).
 */
class GetScreenshotStatusUseCase(
    private val screenshotRepository: ScreenshotRepository
) {
    suspend operator fun invoke(request: Request): Response {
        val job = screenshotRepository.findByIdAndUserId(request.jobId, request.userId)
            ?: throw ResourceNotFoundException("Screenshot", request.jobId)

        return Response(
            jobId = job.id,
            status = job.status,
            resultUrl = job.resultUrl,
            createdAt = job.createdAt.toString(),
            completedAt = job.completedAt?.toString(),
            processingTimeMs = job.processingTimeMs,
            errorMessage = job.errorMessage,
            request = job.request
        )
    }

    data class Request(
        val jobId: String,
        val userId: String
    )
    data class Response(
        val jobId: String,
        val status: DomainStatus,
        val resultUrl: String?,
        val createdAt: String,
        val completedAt: String?,
        val processingTimeMs: Long?,
        val errorMessage: String?,
        val request: dev.screenshotapi.core.domain.entities.ScreenshotRequest
    )
}
