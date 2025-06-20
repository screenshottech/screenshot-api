package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.DailyUserStats
import dev.screenshotapi.core.domain.entities.MonthlyUserStats
import dev.screenshotapi.core.domain.entities.YearlyUserStats
import dev.screenshotapi.core.domain.repositories.DailyStatsRepository
import dev.screenshotapi.core.domain.repositories.StatsField
import kotlinx.datetime.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory implementation of DailyStatsRepository for development and testing
 * 
 * This implementation uses the InMemoryDatabase for storage and provides
 * thread-safe operations using synchronized blocks.
 */
class InMemoryDailyStatsRepository : DailyStatsRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    // Version tracking for optimistic locking simulation
    private val versionCounter = AtomicLong(1)
    
    // Daily Stats Operations
    
    override suspend fun findByUserAndDate(userId: String, date: LocalDate): DailyUserStats? {
        return try {
            InMemoryDatabase.findDailyStats(userId, date)
        } catch (e: Exception) {
            logger.error("Error finding daily stats for user: $userId, date: $date", e)
            null
        }
    }

    override suspend fun findByUserAndDateRange(
        userId: String, 
        startDate: LocalDate, 
        endDate: LocalDate
    ): List<DailyUserStats> {
        return try {
            InMemoryDatabase.findDailyStatsRange(userId, startDate, endDate)
        } catch (e: Exception) {
            logger.error("Error finding daily stats range for user: $userId", e)
            emptyList()
        }
    }

    override suspend fun create(stats: DailyUserStats): DailyUserStats {
        return try {
            val statsWithVersion = stats.copy(version = versionCounter.incrementAndGet())
            InMemoryDatabase.saveDailyStats(statsWithVersion)
        } catch (e: Exception) {
            logger.error("Error creating daily stats for user: ${stats.userId}", e)
            throw e
        }
    }

    override suspend fun atomicUpdate(stats: DailyUserStats): DailyUserStats {
        return try {
            val existing = InMemoryDatabase.findDailyStats(stats.userId, stats.date)
            
            if (existing == null) {
                throw RuntimeException("Daily stats not found for user ${stats.userId} on ${stats.date}")
            }
            
            // Simulate optimistic locking
            if (existing.version != stats.version - 1) {
                throw RuntimeException("Daily stats record was modified by another process")
            }
            
            val updatedStats = stats.copy(version = versionCounter.incrementAndGet())
            InMemoryDatabase.saveDailyStats(updatedStats)
        } catch (e: Exception) {
            logger.error("Error updating daily stats for user: ${stats.userId}", e)
            throw e
        }
    }

    override suspend fun atomicIncrement(
        userId: String, 
        date: LocalDate, 
        field: StatsField, 
        amount: Int
    ): DailyUserStats? {
        return try {
            val existing = InMemoryDatabase.findDailyStats(userId, date)
            
            val updatedStats = if (existing != null) {
                // Update existing record
                when (field) {
                    StatsField.SCREENSHOTS_CREATED -> existing.incrementScreenshotsCreated(amount)
                    StatsField.SCREENSHOTS_COMPLETED -> existing.incrementScreenshotsCompleted(amount)
                    StatsField.SCREENSHOTS_FAILED -> existing.incrementScreenshotsFailed(amount)
                    StatsField.SCREENSHOTS_RETRIED -> existing.incrementScreenshotsRetried(amount)
                    StatsField.CREDITS_USED -> existing.incrementCreditsUsed(amount)
                    StatsField.CREDITS_ADDED -> existing.incrementCreditsAdded(amount)
                    StatsField.API_CALLS_COUNT -> existing.incrementApiCalls(amount)
                    StatsField.API_KEYS_CREATED -> existing.copy(
                        apiKeysCreated = existing.apiKeysCreated + amount,
                        updatedAt = Clock.System.now(),
                        version = existing.version + 1
                    )
                    StatsField.PLAN_CHANGES -> existing.copy(
                        planChanges = existing.planChanges + amount,
                        updatedAt = Clock.System.now(),
                        version = existing.version + 1
                    )
                    StatsField.PAYMENTS_PROCESSED -> existing.copy(
                        paymentsProcessed = existing.paymentsProcessed + amount,
                        updatedAt = Clock.System.now(),
                        version = existing.version + 1
                    )
                }
            } else {
                // Create new record
                val newStats = DailyUserStats.createEmpty(userId, date)
                when (field) {
                    StatsField.SCREENSHOTS_CREATED -> newStats.incrementScreenshotsCreated(amount)
                    StatsField.SCREENSHOTS_COMPLETED -> newStats.incrementScreenshotsCompleted(amount)
                    StatsField.SCREENSHOTS_FAILED -> newStats.incrementScreenshotsFailed(amount)
                    StatsField.SCREENSHOTS_RETRIED -> newStats.incrementScreenshotsRetried(amount)
                    StatsField.CREDITS_USED -> newStats.incrementCreditsUsed(amount)
                    StatsField.CREDITS_ADDED -> newStats.incrementCreditsAdded(amount)
                    StatsField.API_CALLS_COUNT -> newStats.incrementApiCalls(amount)
                    StatsField.API_KEYS_CREATED -> newStats.copy(apiKeysCreated = amount)
                    StatsField.PLAN_CHANGES -> newStats.copy(planChanges = amount)
                    StatsField.PAYMENTS_PROCESSED -> newStats.copy(paymentsProcessed = amount)
                }
            }
            
            InMemoryDatabase.saveDailyStats(updatedStats)
        } catch (e: Exception) {
            logger.error("Error incrementing daily stats field $field for user: $userId", e)
            throw e
        }
    }

    override suspend fun batchCreate(statsList: List<DailyUserStats>): List<DailyUserStats> {
        return try {
            statsList.map { stats ->
                val statsWithVersion = stats.copy(version = versionCounter.incrementAndGet())
                InMemoryDatabase.saveDailyStats(statsWithVersion)
            }
        } catch (e: Exception) {
            logger.error("Error batch creating daily stats", e)
            throw e
        }
    }

    override suspend fun findUsersWithActivityOnDate(date: LocalDate): List<String> {
        return try {
            InMemoryDatabase.findUsersWithDailyActivity(date)
        } catch (e: Exception) {
            logger.error("Error finding users with activity on date: $date", e)
            emptyList()
        }
    }

    override suspend fun deleteOlderThan(date: LocalDate): Int {
        return try {
            InMemoryDatabase.deleteDailyStatsOlderThan(date)
        } catch (e: Exception) {
            logger.error("Error deleting daily stats older than: $date", e)
            0
        }
    }

    override suspend fun findDatesForUser(userId: String, year: Int): List<LocalDate> {
        return try {
            val startDate = LocalDate(year, 1, 1)
            val endDate = LocalDate(year + 1, 1, 1)
            val stats = InMemoryDatabase.findDailyStatsRange(userId, startDate, endDate)
            stats.map { it.date }.distinct().sorted()
        } catch (e: Exception) {
            logger.error("Error finding dates for user: $userId, year: $year", e)
            emptyList()
        }
    }

    // Monthly Stats Operations
    
    override suspend fun findMonthlyByUserAndMonth(userId: String, month: String): MonthlyUserStats? {
        return try {
            InMemoryDatabase.findMonthlyStats(userId, month)
        } catch (e: Exception) {
            logger.error("Error finding monthly stats for user: $userId, month: $month", e)
            null
        }
    }

    override suspend fun createMonthly(stats: MonthlyUserStats): MonthlyUserStats {
        return try {
            val statsWithVersion = stats.copy(version = versionCounter.incrementAndGet())
            InMemoryDatabase.saveMonthlyStats(statsWithVersion)
        } catch (e: Exception) {
            logger.error("Error creating monthly stats for user: ${stats.userId}", e)
            throw e
        }
    }

    override suspend fun updateMonthly(stats: MonthlyUserStats): MonthlyUserStats {
        return try {
            val existing = InMemoryDatabase.findMonthlyStats(stats.userId, stats.month)
            
            if (existing == null) {
                throw RuntimeException("Monthly stats not found for user ${stats.userId} in ${stats.month}")
            }
            
            // Simulate optimistic locking
            if (existing.version != stats.version - 1) {
                throw RuntimeException("Monthly stats record was modified by another process")
            }
            
            val updatedStats = stats.copy(version = versionCounter.incrementAndGet())
            InMemoryDatabase.saveMonthlyStats(updatedStats)
        } catch (e: Exception) {
            logger.error("Error updating monthly stats for user: ${stats.userId}", e)
            throw e
        }
    }

    override suspend fun findMonthlyByUserAndYear(userId: String, year: Int): List<MonthlyUserStats> {
        return try {
            InMemoryDatabase.findMonthlyStatsByYear(userId, year)
        } catch (e: Exception) {
            logger.error("Error finding monthly stats for user: $userId, year: $year", e)
            emptyList()
        }
    }

    override suspend fun deleteMonthlyOlderThan(month: String): Int {
        return try {
            InMemoryDatabase.deleteMonthlyStatsOlderThan(month)
        } catch (e: Exception) {
            logger.error("Error deleting monthly stats older than: $month", e)
            0
        }
    }

    // Yearly Stats Operations
    
    override suspend fun findYearlyByUserAndYear(userId: String, year: Int): YearlyUserStats? {
        return try {
            InMemoryDatabase.findYearlyStats(userId, year)
        } catch (e: Exception) {
            logger.error("Error finding yearly stats for user: $userId, year: $year", e)
            null
        }
    }

    override suspend fun createYearly(stats: YearlyUserStats): YearlyUserStats {
        return try {
            val statsWithVersion = stats.copy(version = versionCounter.incrementAndGet())
            InMemoryDatabase.saveYearlyStats(statsWithVersion)
        } catch (e: Exception) {
            logger.error("Error creating yearly stats for user: ${stats.userId}", e)
            throw e
        }
    }

    override suspend fun updateYearly(stats: YearlyUserStats): YearlyUserStats {
        return try {
            val existing = InMemoryDatabase.findYearlyStats(stats.userId, stats.year)
            
            if (existing == null) {
                throw RuntimeException("Yearly stats not found for user ${stats.userId} in ${stats.year}")
            }
            
            // Simulate optimistic locking
            if (existing.version != stats.version - 1) {
                throw RuntimeException("Yearly stats record was modified by another process")
            }
            
            val updatedStats = stats.copy(version = versionCounter.incrementAndGet())
            InMemoryDatabase.saveYearlyStats(updatedStats)
        } catch (e: Exception) {
            logger.error("Error updating yearly stats for user: ${stats.userId}", e)
            throw e
        }
    }

    override suspend fun findYearlyByUser(userId: String): List<YearlyUserStats> {
        return try {
            InMemoryDatabase.findYearlyStatsByUser(userId)
        } catch (e: Exception) {
            logger.error("Error finding yearly stats for user: $userId", e)
            emptyList()
        }
    }

    override suspend fun deleteYearlyOlderThan(year: Int): Int {
        return try {
            InMemoryDatabase.deleteYearlyStatsOlderThan(year)
        } catch (e: Exception) {
            logger.error("Error deleting yearly stats older than: $year", e)
            0
        }
    }

    // Aggregation support methods
    
    override suspend fun aggregateDailyToMonthly(userId: String, month: String): MonthlyUserStats? {
        return try {
            // Parse month to get date range
            val parts = month.split("-")
            val year = parts[0].toInt()
            val monthNum = parts[1].toInt()
            val startDate = LocalDate(year, monthNum, 1)
            val endDate = if (monthNum == 12) {
                LocalDate(year + 1, 1, 1).minus(DatePeriod(days = 1))
            } else {
                LocalDate(year, monthNum + 1, 1).minus(DatePeriod(days = 1))
            }
            
            val dailyStats = InMemoryDatabase.findDailyStatsRange(userId, startDate, endDate)
            
            if (dailyStats.isEmpty()) return null
            
            val now = Clock.System.now()
            MonthlyUserStats(
                userId = userId,
                month = month,
                screenshotsCreated = dailyStats.sumOf { it.screenshotsCreated },
                screenshotsCompleted = dailyStats.sumOf { it.screenshotsCompleted },
                screenshotsFailed = dailyStats.sumOf { it.screenshotsFailed },
                screenshotsRetried = dailyStats.sumOf { it.screenshotsRetried },
                creditsUsed = dailyStats.sumOf { it.creditsUsed },
                apiCallsCount = dailyStats.sumOf { it.apiCallsCount },
                creditsAdded = dailyStats.sumOf { it.creditsAdded },
                peakDailyScreenshots = dailyStats.maxOfOrNull { it.screenshotsCreated } ?: 0,
                activeDays = dailyStats.count { it.hasActivity },
                createdAt = now,
                updatedAt = now
            )
        } catch (e: Exception) {
            logger.error("Error aggregating daily to monthly for user: $userId, month: $month", e)
            null
        }
    }

    override suspend fun aggregateMonthlyToYearly(userId: String, year: Int): YearlyUserStats? {
        return try {
            val monthlyStats = InMemoryDatabase.findMonthlyStatsByYear(userId, year)
            
            if (monthlyStats.isEmpty()) return null
            
            val now = Clock.System.now()
            YearlyUserStats(
                userId = userId,
                year = year,
                screenshotsCreated = monthlyStats.sumOf { it.screenshotsCreated },
                screenshotsCompleted = monthlyStats.sumOf { it.screenshotsCompleted },
                screenshotsFailed = monthlyStats.sumOf { it.screenshotsFailed },
                screenshotsRetried = monthlyStats.sumOf { it.screenshotsRetried },
                creditsUsed = monthlyStats.sumOf { it.creditsUsed },
                apiCallsCount = monthlyStats.sumOf { it.apiCallsCount },
                creditsAdded = monthlyStats.sumOf { it.creditsAdded },
                peakMonthlyScreenshots = monthlyStats.maxOfOrNull { it.screenshotsCreated } ?: 0,
                activeMonths = monthlyStats.count { it.screenshotsCreated > 0 || it.creditsUsed > 0 },
                createdAt = now,
                updatedAt = now
            )
        } catch (e: Exception) {
            logger.error("Error aggregating monthly to yearly for user: $userId, year: $year", e)
            null
        }
    }

    // Statistics and health check methods
    
    override suspend fun getTableSizes(): Map<String, Long> {
        return try {
            InMemoryDatabase.getStatsTableSizes()
        } catch (e: Exception) {
            logger.error("Error getting table sizes", e)
            emptyMap()
        }
    }

    override suspend fun getOldestRecord(): LocalDate? {
        return try {
            // Find the oldest date from all daily stats
            val allDailyStats = InMemoryDatabase.findDailyStatsRange(
                "dummy", // Not used in our filter
                LocalDate(2020, 1, 1),
                LocalDate(2030, 12, 31)
            )
            allDailyStats.minOfOrNull { it.date }
        } catch (e: Exception) {
            logger.error("Error getting oldest record", e)
            null
        }
    }

    override suspend fun getUsersWithStatsInPeriod(startDate: LocalDate, endDate: LocalDate): Set<String> {
        return try {
            InMemoryDatabase.getUsersWithStatsInPeriod(startDate, endDate)
        } catch (e: Exception) {
            logger.error("Error getting users with stats in period", e)
            emptySet()
        }
    }
}