package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.entities.StatsGroupBy
import dev.screenshotapi.core.domain.repositories.ScreenshotOverallStats
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.usecases.admin.ScreenshotStatItem
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class InMemoryScreenshotRepository : ScreenshotRepository {
    
    private val locks = ConcurrentHashMap<String, String>()

    override suspend fun save(job: ScreenshotJob): ScreenshotJob {
        return InMemoryDatabase.saveScreenshot(job)
    }

    override suspend fun findById(id: String): ScreenshotJob? {
        return InMemoryDatabase.findScreenshot(id)
    }

    override suspend fun findByIdAndUserId(id: String, userId: String): ScreenshotJob? {
        val job = InMemoryDatabase.findScreenshot(id)
        return if (job?.userId == userId) job else null
    }

    override suspend fun findByUserId(userId: String, page: Int, limit: Int): List<ScreenshotJob> {
        return InMemoryDatabase.findScreenshotsByUser(userId, page, limit)
    }

    override suspend fun findByUserIdAndStatus(userId: String, status: ScreenshotStatus, page: Int, limit: Int): List<ScreenshotJob> {
        return InMemoryDatabase.findScreenshotsByUserAndStatus(userId, status, page, limit)
    }

    override suspend fun findByIds(ids: List<String>, userId: String): List<ScreenshotJob> {
        return InMemoryDatabase.findScreenshotsByIds(ids, userId)
    }

    override suspend fun update(job: ScreenshotJob): ScreenshotJob {
        return InMemoryDatabase.saveScreenshot(job)
    }

    override suspend fun countByUserId(userId: String): Long {
        return InMemoryDatabase.countScreenshotsByUser(userId)
    }

    override suspend fun countByUserIdAndStatus(userId: String, status: ScreenshotStatus): Long {
        return InMemoryDatabase.countScreenshotsByUserAndStatus(userId, status)
    }

    override suspend fun findPendingJobs(): List<ScreenshotJob> {
        return InMemoryDatabase.findPendingScreenshots()
    }

    override suspend fun getStatsByPeriod(
        startDate: Instant,
        endDate: Instant,
        groupBy: StatsGroupBy
    ): List<ScreenshotStatItem> {
        val screenshots = InMemoryDatabase.getAllScreenshots()
            .filter { it.createdAt >= startDate && it.createdAt <= endDate }

        return when (groupBy) {
            StatsGroupBy.DAY -> groupByDay(screenshots)
            StatsGroupBy.HOUR -> groupByHour(screenshots)
            StatsGroupBy.WEEK -> groupByWeek(screenshots)
            StatsGroupBy.MONTH -> groupByMonth(screenshots)
            StatsGroupBy.STATUS -> groupByStatus(screenshots)
            StatsGroupBy.FORMAT -> groupByFormat(screenshots)
        }
    }

    override suspend fun getOverallStats(): ScreenshotOverallStats {
        val screenshots = InMemoryDatabase.getAllScreenshots()

        val total = screenshots.size.toLong()
        val completed = screenshots.count { it.status == ScreenshotStatus.COMPLETED }.toLong()
        val failed = screenshots.count { it.status == ScreenshotStatus.FAILED }.toLong()
        val queued = screenshots.count { it.status == ScreenshotStatus.QUEUED }.toLong()
        val processing = screenshots.count { it.status == ScreenshotStatus.PROCESSING }.toLong()

        val completedScreenshots = screenshots.filter { it.status == ScreenshotStatus.COMPLETED }
        val averageProcessingTime = if (completedScreenshots.isNotEmpty()) {
            completedScreenshots.mapNotNull { it.processingTimeMs }.average().toLong()
        } else 0L

        val successRate = if (total > 0) (completed.toDouble() / total.toDouble()) else 0.0

        return ScreenshotOverallStats(
            total = total,
            completed = completed,
            failed = failed,
            queued = queued,
            processing = processing,
            averageProcessingTime = averageProcessingTime,
            successRate = successRate
        )
    }

    override suspend fun getStatsByFormat(): Map<String, Long> {
        return InMemoryDatabase.getAllScreenshots()
            .groupBy { it.request.format.name }
            .mapValues { it.value.size.toLong() }
    }

    override suspend fun getStatsByStatus(): Map<String, Long> {
        return InMemoryDatabase.getAllScreenshots()
            .groupBy { it.status.name }
            .mapValues { it.value.size.toLong() }
    }

    override suspend fun getAverageProcessingTime(): Long {
        val completedScreenshots = InMemoryDatabase.getAllScreenshots()
            .filter { it.status == ScreenshotStatus.COMPLETED && it.processingTimeMs != null }

        return if (completedScreenshots.isNotEmpty()) {
            completedScreenshots.mapNotNull { it.processingTimeMs }.average().toLong()
        } else 0L
    }

    override suspend fun getSuccessRate(): Double {
        val screenshots = InMemoryDatabase.getAllScreenshots()
        val total = screenshots.size
        val successful = screenshots.count { it.status == ScreenshotStatus.COMPLETED }

        return if (total > 0) (successful.toDouble() / total.toDouble()) else 0.0
    }

    override suspend fun countCreatedToday(): Long {
        val today = Clock.System.now().minus(1.days)
        return InMemoryDatabase.getAllScreenshots()
            .count { it.createdAt >= today }
            .toLong()
    }

    private fun groupByDay(screenshots: List<ScreenshotJob>): List<ScreenshotStatItem> {
        return screenshots
            .groupBy { it.createdAt.toString().substring(0, 10) } // YYYY-MM-DD
            .map { (date, jobs) ->
                val processingTimes = jobs.mapNotNull { it.processingTimeMs }
                val avgProcessingTime = if (processingTimes.isNotEmpty()) {
                    processingTimes.average().toLong()
                } else null

                ScreenshotStatItem(
                    period = date,
                    count = jobs.size.toLong(),
                    successful = jobs.count { it.status == ScreenshotStatus.COMPLETED }.toLong(),
                    failed = jobs.count { it.status == ScreenshotStatus.FAILED }.toLong(),
                    averageProcessingTime = avgProcessingTime
                )
            }
            .sortedBy { it.period }
    }

    private fun groupByHour(screenshots: List<ScreenshotJob>): List<ScreenshotStatItem> {
        return screenshots
            .groupBy { it.createdAt.toString().substring(0, 13) } // YYYY-MM-DDTHH
            .map { (hour, jobs) ->
                val processingTimes = jobs.mapNotNull { it.processingTimeMs }
                val avgProcessingTime = if (processingTimes.isNotEmpty()) {
                    processingTimes.average().toLong()
                } else null

                ScreenshotStatItem(
                    period = hour,
                    count = jobs.size.toLong(),
                    successful = jobs.count { it.status == ScreenshotStatus.COMPLETED }.toLong(),
                    failed = jobs.count { it.status == ScreenshotStatus.FAILED }.toLong(),
                    averageProcessingTime = avgProcessingTime
                )
            }
            .sortedBy { it.period }
    }

    private fun groupByWeek(screenshots: List<ScreenshotJob>): List<ScreenshotStatItem> {
        return screenshots
            .groupBy {
                val weekOfYear = (it.createdAt.toEpochMilliseconds() / (7 * 24 * 60 * 60 * 1000)) % 52
                "${it.createdAt.toString().substring(0, 4)}-W${weekOfYear.toString().padStart(2, '0')}"
            }
            .map { (week, jobs) ->
                val processingTimes = jobs.mapNotNull { it.processingTimeMs }
                val avgProcessingTime = if (processingTimes.isNotEmpty()) {
                    processingTimes.average().toLong()
                } else null

                ScreenshotStatItem(
                    period = week,
                    count = jobs.size.toLong(),
                    successful = jobs.count { it.status == ScreenshotStatus.COMPLETED }.toLong(),
                    failed = jobs.count { it.status == ScreenshotStatus.FAILED }.toLong(),
                    averageProcessingTime = avgProcessingTime
                )
            }
            .sortedBy { it.period }
    }

    private fun groupByMonth(screenshots: List<ScreenshotJob>): List<ScreenshotStatItem> {
        return screenshots
            .groupBy { it.createdAt.toString().substring(0, 7) } // YYYY-MM
            .map { (month, jobs) ->
                val processingTimes = jobs.mapNotNull { it.processingTimeMs }
                val avgProcessingTime = if (processingTimes.isNotEmpty()) {
                    processingTimes.average().toLong()
                } else null

                ScreenshotStatItem(
                    period = month,
                    count = jobs.size.toLong(),
                    successful = jobs.count { it.status == ScreenshotStatus.COMPLETED }.toLong(),
                    failed = jobs.count { it.status == ScreenshotStatus.FAILED }.toLong(),
                    averageProcessingTime = avgProcessingTime
                )
            }
            .sortedBy { it.period }
    }

    private fun groupByStatus(screenshots: List<ScreenshotJob>): List<ScreenshotStatItem> {
        return screenshots
            .groupBy { it.status.name }
            .map { (status, jobs) ->
                val processingTimes = jobs.mapNotNull { it.processingTimeMs }
                val avgProcessingTime = if (processingTimes.isNotEmpty()) {
                    processingTimes.average().toLong()
                } else null

                ScreenshotStatItem(
                    period = status,
                    count = jobs.size.toLong(),
                    successful = jobs.count { it.status == ScreenshotStatus.COMPLETED }.toLong(),
                    failed = jobs.count { it.status == ScreenshotStatus.FAILED }.toLong(),
                    averageProcessingTime = avgProcessingTime
                )
            }
    }

    private fun groupByFormat(screenshots: List<ScreenshotJob>): List<ScreenshotStatItem> {
        return screenshots
            .groupBy { it.request.format.name }
            .map { (format, jobs) ->
                val processingTimes = jobs.mapNotNull { it.processingTimeMs }
                val avgProcessingTime = if (processingTimes.isNotEmpty()) {
                    processingTimes.average().toLong()
                } else null

                ScreenshotStatItem(
                    period = format,
                    count = jobs.size.toLong(),
                    successful = jobs.count { it.status == ScreenshotStatus.COMPLETED }.toLong(),
                    failed = jobs.count { it.status == ScreenshotStatus.FAILED }.toLong(),
                    averageProcessingTime = avgProcessingTime,
                    formats = mapOf(format to jobs.size.toLong())
                )
            }
    }

    override suspend fun tryLockJob(jobId: String, lockOwner: String): ScreenshotJob? {
        val job = findById(jobId) ?: return null
        
        if (job.isLocked()) {
            return null
        }
        
        if (locks.putIfAbsent(jobId, lockOwner) == null) {
            val lockedJob = job.lock(lockOwner)
            update(lockedJob)
            return lockedJob
        }
        
        return null
    }

    override suspend fun findStuckJobs(stuckAfterMinutes: Int, limit: Int): List<ScreenshotJob> {
        val cutoffTime = Clock.System.now().minus(stuckAfterMinutes.minutes)
        return InMemoryDatabase.getAllScreenshots()
            .filter { job ->
                job.status == ScreenshotStatus.PROCESSING && 
                job.updatedAt < cutoffTime
            }
            .take(limit)
    }

    override suspend fun findFailedRetryableJobs(limit: Int): List<ScreenshotJob> {
        return InMemoryDatabase.getAllScreenshots()
            .filter { job ->
                job.status == ScreenshotStatus.FAILED && 
                job.isRetryable && 
                job.canRetry()
            }
            .take(limit)
    }

    override suspend fun findJobsReadyForRetry(limit: Int): List<ScreenshotJob> {
        val currentTime = Clock.System.now()
        return InMemoryDatabase.getAllScreenshots()
            .filter { job ->
                job.status == ScreenshotStatus.QUEUED &&
                job.isRetryable &&
                job.nextRetryAt != null &&
                job.nextRetryAt <= currentTime
            }
            .take(limit)
    }
}
