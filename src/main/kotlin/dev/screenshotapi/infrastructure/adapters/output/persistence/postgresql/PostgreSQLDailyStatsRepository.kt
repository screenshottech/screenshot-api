package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.DailyUserStats
import dev.screenshotapi.core.domain.entities.MonthlyUserStats
import dev.screenshotapi.core.domain.entities.YearlyUserStats
import dev.screenshotapi.core.domain.repositories.DailyStatsRepository
import dev.screenshotapi.core.domain.repositories.StatsField
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.DailyUserStatsTable
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.MonthlyUserStatsTable
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.YearlyUserStatsTable
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * PostgreSQL implementation of DailyStatsRepository with atomic operations
 *
 * This implementation ensures data consistency in distributed environments
 * using database-level atomic operations and optimistic locking.
 */
class PostgreSQLDailyStatsRepository(private val database: Database) : DailyStatsRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Daily Stats Operations

    override suspend fun findByUserAndDate(userId: String, date: LocalDate): DailyUserStats? {
        return try {
            dbQuery(database) {
                DailyUserStatsTable.select {
                    (DailyUserStatsTable.userId eq userId) and (DailyUserStatsTable.date eq date)
                }.map { row ->
                    row.toDailyUserStats()
                }.singleOrNull()
            }
        } catch (e: Exception) {
            logger.error("Error finding daily stats for user: $userId, date: $date", e)
            throw DatabaseException.OperationFailed("Failed to find daily stats", e)
        }
    }

    override suspend fun findByUserAndDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyUserStats> {
        return try {
            dbQuery(database) {
                DailyUserStatsTable.select {
                    (DailyUserStatsTable.userId eq userId) and
                    (DailyUserStatsTable.date greaterEq startDate) and
                    (DailyUserStatsTable.date lessEq endDate)
                }.orderBy(DailyUserStatsTable.date to SortOrder.ASC)
                .map { row ->
                    row.toDailyUserStats()
                }
            }
        } catch (e: Exception) {
            logger.error("Error finding daily stats range for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find daily stats range", e)
        }
    }

    override suspend fun create(stats: DailyUserStats): DailyUserStats {
        return try {
            dbQuery(database) {
                val id = UUID.randomUUID().toString()
                DailyUserStatsTable.insert {
                    it[DailyUserStatsTable.id] = id
                    it[userId] = stats.userId
                    it[date] = stats.date
                    it[screenshotsCreated] = stats.screenshotsCreated
                    it[screenshotsCompleted] = stats.screenshotsCompleted
                    it[screenshotsFailed] = stats.screenshotsFailed
                    it[screenshotsRetried] = stats.screenshotsRetried
                    it[creditsUsed] = stats.creditsUsed
                    it[apiCallsCount] = stats.apiCallsCount
                    it[apiKeysUsed] = stats.apiKeysUsed
                    it[creditsAdded] = stats.creditsAdded
                    it[paymentsProcessed] = stats.paymentsProcessed
                    it[apiKeysCreated] = stats.apiKeysCreated
                    it[planChanges] = stats.planChanges
                    it[createdAt] = stats.createdAt
                    it[updatedAt] = stats.updatedAt
                    it[version] = stats.version
                }
                stats
            }
        } catch (e: Exception) {
            logger.error("Error creating daily stats for user: ${stats.userId}", e)
            throw DatabaseException.OperationFailed("Failed to create daily stats", e)
        }
    }

    override suspend fun atomicUpdate(stats: DailyUserStats): DailyUserStats {
        return try {
            dbQuery(database) {
                val updatedRows = DailyUserStatsTable.update({
                    (DailyUserStatsTable.userId eq stats.userId) and
                    (DailyUserStatsTable.date eq stats.date) and
                    (DailyUserStatsTable.version eq stats.version - 1) // Optimistic locking
                }) {
                    it[screenshotsCreated] = stats.screenshotsCreated
                    it[screenshotsCompleted] = stats.screenshotsCompleted
                    it[screenshotsFailed] = stats.screenshotsFailed
                    it[screenshotsRetried] = stats.screenshotsRetried
                    it[creditsUsed] = stats.creditsUsed
                    it[apiCallsCount] = stats.apiCallsCount
                    it[apiKeysUsed] = stats.apiKeysUsed
                    it[creditsAdded] = stats.creditsAdded
                    it[paymentsProcessed] = stats.paymentsProcessed
                    it[apiKeysCreated] = stats.apiKeysCreated
                    it[planChanges] = stats.planChanges
                    it[updatedAt] = stats.updatedAt
                    it[version] = stats.version
                }

                if (updatedRows == 0) {
                    throw DatabaseException.OperationFailed("Stats record was modified by another process (optimistic locking)")
                }

                stats
            }
        } catch (e: Exception) {
            logger.error("Error updating daily stats for user: ${stats.userId}", e)
            throw DatabaseException.OperationFailed("Failed to update daily stats", e)
        }
    }

    override suspend fun atomicIncrement(
        userId: String,
        date: LocalDate,
        field: StatsField,
        amount: Int
    ): DailyUserStats? {
        return try {
            dbQuery(database) {
                val now = Clock.System.now()

                // First try to update existing record
                val column = when (field) {
                    StatsField.SCREENSHOTS_CREATED -> DailyUserStatsTable.screenshotsCreated
                    StatsField.SCREENSHOTS_COMPLETED -> DailyUserStatsTable.screenshotsCompleted
                    StatsField.SCREENSHOTS_FAILED -> DailyUserStatsTable.screenshotsFailed
                    StatsField.SCREENSHOTS_RETRIED -> DailyUserStatsTable.screenshotsRetried
                    StatsField.CREDITS_USED -> DailyUserStatsTable.creditsUsed
                    StatsField.CREDITS_ADDED -> DailyUserStatsTable.creditsAdded
                    StatsField.API_CALLS_COUNT -> DailyUserStatsTable.apiCallsCount
                    StatsField.API_KEYS_CREATED -> DailyUserStatsTable.apiKeysCreated
                    StatsField.PLAN_CHANGES -> DailyUserStatsTable.planChanges
                    StatsField.PAYMENTS_PROCESSED -> DailyUserStatsTable.paymentsProcessed
                }

                val updatedRows = DailyUserStatsTable.update({
                    (DailyUserStatsTable.userId eq userId) and (DailyUserStatsTable.date eq date)
                }) {
                    it[column] = column + amount
                    it[updatedAt] = now
                    it[version] = DailyUserStatsTable.version + 1
                }

                if (updatedRows == 0) {
                    // Record doesn't exist, create it with the increment value
                    val newStats = DailyUserStats.createEmpty(userId, date)
                    val statsWithIncrement = when (field) {
                        StatsField.SCREENSHOTS_CREATED -> newStats.incrementScreenshotsCreated(amount)
                        StatsField.SCREENSHOTS_COMPLETED -> newStats.incrementScreenshotsCompleted(amount)
                        StatsField.SCREENSHOTS_FAILED -> newStats.incrementScreenshotsFailed(amount)
                        StatsField.SCREENSHOTS_RETRIED -> newStats.incrementScreenshotsRetried(amount)
                        StatsField.CREDITS_USED -> newStats.incrementCreditsUsed(amount)
                        StatsField.CREDITS_ADDED -> newStats.incrementCreditsAdded(amount)
                        StatsField.API_CALLS_COUNT -> newStats.incrementApiCalls(amount)
                        StatsField.API_KEYS_CREATED -> newStats.copy(
                            apiKeysCreated = amount,
                            updatedAt = now,
                            version = 2
                        )
                        StatsField.PLAN_CHANGES -> newStats.copy(
                            planChanges = amount,
                            updatedAt = now,
                            version = 2
                        )
                        StatsField.PAYMENTS_PROCESSED -> newStats.copy(
                            paymentsProcessed = amount,
                            updatedAt = now,
                            version = 2
                        )
                    }
                    create(statsWithIncrement)
                } else {
                    // Fetch the updated record
                    findByUserAndDate(userId, date)
                }
            }
        } catch (e: Exception) {
            logger.error("Error incrementing daily stats field $field for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to increment daily stats", e)
        }
    }

    override suspend fun batchCreate(statsList: List<DailyUserStats>): List<DailyUserStats> {
        return try {
            dbQuery(database) {
                DailyUserStatsTable.batchInsert(statsList) { stats ->
                    this[DailyUserStatsTable.id] = UUID.randomUUID().toString()
                    this[DailyUserStatsTable.userId] = stats.userId
                    this[DailyUserStatsTable.date] = stats.date
                    this[DailyUserStatsTable.screenshotsCreated] = stats.screenshotsCreated
                    this[DailyUserStatsTable.screenshotsCompleted] = stats.screenshotsCompleted
                    this[DailyUserStatsTable.screenshotsFailed] = stats.screenshotsFailed
                    this[DailyUserStatsTable.screenshotsRetried] = stats.screenshotsRetried
                    this[DailyUserStatsTable.creditsUsed] = stats.creditsUsed
                    this[DailyUserStatsTable.apiCallsCount] = stats.apiCallsCount
                    this[DailyUserStatsTable.apiKeysUsed] = stats.apiKeysUsed
                    this[DailyUserStatsTable.creditsAdded] = stats.creditsAdded
                    this[DailyUserStatsTable.paymentsProcessed] = stats.paymentsProcessed
                    this[DailyUserStatsTable.apiKeysCreated] = stats.apiKeysCreated
                    this[DailyUserStatsTable.planChanges] = stats.planChanges
                    this[DailyUserStatsTable.createdAt] = stats.createdAt
                    this[DailyUserStatsTable.updatedAt] = stats.updatedAt
                    this[DailyUserStatsTable.version] = stats.version
                }
                statsList
            }
        } catch (e: Exception) {
            logger.error("Error batch creating daily stats", e)
            throw DatabaseException.OperationFailed("Failed to batch create daily stats", e)
        }
    }

    override suspend fun findUsersWithActivityOnDate(date: LocalDate): List<String> {
        return try {
            dbQuery(database) {
                DailyUserStatsTable.slice(DailyUserStatsTable.userId)
                    .select {
                        (DailyUserStatsTable.date eq date) and
                        (DailyUserStatsTable.screenshotsCreated greater 0)
                    }
                    .map { it[DailyUserStatsTable.userId].value }
                    .distinct()
            }
        } catch (e: Exception) {
            logger.error("Error finding users with activity on date: $date", e)
            throw DatabaseException.OperationFailed("Failed to find users with activity", e)
        }
    }

    override suspend fun deleteOlderThan(date: LocalDate): Int {
        return try {
            dbQuery(database) {
                DailyUserStatsTable.deleteWhere {
                    DailyUserStatsTable.date less date
                }
            }
        } catch (e: Exception) {
            logger.error("Error deleting daily stats older than: $date", e)
            throw DatabaseException.OperationFailed("Failed to delete old daily stats", e)
        }
    }

    override suspend fun findDatesForUser(userId: String, year: Int): List<LocalDate> {
        return try {
            dbQuery(database) {
                DailyUserStatsTable.slice(DailyUserStatsTable.date)
                    .select {
                        (DailyUserStatsTable.userId eq userId) and
                        (DailyUserStatsTable.date greaterEq LocalDate(year, 1, 1)) and
                        (DailyUserStatsTable.date less LocalDate(year + 1, 1, 1))
                    }
                    .orderBy(DailyUserStatsTable.date to SortOrder.ASC)
                    .map { it[DailyUserStatsTable.date] }
            }
        } catch (e: Exception) {
            logger.error("Error finding dates for user: $userId, year: $year", e)
            throw DatabaseException.OperationFailed("Failed to find dates for user", e)
        }
    }

    // Monthly Stats Operations

    override suspend fun findMonthlyByUserAndMonth(userId: String, month: String): MonthlyUserStats? {
        return try {
            dbQuery(database) {
                MonthlyUserStatsTable.select {
                    (MonthlyUserStatsTable.userId eq userId) and (MonthlyUserStatsTable.month eq month)
                }.map { row ->
                    row.toMonthlyUserStats()
                }.singleOrNull()
            }
        } catch (e: Exception) {
            logger.error("Error finding monthly stats for user: $userId, month: $month", e)
            throw DatabaseException.OperationFailed("Failed to find monthly stats", e)
        }
    }

    override suspend fun createMonthly(stats: MonthlyUserStats): MonthlyUserStats {
        return try {
            dbQuery(database) {
                val id = UUID.randomUUID().toString()
                MonthlyUserStatsTable.insert {
                    it[MonthlyUserStatsTable.id] = id
                    it[userId] = stats.userId
                    it[month] = stats.month
                    it[screenshotsCreated] = stats.screenshotsCreated
                    it[screenshotsCompleted] = stats.screenshotsCompleted
                    it[screenshotsFailed] = stats.screenshotsFailed
                    it[screenshotsRetried] = stats.screenshotsRetried
                    it[creditsUsed] = stats.creditsUsed
                    it[apiCallsCount] = stats.apiCallsCount
                    it[creditsAdded] = stats.creditsAdded
                    it[peakDailyScreenshots] = stats.peakDailyScreenshots
                    it[activeDays] = stats.activeDays
                    it[createdAt] = stats.createdAt
                    it[updatedAt] = stats.updatedAt
                    it[version] = stats.version
                }
                stats
            }
        } catch (e: Exception) {
            logger.error("Error creating monthly stats for user: ${stats.userId}", e)
            throw DatabaseException.OperationFailed("Failed to create monthly stats", e)
        }
    }

    override suspend fun updateMonthly(stats: MonthlyUserStats): MonthlyUserStats {
        return try {
            dbQuery(database) {
                val updatedRows = MonthlyUserStatsTable.update({
                    (MonthlyUserStatsTable.userId eq stats.userId) and
                    (MonthlyUserStatsTable.month eq stats.month) and
                    (MonthlyUserStatsTable.version eq stats.version - 1)
                }) {
                    it[screenshotsCreated] = stats.screenshotsCreated
                    it[screenshotsCompleted] = stats.screenshotsCompleted
                    it[screenshotsFailed] = stats.screenshotsFailed
                    it[screenshotsRetried] = stats.screenshotsRetried
                    it[creditsUsed] = stats.creditsUsed
                    it[apiCallsCount] = stats.apiCallsCount
                    it[creditsAdded] = stats.creditsAdded
                    it[peakDailyScreenshots] = stats.peakDailyScreenshots
                    it[activeDays] = stats.activeDays
                    it[updatedAt] = stats.updatedAt
                    it[version] = stats.version
                }

                if (updatedRows == 0) {
                    throw DatabaseException.OperationFailed("Monthly stats record was modified by another process (optimistic locking)")
                }

                stats
            }
        } catch (e: Exception) {
            logger.error("Error updating monthly stats for user: ${stats.userId}", e)
            throw DatabaseException.OperationFailed("Failed to update monthly stats", e)
        }
    }

    override suspend fun findMonthlyByUserAndYear(userId: String, year: Int): List<MonthlyUserStats> {
        return try {
            dbQuery(database) {
                MonthlyUserStatsTable.select {
                    (MonthlyUserStatsTable.userId eq userId) and
                    (MonthlyUserStatsTable.month like "$year-%")
                }.orderBy(MonthlyUserStatsTable.month to SortOrder.ASC)
                .map { row ->
                    row.toMonthlyUserStats()
                }
            }
        } catch (e: Exception) {
            logger.error("Error finding monthly stats for user: $userId, year: $year", e)
            throw DatabaseException.OperationFailed("Failed to find monthly stats for year", e)
        }
    }

    override suspend fun deleteMonthlyOlderThan(month: String): Int {
        return try {
            dbQuery(database) {
                MonthlyUserStatsTable.deleteWhere {
                    MonthlyUserStatsTable.month less month
                }
            }
        } catch (e: Exception) {
            logger.error("Error deleting monthly stats older than: $month", e)
            throw DatabaseException.OperationFailed("Failed to delete old monthly stats", e)
        }
    }

    // Yearly Stats Operations

    override suspend fun findYearlyByUserAndYear(userId: String, year: Int): YearlyUserStats? {
        return try {
            dbQuery(database) {
                YearlyUserStatsTable.select {
                    (YearlyUserStatsTable.userId eq userId) and (YearlyUserStatsTable.year eq year)
                }.map { row ->
                    row.toYearlyUserStats()
                }.singleOrNull()
            }
        } catch (e: Exception) {
            logger.error("Error finding yearly stats for user: $userId, year: $year", e)
            throw DatabaseException.OperationFailed("Failed to find yearly stats", e)
        }
    }

    override suspend fun createYearly(stats: YearlyUserStats): YearlyUserStats {
        return try {
            dbQuery(database) {
                val id = UUID.randomUUID().toString()
                YearlyUserStatsTable.insert {
                    it[YearlyUserStatsTable.id] = id
                    it[userId] = stats.userId
                    it[year] = stats.year
                    it[screenshotsCreated] = stats.screenshotsCreated
                    it[screenshotsCompleted] = stats.screenshotsCompleted
                    it[screenshotsFailed] = stats.screenshotsFailed
                    it[screenshotsRetried] = stats.screenshotsRetried
                    it[creditsUsed] = stats.creditsUsed
                    it[apiCallsCount] = stats.apiCallsCount
                    it[creditsAdded] = stats.creditsAdded
                    it[peakMonthlyScreenshots] = stats.peakMonthlyScreenshots
                    it[activeMonths] = stats.activeMonths
                    it[createdAt] = stats.createdAt
                    it[updatedAt] = stats.updatedAt
                    it[version] = stats.version
                }
                stats
            }
        } catch (e: Exception) {
            logger.error("Error creating yearly stats for user: ${stats.userId}", e)
            throw DatabaseException.OperationFailed("Failed to create yearly stats", e)
        }
    }

    override suspend fun updateYearly(stats: YearlyUserStats): YearlyUserStats {
        return try {
            dbQuery(database) {
                val updatedRows = YearlyUserStatsTable.update({
                    (YearlyUserStatsTable.userId eq stats.userId) and
                    (YearlyUserStatsTable.year eq stats.year) and
                    (YearlyUserStatsTable.version eq stats.version - 1)
                }) {
                    it[screenshotsCreated] = stats.screenshotsCreated
                    it[screenshotsCompleted] = stats.screenshotsCompleted
                    it[screenshotsFailed] = stats.screenshotsFailed
                    it[screenshotsRetried] = stats.screenshotsRetried
                    it[creditsUsed] = stats.creditsUsed
                    it[apiCallsCount] = stats.apiCallsCount
                    it[creditsAdded] = stats.creditsAdded
                    it[peakMonthlyScreenshots] = stats.peakMonthlyScreenshots
                    it[activeMonths] = stats.activeMonths
                    it[updatedAt] = stats.updatedAt
                    it[version] = stats.version
                }

                if (updatedRows == 0) {
                    throw DatabaseException.OperationFailed("Yearly stats record was modified by another process (optimistic locking)")
                }

                stats
            }
        } catch (e: Exception) {
            logger.error("Error updating yearly stats for user: ${stats.userId}", e)
            throw DatabaseException.OperationFailed("Failed to update yearly stats", e)
        }
    }

    override suspend fun findYearlyByUser(userId: String): List<YearlyUserStats> {
        return try {
            dbQuery(database) {
                YearlyUserStatsTable.select {
                    YearlyUserStatsTable.userId eq userId
                }.orderBy(YearlyUserStatsTable.year to SortOrder.DESC)
                .map { row ->
                    row.toYearlyUserStats()
                }
            }
        } catch (e: Exception) {
            logger.error("Error finding yearly stats for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find yearly stats", e)
        }
    }

    override suspend fun deleteYearlyOlderThan(year: Int): Int {
        return try {
            dbQuery(database) {
                YearlyUserStatsTable.deleteWhere {
                    YearlyUserStatsTable.year less year
                }
            }
        } catch (e: Exception) {
            logger.error("Error deleting yearly stats older than: $year", e)
            throw DatabaseException.OperationFailed("Failed to delete old yearly stats", e)
        }
    }

    // Aggregation support methods

    override suspend fun aggregateDailyToMonthly(userId: String, month: String): MonthlyUserStats? {
        // Implementation to aggregate daily stats into monthly
        return null // Placeholder
    }

    override suspend fun aggregateMonthlyToYearly(userId: String, year: Int): YearlyUserStats? {
        // Implementation to aggregate monthly stats into yearly
        return null // Placeholder
    }

    // Statistics and health check methods

    override suspend fun getTableSizes(): Map<String, Long> {
        return try {
            dbQuery(database) {
                mapOf(
                    "daily_user_stats" to DailyUserStatsTable.selectAll().count(),
                    "monthly_user_stats" to MonthlyUserStatsTable.selectAll().count(),
                    "yearly_user_stats" to YearlyUserStatsTable.selectAll().count()
                )
            }
        } catch (e: Exception) {
            logger.error("Error getting table sizes", e)
            emptyMap()
        }
    }

    override suspend fun getOldestRecord(): LocalDate? {
        return try {
            dbQuery(database) {
                DailyUserStatsTable.slice(DailyUserStatsTable.date)
                    .selectAll()
                    .orderBy(DailyUserStatsTable.date to SortOrder.ASC)
                    .limit(1)
                    .map { it[DailyUserStatsTable.date] }
                    .singleOrNull()
            }
        } catch (e: Exception) {
            logger.error("Error getting oldest record", e)
            null
        }
    }

    override suspend fun getUsersWithStatsInPeriod(startDate: LocalDate, endDate: LocalDate): Set<String> {
        return try {
            dbQuery(database) {
                DailyUserStatsTable.slice(DailyUserStatsTable.userId)
                    .select {
                        (DailyUserStatsTable.date greaterEq startDate) and
                        (DailyUserStatsTable.date lessEq endDate)
                    }
                    .map { it[DailyUserStatsTable.userId].value }
                    .toSet()
            }
        } catch (e: Exception) {
            logger.error("Error getting users with stats in period", e)
            emptySet()
        }
    }

    // Helper extension functions to convert ResultRow to entities
    private fun ResultRow.toDailyUserStats(): DailyUserStats {
        return DailyUserStats(
            userId = this[DailyUserStatsTable.userId].value,
            date = this[DailyUserStatsTable.date],
            screenshotsCreated = this[DailyUserStatsTable.screenshotsCreated],
            screenshotsCompleted = this[DailyUserStatsTable.screenshotsCompleted],
            screenshotsFailed = this[DailyUserStatsTable.screenshotsFailed],
            screenshotsRetried = this[DailyUserStatsTable.screenshotsRetried],
            creditsUsed = this[DailyUserStatsTable.creditsUsed],
            apiCallsCount = this[DailyUserStatsTable.apiCallsCount],
            apiKeysUsed = this[DailyUserStatsTable.apiKeysUsed],
            creditsAdded = this[DailyUserStatsTable.creditsAdded],
            paymentsProcessed = this[DailyUserStatsTable.paymentsProcessed],
            apiKeysCreated = this[DailyUserStatsTable.apiKeysCreated],
            planChanges = this[DailyUserStatsTable.planChanges],
            createdAt = this[DailyUserStatsTable.createdAt],
            updatedAt = this[DailyUserStatsTable.updatedAt],
            version = this[DailyUserStatsTable.version]
        )
    }

    private fun ResultRow.toMonthlyUserStats(): MonthlyUserStats {
        return MonthlyUserStats(
            userId = this[MonthlyUserStatsTable.userId].value,
            month = this[MonthlyUserStatsTable.month],
            screenshotsCreated = this[MonthlyUserStatsTable.screenshotsCreated],
            screenshotsCompleted = this[MonthlyUserStatsTable.screenshotsCompleted],
            screenshotsFailed = this[MonthlyUserStatsTable.screenshotsFailed],
            screenshotsRetried = this[MonthlyUserStatsTable.screenshotsRetried],
            creditsUsed = this[MonthlyUserStatsTable.creditsUsed],
            apiCallsCount = this[MonthlyUserStatsTable.apiCallsCount],
            creditsAdded = this[MonthlyUserStatsTable.creditsAdded],
            peakDailyScreenshots = this[MonthlyUserStatsTable.peakDailyScreenshots],
            activeDays = this[MonthlyUserStatsTable.activeDays],
            createdAt = this[MonthlyUserStatsTable.createdAt],
            updatedAt = this[MonthlyUserStatsTable.updatedAt],
            version = this[MonthlyUserStatsTable.version]
        )
    }

    private fun ResultRow.toYearlyUserStats(): YearlyUserStats {
        return YearlyUserStats(
            userId = this[YearlyUserStatsTable.userId].value,
            year = this[YearlyUserStatsTable.year],
            screenshotsCreated = this[YearlyUserStatsTable.screenshotsCreated],
            screenshotsCompleted = this[YearlyUserStatsTable.screenshotsCompleted],
            screenshotsFailed = this[YearlyUserStatsTable.screenshotsFailed],
            screenshotsRetried = this[YearlyUserStatsTable.screenshotsRetried],
            creditsUsed = this[YearlyUserStatsTable.creditsUsed],
            apiCallsCount = this[YearlyUserStatsTable.apiCallsCount],
            creditsAdded = this[YearlyUserStatsTable.creditsAdded],
            peakMonthlyScreenshots = this[YearlyUserStatsTable.peakMonthlyScreenshots],
            activeMonths = this[YearlyUserStatsTable.activeMonths],
            createdAt = this[YearlyUserStatsTable.createdAt],
            updatedAt = this[YearlyUserStatsTable.updatedAt],
            version = this[YearlyUserStatsTable.version]
        )
    }
}
