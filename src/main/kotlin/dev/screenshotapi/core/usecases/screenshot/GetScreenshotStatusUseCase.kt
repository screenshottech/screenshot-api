package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.PageMetadata
import dev.screenshotapi.core.domain.entities.ScreenshotStatus as DomainStatus
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import org.slf4j.LoggerFactory

/**
 * Use case for retrieving the status of a screenshot job (pure domain).
 */
class GetScreenshotStatusUseCase(
    private val screenshotRepository: ScreenshotRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    suspend operator fun invoke(request: Request): Response {
        logger.debug("Querying job status: jobId={}, userId={}", request.jobId, request.userId)
        
        val job = screenshotRepository.findByIdAndUserId(request.jobId, request.userId)
            ?: throw ResourceNotFoundException("Screenshot", request.jobId)
            
        logger.debug("Job status query result: jobId={}, status={}, processingTime={}ms", 
            job.id, job.status.name, job.processingTimeMs ?: 0)

        return Response(
            jobId = job.id,
            status = job.status,
            resultUrl = job.resultUrl,
            createdAt = job.createdAt.toString(),
            completedAt = job.completedAt?.toString(),
            processingTimeMs = job.processingTimeMs,
            fileSizeBytes = job.fileSizeBytes,
            errorMessage = job.errorMessage,
            request = job.request,
            metadata = job.metadata
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
        val fileSizeBytes: Long?,
        val errorMessage: String?,
        val request: dev.screenshotapi.core.domain.entities.ScreenshotRequest,
        val metadata: PageMetadata? = null
    )
}
