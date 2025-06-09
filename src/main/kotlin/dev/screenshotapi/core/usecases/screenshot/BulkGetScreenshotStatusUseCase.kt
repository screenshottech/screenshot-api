package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Bulk Get Screenshot Status Use Case
 * 
 * Efficiently retrieves status for multiple screenshot jobs in a single operation.
 * Optimized for polling scenarios where multiple jobs need status updates.
 */
class BulkGetScreenshotStatusUseCase(
    private val screenshotRepository: ScreenshotRepository
) : UseCase<BulkStatusRequest, BulkStatusResponse> {
    
    override suspend fun invoke(request: BulkStatusRequest): BulkStatusResponse {
        // Validate input
        if (request.jobIds.isEmpty()) {
            return BulkStatusResponse(
                jobs = emptyList(),
                requestedAt = Clock.System.now(),
                totalJobs = 0,
                foundJobs = 0
            )
        }
        
        // Fetch jobs by IDs and user ID for security
        val jobs = screenshotRepository.findByIds(request.jobIds, request.userId)
        
        // Convert to response DTOs
        val jobResponses = jobs.map { job: ScreenshotJob ->
            BulkStatusJobResponse(
                jobId = job.id,
                status = job.status.name,
                resultUrl = job.resultUrl,
                processingTimeMs = job.processingTimeMs,
                completedAt = job.completedAt,
                errorMessage = job.errorMessage
            )
        }
        
        return BulkStatusResponse(
            jobs = jobResponses,
            requestedAt = Clock.System.now(),
            totalJobs = request.jobIds.size,
            foundJobs = jobs.size
        )
    }
}

/**
 * Bulk Status Request
 */
data class BulkStatusRequest(
    val jobIds: List<String>,
    val userId: String
)

/**
 * Individual job status response for bulk operations
 */
data class BulkStatusJobResponse(
    val jobId: String,
    val status: String,
    val resultUrl: String? = null,
    val processingTimeMs: Long? = null,
    val completedAt: Instant? = null,
    val errorMessage: String? = null
)

/**
 * Bulk status response containing all requested job statuses
 */
data class BulkStatusResponse(
    val jobs: List<BulkStatusJobResponse>,
    val requestedAt: Instant,
    val totalJobs: Int,
    val foundJobs: Int
)