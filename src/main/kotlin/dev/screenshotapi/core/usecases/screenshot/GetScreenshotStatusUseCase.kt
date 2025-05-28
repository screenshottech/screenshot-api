package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GetScreenshotStatusUseCase : KoinComponent {
    private val screenshotRepository: ScreenshotRepository by inject()

    suspend operator fun invoke(request: GetScreenshotStatusRequest): GetScreenshotStatusResponse {

        val job = screenshotRepository.findByIdAndUserId(request.jobId, request.userId)
            ?: throw ResourceNotFoundException("Screenshot", request.jobId)

        return GetScreenshotStatusResponse(
            job = ScreenshotJob(
                id = job.id,
                status = when (job.status) {
                    dev.screenshotapi.core.domain.entities.ScreenshotStatus.QUEUED -> ScreenshotStatus.PENDING
                    dev.screenshotapi.core.domain.entities.ScreenshotStatus.PROCESSING -> ScreenshotStatus.PROCESSING
                    dev.screenshotapi.core.domain.entities.ScreenshotStatus.COMPLETED -> ScreenshotStatus.COMPLETED
                    dev.screenshotapi.core.domain.entities.ScreenshotStatus.FAILED -> ScreenshotStatus.FAILED
                },
                resultUrl = job.resultUrl,
                createdAt = job.createdAt.toString(),
                completedAt = job.completedAt?.toString(),
                processingTimeMs = job.processingTimeMs,
                errorMessage = job.errorMessage,
                request = ScreenshotRequest(
                    url = job.request.url,
                    width = job.request.width,
                    height = job.request.height,
                    fullPage = job.request.fullPage,
                    waitTime = job.request.waitTime,
                    waitForSelector = job.request.waitForSelector,
                    quality = job.request.quality,
                    format = when (job.request.format) {
                        dev.screenshotapi.core.domain.entities.ScreenshotFormat.PNG -> ScreenshotFormat.PNG
                        dev.screenshotapi.core.domain.entities.ScreenshotFormat.JPEG -> ScreenshotFormat.JPEG
                        dev.screenshotapi.core.domain.entities.ScreenshotFormat.PDF -> ScreenshotFormat.PDF
                    }
                )
            )
        )
    }
}

data class GetScreenshotStatusRequest(
    val jobId: String,
    val userId: String
)
