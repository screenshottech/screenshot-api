package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.StatsGroupBy
import dev.screenshotapi.core.usecases.admin.ScreenshotStatItem
import kotlinx.datetime.Instant

interface ScreenshotRepository {
    suspend fun save(job: ScreenshotJob): ScreenshotJob
    suspend fun findById(id: String): ScreenshotJob?
    suspend fun findByIdAndUserId(id: String, userId: String): ScreenshotJob?
    suspend fun findByUserId(userId: String, page: Int = 1, limit: Int = 20): List<ScreenshotJob>
    suspend fun findByUserIdAndStatus(userId: String, status: dev.screenshotapi.core.domain.entities.ScreenshotStatus, page: Int = 1, limit: Int = 20): List<ScreenshotJob>
    suspend fun findByIds(ids: List<String>, userId: String): List<ScreenshotJob>
    suspend fun update(job: ScreenshotJob): ScreenshotJob
    suspend fun countByUserId(userId: String): Long
    suspend fun countByUserIdAndStatus(userId: String, status: dev.screenshotapi.core.domain.entities.ScreenshotStatus): Long
    suspend fun findPendingJobs(): List<ScreenshotJob>
    suspend fun getStatsByPeriod(
        startDate: Instant,
        endDate: Instant,
        groupBy: StatsGroupBy
    ): List<ScreenshotStatItem>

    suspend fun getOverallStats(): ScreenshotOverallStats
    suspend fun getStatsByFormat(): Map<String, Long>
    suspend fun getStatsByStatus(): Map<String, Long>
    suspend fun getAverageProcessingTime(): Long
    suspend fun getSuccessRate(): Double
    suspend fun countCreatedToday(): Long
    
    // Retry-related methods
    suspend fun tryLockJob(jobId: String, workerId: String): ScreenshotJob?
    suspend fun findStuckJobs(stuckAfterMinutes: Int = 30, limit: Int = 100): List<ScreenshotJob>
    suspend fun findJobsReadyForRetry(limit: Int = 100): List<ScreenshotJob>
    suspend fun findFailedRetryableJobs(limit: Int = 100): List<ScreenshotJob>
}


data class ScreenshotOverallStats(
    val total: Long,
    val completed: Long,
    val failed: Long,
    val queued: Long,
    val processing: Long,
    val averageProcessingTime: Long,
    val successRate: Double
)
