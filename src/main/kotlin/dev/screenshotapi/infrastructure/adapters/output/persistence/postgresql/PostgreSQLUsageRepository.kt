package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.UserUsage
import dev.screenshotapi.core.domain.entities.UsageTimelineEntry
import dev.screenshotapi.core.domain.entities.TimeGranularity
import dev.screenshotapi.core.domain.repositories.UsageRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Plans
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.UsageTracking
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.UsageLogs
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory

class PostgreSQLUsageRepository(private val database: Database) : UsageRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun findByUserAndMonth(userId: String, month: String): UserUsage? {
        return try {
            dbQuery(database) {
                UsageTracking.select {
                    (UsageTracking.userId eq userId) and (UsageTracking.month eq month)
                }.map { row ->
                    UserUsage(
                        userId = row[UsageTracking.userId].value,
                        month = row[UsageTracking.month],
                        totalRequests = row[UsageTracking.totalRequests],
                        planCreditsLimit = row[UsageTracking.planCreditsLimit],
                        remainingCredits = row[UsageTracking.remainingCredits],
                        lastRequestAt = row[UsageTracking.lastRequestAt],
                        createdAt = row[UsageTracking.createdAt],
                        updatedAt = row[UsageTracking.updatedAt]
                    )
                }.singleOrNull()
            }
        } catch (e: Exception) {
            logger.error("Error finding usage for user: $userId and month: $month", e)
            throw DatabaseException.OperationFailed("Failed to find usage", e)
        }
    }

    override suspend fun incrementUsage(userId: String, month: String, amount: Int): UserUsage {
        return try {
            dbQuery(database) {
                val existing = UsageTracking.select {
                    (UsageTracking.userId eq userId) and (UsageTracking.month eq month)
                }.singleOrNull()

                val now = Clock.System.now()

                if (existing != null) {
                    // Update existing record
                    val newTotalRequests = existing[UsageTracking.totalRequests] + amount
                    val newRemainingCredits = (existing[UsageTracking.remainingCredits] - amount).coerceAtLeast(0)

                    UsageTracking.update({
                        (UsageTracking.userId eq userId) and (UsageTracking.month eq month)
                    }) {
                        it[totalRequests] = newTotalRequests
                        it[remainingCredits] = newRemainingCredits
                        it[lastRequestAt] = now
                        it[updatedAt] = now
                    }

                    UserUsage(
                        userId = userId,
                        month = month,
                        totalRequests = newTotalRequests,
                        planCreditsLimit = existing[UsageTracking.planCreditsLimit],
                        remainingCredits = newRemainingCredits,
                        lastRequestAt = now,
                        createdAt = existing[UsageTracking.createdAt],
                        updatedAt = now
                    )
                } else {
                    // Create new record - need to get plan credits
                    val planCredits = getDefaultPlanCredits()
                    val newRemainingCredits = (planCredits - amount).coerceAtLeast(0)

                    UsageTracking.insert {
                        it[UsageTracking.userId] = userId
                        it[UsageTracking.month] = month
                        it[totalRequests] = amount
                        it[planCreditsLimit] = planCredits
                        it[remainingCredits] = newRemainingCredits
                        it[lastRequestAt] = now
                        it[createdAt] = now
                        it[updatedAt] = now
                    }

                    UserUsage(
                        userId = userId,
                        month = month,
                        totalRequests = amount,
                        planCreditsLimit = planCredits,
                        remainingCredits = newRemainingCredits,
                        lastRequestAt = now,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error incrementing usage for user: $userId and month: $month", e)
            throw DatabaseException.OperationFailed("Failed to increment usage", e)
        }
    }

    override suspend fun createUsage(usage: UserUsage): UserUsage {
        return try {
            dbQuery(database) {
                UsageTracking.insert {
                    it[userId] = usage.userId
                    it[month] = usage.month
                    it[totalRequests] = usage.totalRequests
                    it[planCreditsLimit] = usage.planCreditsLimit
                    it[remainingCredits] = usage.remainingCredits
                    it[lastRequestAt] = usage.lastRequestAt
                    it[createdAt] = usage.createdAt
                    it[updatedAt] = usage.updatedAt
                }
                usage
            }
        } catch (e: Exception) {
            logger.error("Error creating usage for user: ${usage.userId}", e)
            throw DatabaseException.OperationFailed("Failed to create usage", e)
        }
    }

    override suspend fun updateUsage(usage: UserUsage): UserUsage {
        return try {
            dbQuery(database) {
                val updatedRows = UsageTracking.update({
                    (UsageTracking.userId eq usage.userId) and (UsageTracking.month eq usage.month)
                }) {
                    it[totalRequests] = usage.totalRequests
                    it[planCreditsLimit] = usage.planCreditsLimit
                    it[remainingCredits] = usage.remainingCredits
                    it[lastRequestAt] = usage.lastRequestAt
                    it[updatedAt] = usage.updatedAt
                }

                if (updatedRows == 0) {
                    throw DatabaseException.OperationFailed("Usage not found: ${usage.userId}/${usage.month}")
                }

                usage
            }
        } catch (e: Exception) {
            logger.error("Error updating usage for user: ${usage.userId}", e)
            throw DatabaseException.OperationFailed("Failed to update usage", e)
        }
    }

    override suspend fun getUserMonthlyStats(userId: String, year: Int): List<UserUsage> {
        return try {
            dbQuery(database) {
                UsageTracking.select {
                    (UsageTracking.userId eq userId) and (UsageTracking.month like "$year-%")
                }.orderBy(UsageTracking.month to SortOrder.DESC)
                    .map { row ->
                        UserUsage(
                            userId = row[UsageTracking.userId].value,
                            month = row[UsageTracking.month],
                            totalRequests = row[UsageTracking.totalRequests],
                            planCreditsLimit = row[UsageTracking.planCreditsLimit],
                            remainingCredits = row[UsageTracking.remainingCredits],
                            lastRequestAt = row[UsageTracking.lastRequestAt],
                            createdAt = row[UsageTracking.createdAt],
                            updatedAt = row[UsageTracking.updatedAt]
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Error getting monthly stats for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to get monthly stats", e)
        }
    }

    override suspend fun getUsageTimeline(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        granularity: TimeGranularity
    ): List<UsageTimelineEntry> {
        return try {
            dbQuery(database) {
                // Convert LocalDate to Instant for database queries
                val startInstant = startDate.atStartOfDayIn(TimeZone.UTC)
                val endInstant = endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC)

                // Query usage logs for the specified period and user
                val logs = UsageLogs.select {
                    (UsageLogs.userId eq userId) and 
                    (UsageLogs.timestamp greaterEq startInstant) and 
                    (UsageLogs.timestamp less endInstant)
                }.orderBy(UsageLogs.timestamp to SortOrder.ASC).toList()

                // Group logs by date and aggregate data
                val logsByDate = logs.groupBy { row ->
                    val timestamp = row[UsageLogs.timestamp]
                    timestamp.toLocalDateTime(TimeZone.UTC).date
                }

                // Generate date range based on granularity
                val dateRange = generateDateRange(startDate, endDate, granularity)

                // Create timeline entries for each date/period
                dateRange.map { date ->
                    val logsForDate = when (granularity) {
                        TimeGranularity.DAILY -> logsByDate[date] ?: emptyList()
                        TimeGranularity.WEEKLY -> {
                            // Get logs for the whole week starting from this date
                            val weekStart = date
                            val weekEnd = date.plus(DatePeriod(days = 6))
                            logsByDate.filterKeys { it >= weekStart && it <= weekEnd }.values.flatten()
                        }
                        TimeGranularity.MONTHLY -> {
                            // Get logs for the whole month
                            val monthStart = LocalDate(date.year, date.month, 1)
                            val monthEnd = monthStart.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
                            logsByDate.filterKeys { it >= monthStart && it <= monthEnd }.values.flatten()
                        }
                    }

                    // Calculate metrics from logs - now with proper tracking
                    val screenshotsCreated = logsForDate.count { it[UsageLogs.action] == "SCREENSHOT_CREATED" }
                    val successfulScreenshots = logsForDate.count { it[UsageLogs.action] == "SCREENSHOT_COMPLETED" }
                    val failedScreenshots = logsForDate.count { it[UsageLogs.action] == "SCREENSHOT_FAILED" }
                    val apiKeyUsedCount = logsForDate.count { it[UsageLogs.action] == "API_KEY_USED" }
                    // Total screenshots = screenshots that were actually started (created)
                    val screenshots = screenshotsCreated
                    val creditsUsed = logsForDate.sumOf { it[UsageLogs.creditsUsed] }
                    val apiCalls = apiKeyUsedCount // Count API calls specifically

                    UsageTimelineEntry(
                        date = date,
                        screenshots = screenshots,
                        creditsUsed = creditsUsed,
                        apiCalls = apiCalls,
                        successfulScreenshots = successfulScreenshots,
                        failedScreenshots = failedScreenshots
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting usage timeline for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to get usage timeline", e)
        }
    }

    /**
     * Generate date range based on granularity
     */
    private fun generateDateRange(startDate: LocalDate, endDate: LocalDate, granularity: TimeGranularity): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var currentDate = startDate

        when (granularity) {
            TimeGranularity.DAILY -> {
                while (currentDate <= endDate) {
                    dates.add(currentDate)
                    currentDate = currentDate.plus(DatePeriod(days = 1))
                }
            }
            TimeGranularity.WEEKLY -> {
                // Start from the beginning of the week (Monday)
                val weekStart = startDate.minus(DatePeriod(days = startDate.dayOfWeek.ordinal))
                currentDate = weekStart
                while (currentDate <= endDate) {
                    dates.add(currentDate)
                    currentDate = currentDate.plus(DatePeriod(days = 7))
                }
            }
            TimeGranularity.MONTHLY -> {
                // Start from the beginning of the month
                currentDate = LocalDate(startDate.year, startDate.month, 1)
                while (currentDate <= endDate) {
                    dates.add(currentDate)
                    currentDate = currentDate.plus(DatePeriod(months = 1))
                }
            }
        }

        return dates
    }

    /**
     * Helper function to get default plan credits
     */
    private suspend fun getDefaultPlanCredits(): Int {
        return try {
            dbQuery(database) {
                val freePlanCredits = Plans.select {
                    (Plans.id eq "plan_free") and (Plans.isActive eq true)
                }
                    .map { it[Plans.creditsPerMonth] }
                    .singleOrNull()
                freePlanCredits?.takeIf { it > 0 } ?: 300
            }
        } catch (e: Exception) {
            logger.warn("Error getting default plan credits, using fallback value", e)
            300 // Free plan default
        }
    }
}
