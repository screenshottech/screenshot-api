package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ListScreenshotsUseCase : KoinComponent {
    private val screenshotRepository: ScreenshotRepository by inject()

    suspend operator fun invoke(request: ListScreenshotsRequest): ListScreenshotsResponse {

        val jobs = screenshotRepository.findByUserId(request.userId, request.page, request.limit)
        val total = screenshotRepository.countByUserId(request.userId)

        val screenshotDtos = jobs.map { job ->
            ScreenshotJob(
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
        }

        return ListScreenshotsResponse(
            screenshots = screenshotDtos,
            page = request.page,
            limit = request.limit,
            total = total
        )
    }
}

data class ListScreenshotsRequest(
    val userId: String,
    val page: Int = 1,
    val limit: Int = 20,
    val status: ScreenshotStatus? = null
)
