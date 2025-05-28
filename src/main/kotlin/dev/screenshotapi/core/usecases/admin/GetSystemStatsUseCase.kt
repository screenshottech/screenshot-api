package dev.screenshotapi.core.usecases.admin

class GetSystemStatsUseCase {
    suspend operator fun invoke(request: GetSystemStatsRequest = GetSystemStatsRequest()): GetSystemStatsResponse {
        return GetSystemStatsResponse(
            overview = SystemOverview(
                totalUsers = 0,
                activeUsers = 0,
                totalScreenshots = 0,
                screenshotsToday = 0,
                successRate = 0.0,
                averageProcessingTime = 0,
                queueSize = 0,
                activeWorkers = 0
            ),
            screenshots = ScreenshotStats(
                total = 0,
                completed = 0,
                failed = 0,
                queued = 0,
                processing = 0,
                successRate = 0.0,
                averageProcessingTime = 0,
                byFormat = emptyMap(),
                byStatus = emptyMap()
            ),
            users = UserStats(
                total = 0,
                active = 0,
                suspended = 0,
                newToday = 0,
                newThisWeek = 0,
                newThisMonth = 0,
                topUsers = emptyList()
            ),
            performance = PerformanceStats(
                averageResponseTime = 0,
                p95ResponseTime = 0,
                p99ResponseTime = 0,
                errorRate = 0.0,
                throughput = 0.0,
                memoryUsage = MemoryUsage(used = 0, total = 0, percentage = 0),
                workerUtilization = 0.0
            ),
            breakdown = emptyList()
        )
    }
}
