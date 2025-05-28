package dev.screenshotapi.core.usecases.admin

class GetScreenshotStatsUseCase {
    suspend operator fun invoke(request: GetScreenshotStatsRequest = GetScreenshotStatsRequest()): GetScreenshotStatsResponse {
        return GetScreenshotStatsResponse(
            totalScreenshots = 0,
            period = request.period,
            groupBy = request.groupBy,
            data = emptyList()
        )
    }
}
