package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.StatsGroupBy
import dev.screenshotapi.core.domain.repositories.ScreenshotOverallStats
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.usecases.admin.ScreenshotStatItem
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Database

class PostgreSQLScreenshotRepository(private val database: Database) : ScreenshotRepository {
    override suspend fun save(job: ScreenshotJob): ScreenshotJob {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findById(id: String): ScreenshotJob? {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findByIdAndUserId(id: String, userId: String): ScreenshotJob? {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findByUserId(userId: String, page: Int, limit: Int): List<ScreenshotJob> {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun update(job: ScreenshotJob): ScreenshotJob {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun countByUserId(userId: String): Long {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findPendingJobs(): List<ScreenshotJob> {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun getStatsByPeriod(
        startDate: Instant,
        endDate: Instant,
        groupBy: StatsGroupBy
    ): List<ScreenshotStatItem> {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun getOverallStats(): ScreenshotOverallStats {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun getStatsByFormat(): Map<String, Long> {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun getStatsByStatus(): Map<String, Long> {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun getAverageProcessingTime(): Long {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun getSuccessRate(): Double {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun countCreatedToday(): Long {
        TODO("PostgreSQL implementation not yet completed")
    }
}
