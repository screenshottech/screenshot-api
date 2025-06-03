package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.domain.entities.ScreenshotStatus as DomainStatus
import dev.screenshotapi.core.domain.entities.ScreenshotFormat
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import kotlinx.datetime.Clock
import java.util.*

/**
 * Use case for creating a new screenshot job.
 * Pure domain logic, no framework or infrastructure dependencies.
 */
class TakeScreenshotUseCase(
    private val screenshotRepository: ScreenshotRepository,
    private val queueRepository: QueueRepository
) {
    suspend operator fun invoke(request: Request): Response {
        val jobId = "job_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

        // Create screenshot job
        val job = ScreenshotJob(
            id = jobId,
            userId = request.userId,
            apiKeyId = request.apiKeyId,
            status = DomainStatus.QUEUED,
            request = request.screenshotRequest,
            webhookUrl = request.webhookUrl,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            completedAt = null,
            resultUrl = null,
            processingTimeMs = null,
            errorMessage = null,
            webhookSent = false
        )

        // Save and enqueue
        screenshotRepository.save(job)
        queueRepository.enqueue(job)
        val queueSize = queueRepository.size()

        return Response(
            jobId = jobId,
            status = DomainStatus.QUEUED,
            estimatedCompletion = "30-60s",
            queuePosition = queueSize.toInt()
        )
    }

    data class Request(
        val userId: String,
        val apiKeyId: String,
        val screenshotRequest: ScreenshotRequest,
        val webhookUrl: String? = null
    )
    data class Response(
        val jobId: String,
        val status: DomainStatus,
        val estimatedCompletion: String,
        val queuePosition: Int
    )
}
