package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.ScreenshotRequest
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.ScreenshotStatus
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.TakeScreenshotResponse
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class TakeScreenshotUseCase : KoinComponent {
    private val screenshotRepository: ScreenshotRepository by inject()
    private val queueRepository: QueueRepository by inject()

    suspend operator fun invoke(request: TakeScreenshotRequest): TakeScreenshotResponse {
        val jobId = "job_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

        // Convert DTO to domain request
        val domainRequest = dev.screenshotapi.core.domain.entities.ScreenshotRequest(
            url = request.screenshotRequest.url,
            width = request.screenshotRequest.width,
            height = request.screenshotRequest.height,
            fullPage = request.screenshotRequest.fullPage,
            waitTime = request.screenshotRequest.waitTime,
            waitForSelector = request.screenshotRequest.waitForSelector,
            quality = request.screenshotRequest.quality,
            format = dev.screenshotapi.core.domain.entities.ScreenshotFormat.valueOf(request.screenshotRequest.format.name)
        )

        // Create screenshot job
        val job = ScreenshotJob(
            id = jobId,
            userId = request.userId,
            apiKeyId = request.apiKeyId,
            status = dev.screenshotapi.core.domain.entities.ScreenshotStatus.QUEUED,
            request = domainRequest,
            webhookUrl = request.webhookUrl,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            completedAt = null,
            resultUrl = null,
            processingTimeMs = null,
            errorMessage = null,
            webhookSent = false
        )

        // Save to repository
        screenshotRepository.save(job)

        // Add to queue for processing
        queueRepository.enqueue(job)

        // Get queue position
        val queueSize = queueRepository.size()

        return TakeScreenshotResponse(
            jobId = jobId,
            status = ScreenshotStatus.PENDING,
            estimatedCompletion = "30-60s",
            queuePosition = queueSize.toInt()
        )
    }
}

data class TakeScreenshotRequest(
    val userId: String,
    val apiKeyId: String,
    val screenshotRequest: ScreenshotRequest,
    val webhookUrl: String? = null
)

