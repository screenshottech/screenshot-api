package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotStatus as DomainStatus
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository

/**
 * Use case for listing screenshot jobs for a user (pure domain).
 */
class ListScreenshotsUseCase(
    private val screenshotRepository: ScreenshotRepository
) {
    suspend operator fun invoke(request: Request): Response {
        val jobs = screenshotRepository.findByUserId(request.userId, request.page, request.limit)
        val total = screenshotRepository.countByUserId(request.userId)
        return Response(
            screenshots = jobs,
            page = request.page,
            limit = request.limit,
            total = total
        )
    }

    data class Request(
        val userId: String,
        val page: Int = 1,
        val limit: Int = 20,
        val status: DomainStatus? = null
    )
    data class Response(
        val screenshots: List<ScreenshotJob>,
        val page: Int,
        val limit: Int,
        val total: Long
    )
}
