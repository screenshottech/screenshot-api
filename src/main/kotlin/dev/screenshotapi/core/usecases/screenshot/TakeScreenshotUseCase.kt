package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.domain.entities.ScreenshotStatus as DomainStatus
import dev.screenshotapi.core.domain.entities.ScreenshotFormat
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Use case for creating a new screenshot job.
 * Pure domain logic, no framework or infrastructure dependencies.
 */
class TakeScreenshotUseCase(
    private val screenshotRepository: ScreenshotRepository,
    private val queueRepository: QueueRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    suspend operator fun invoke(request: Request): Response {
        val jobId = "job_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        
        logger.info("Creating screenshot job: jobId={}, userId={}, url={}, format={}, dimensions={}x{}", 
            jobId, request.userId, request.screenshotRequest.url, 
            request.screenshotRequest.format.name,
            request.screenshotRequest.width, request.screenshotRequest.height)

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
        
        logger.info("Screenshot job created and enqueued: jobId={}, queuePosition={}, estimatedCompletion=30-60s", 
            jobId, queueSize)

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
