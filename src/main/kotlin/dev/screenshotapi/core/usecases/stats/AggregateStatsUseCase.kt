package dev.screenshotapi.core.usecases.stats

import dev.screenshotapi.core.domain.entities.DailyUserStats
import dev.screenshotapi.core.domain.entities.MonthlyUserStats
import dev.screenshotapi.core.domain.entities.YearlyUserStats
import dev.screenshotapi.core.domain.repositories.DailyStatsRepository
import kotlinx.datetime.*
import org.slf4j.LoggerFactory

/**
 * Use case for aggregating statistics from daily → monthly → yearly
 * 
 * This use case handles the batch processing of aggregating detailed
 * daily statistics into summary monthly and yearly statistics for
 * efficient querying and long-term data retention.
 */
class AggregateStatsUseCase(
    private val dailyStatsRepository: DailyStatsRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Daily to Monthly Aggregation
    
    data class DailyToMonthlyRequest(
        val targetDate: LocalDate,
        val forceRecalculation: Boolean = false
    )

    data class DailyToMonthlyResponse(
        val success: Boolean,
        val usersProcessed: Int = 0,
        val recordsAggregated: Int = 0,
        val monthlyRecordsCreated: Int = 0,
        val monthlyRecordsUpdated: Int = 0,
        val error: String? = null,
        val processingTimeMs: Long = 0
    )

    suspend fun aggregateDailyToMonthly(request: DailyToMonthlyRequest): DailyToMonthlyResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            logger.info("Starting daily to monthly aggregation for date: ${request.targetDate}")
            
            val month = "${request.targetDate.year}-${request.targetDate.monthNumber.toString().padStart(2, '0')}"
            
            // Get all users who had activity on this date
            val usersWithActivity = dailyStatsRepository.findUsersWithActivityOnDate(request.targetDate)
            
            logger.info("Found ${usersWithActivity.size} users with activity on ${request.targetDate}")
            
            var recordsAggregated = 0
            var monthlyRecordsCreated = 0
            var monthlyRecordsUpdated = 0
            
            for (userId in usersWithActivity) {
                try {
                    // Get daily stats for this user and date
                    val dailyStats = dailyStatsRepository.findByUserAndDate(userId, request.targetDate)
                    
                    if (dailyStats != null && dailyStats.hasActivity) {
                        // Check if monthly record already exists
                        val existingMonthly = dailyStatsRepository.findMonthlyByUserAndMonth(userId, month)
                        
                        if (existingMonthly == null) {
                            // Create new monthly record
                            val monthlyStats = createMonthlyFromDaily(userId, month, listOf(dailyStats))
                            dailyStatsRepository.createMonthly(monthlyStats)
                            monthlyRecordsCreated++
                            logger.debug("Created monthly stats for user $userId, month $month")
                            
                        } else if (request.forceRecalculation) {
                            // Recalculate entire month
                            val allDailyStatsForMonth = getDailyStatsForMonth(userId, request.targetDate)
                            val updatedMonthly = createMonthlyFromDaily(userId, month, allDailyStatsForMonth)
                            dailyStatsRepository.updateMonthly(updatedMonthly)
                            monthlyRecordsUpdated++
                            logger.debug("Updated monthly stats for user $userId, month $month")
                            
                        } else {
                            // Incrementally update existing monthly record
                            val updatedMonthly = incrementMonthlyStats(existingMonthly, dailyStats)
                            dailyStatsRepository.updateMonthly(updatedMonthly)
                            monthlyRecordsUpdated++
                            logger.debug("Incremented monthly stats for user $userId, month $month")
                        }
                        
                        recordsAggregated++
                    }
                    
                } catch (e: Exception) {
                    logger.error("Failed to aggregate daily stats for user $userId on ${request.targetDate}", e)
                    // Continue processing other users
                }
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            logger.info("Completed daily to monthly aggregation: $recordsAggregated records, $monthlyRecordsCreated created, $monthlyRecordsUpdated updated in ${processingTime}ms")
            
            DailyToMonthlyResponse(
                success = true,
                usersProcessed = usersWithActivity.size,
                recordsAggregated = recordsAggregated,
                monthlyRecordsCreated = monthlyRecordsCreated,
                monthlyRecordsUpdated = monthlyRecordsUpdated,
                processingTimeMs = processingTime
            )
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Daily to monthly aggregation failed", e)
            
            DailyToMonthlyResponse(
                success = false,
                error = "Aggregation failed: ${e.message}",
                processingTimeMs = processingTime
            )
        }
    }

    // Convenience method for scheduler (expects targetYear)
    data class MonthlyToYearlyRequest(
        val targetYear: Int,
        val forceRecalculation: Boolean = false
    )

    suspend fun aggregateMonthlyToYearly(request: MonthlyToYearlyRequest): MonthlyToYearlyResponse {
        return aggregateMonthlyToYearlyByMonth(MonthlyToYearlyByMonthRequest(
            targetMonth = "${request.targetYear}-12", // Aggregate full year
            forceRecalculation = request.forceRecalculation
        ))
    }
    
    // Monthly to Yearly Aggregation (by month)
    
    data class MonthlyToYearlyByMonthRequest(
        val targetMonth: String, // Format: "2025-01"
        val forceRecalculation: Boolean = false
    )

    data class MonthlyToYearlyResponse(
        val success: Boolean,
        val usersProcessed: Int = 0,
        val recordsAggregated: Int = 0,
        val yearlyRecordsCreated: Int = 0,
        val yearlyRecordsUpdated: Int = 0,
        val error: String? = null,
        val processingTimeMs: Long = 0
    )

    suspend fun aggregateMonthlyToYearlyByMonth(request: MonthlyToYearlyByMonthRequest): MonthlyToYearlyResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            logger.info("Starting monthly to yearly aggregation for month: ${request.targetMonth}")
            
            val year = request.targetMonth.split("-")[0].toInt()
            
            // Get all users who had monthly stats for this month
            val usersWithMonthlyStats = getUsersWithMonthlyStats(request.targetMonth)
            
            logger.info("Found ${usersWithMonthlyStats.size} users with monthly stats for ${request.targetMonth}")
            
            var recordsAggregated = 0
            var yearlyRecordsCreated = 0
            var yearlyRecordsUpdated = 0
            
            for (userId in usersWithMonthlyStats) {
                try {
                    // Get monthly stats for this user and month
                    val monthlyStats = dailyStatsRepository.findMonthlyByUserAndMonth(userId, request.targetMonth)
                    
                    if (monthlyStats != null) {
                        // Check if yearly record already exists
                        val existingYearly = dailyStatsRepository.findYearlyByUserAndYear(userId, year)
                        
                        if (existingYearly == null) {
                            // Create new yearly record
                            val yearlyStats = createYearlyFromMonthly(userId, year, listOf(monthlyStats))
                            dailyStatsRepository.createYearly(yearlyStats)
                            yearlyRecordsCreated++
                            logger.debug("Created yearly stats for user $userId, year $year")
                            
                        } else if (request.forceRecalculation) {
                            // Recalculate entire year
                            val allMonthlyStatsForYear = dailyStatsRepository.findMonthlyByUserAndYear(userId, year)
                            val updatedYearly = createYearlyFromMonthly(userId, year, allMonthlyStatsForYear)
                            dailyStatsRepository.updateYearly(updatedYearly)
                            yearlyRecordsUpdated++
                            logger.debug("Updated yearly stats for user $userId, year $year")
                            
                        } else {
                            // Incrementally update existing yearly record
                            val updatedYearly = incrementYearlyStats(existingYearly, monthlyStats)
                            dailyStatsRepository.updateYearly(updatedYearly)
                            yearlyRecordsUpdated++
                            logger.debug("Incremented yearly stats for user $userId, year $year")
                        }
                        
                        recordsAggregated++
                    }
                    
                } catch (e: Exception) {
                    logger.error("Failed to aggregate monthly stats for user $userId for ${request.targetMonth}", e)
                    // Continue processing other users
                }
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            logger.info("Completed monthly to yearly aggregation: $recordsAggregated records, $yearlyRecordsCreated created, $yearlyRecordsUpdated updated in ${processingTime}ms")
            
            MonthlyToYearlyResponse(
                success = true,
                usersProcessed = usersWithMonthlyStats.size,
                recordsAggregated = recordsAggregated,
                yearlyRecordsCreated = yearlyRecordsCreated,
                yearlyRecordsUpdated = yearlyRecordsUpdated,
                processingTimeMs = processingTime
            )
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Monthly to yearly aggregation failed", e)
            
            MonthlyToYearlyResponse(
                success = false,
                error = "Aggregation failed: ${e.message}",
                processingTimeMs = processingTime
            )
        }
    }

    // Data Cleanup
    
    data class CleanupRequest(
        val retainDailyDays: Int = 90,      // Keep 90 days of daily data
        val retainMonthlyMonths: Int = 24,  // Keep 24 months of monthly data
        val retainYearlyYears: Int = 5,     // Keep 5 years of yearly data
        val dryRun: Boolean = false
    )

    data class CleanupResponse(
        val success: Boolean,
        val recordsDeleted: Int = 0,
        val dailyRecordsDeleted: Int = 0,
        val monthlyRecordsDeleted: Int = 0,
        val yearlyRecordsDeleted: Int = 0,
        val error: String? = null,
        val processingTimeMs: Long = 0
    )

    suspend fun cleanupOldData(request: CleanupRequest): CleanupResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            logger.info("Starting data cleanup (dryRun: ${request.dryRun})")
            
            val today = Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
            
            // Calculate cutoff dates
            val dailyCutoff = today.minus(DatePeriod(days = request.retainDailyDays))
            val monthlyCutoff = "${today.year - (request.retainMonthlyMonths / 12)}-${(today.monthNumber - (request.retainMonthlyMonths % 12)).toString().padStart(2, '0')}"
            val yearlyCutoff = today.year - request.retainYearlyYears
            
            var dailyDeleted = 0
            var monthlyDeleted = 0
            var yearlyDeleted = 0
            
            if (!request.dryRun) {
                // Delete old daily records
                dailyDeleted = dailyStatsRepository.deleteOlderThan(dailyCutoff)
                logger.info("Deleted $dailyDeleted daily records older than $dailyCutoff")
                
                // Delete old monthly records
                monthlyDeleted = dailyStatsRepository.deleteMonthlyOlderThan(monthlyCutoff)
                logger.info("Deleted $monthlyDeleted monthly records older than $monthlyCutoff")
                
                // Delete old yearly records
                yearlyDeleted = dailyStatsRepository.deleteYearlyOlderThan(yearlyCutoff)
                logger.info("Deleted $yearlyDeleted yearly records older than $yearlyCutoff")
            } else {
                logger.info("DRY RUN: Would delete records older than - Daily: $dailyCutoff, Monthly: $monthlyCutoff, Yearly: $yearlyCutoff")
            }
            
            val totalDeleted = dailyDeleted + monthlyDeleted + yearlyDeleted
            val processingTime = System.currentTimeMillis() - startTime
            
            logger.info("Completed data cleanup: $totalDeleted total records deleted in ${processingTime}ms")
            
            CleanupResponse(
                success = true,
                recordsDeleted = totalDeleted,
                dailyRecordsDeleted = dailyDeleted,
                monthlyRecordsDeleted = monthlyDeleted,
                yearlyRecordsDeleted = yearlyDeleted,
                processingTimeMs = processingTime
            )
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Data cleanup failed", e)
            
            CleanupResponse(
                success = false,
                error = "Cleanup failed: ${e.message}",
                processingTimeMs = processingTime
            )
        }
    }

    // Helper Methods
    
    private suspend fun getDailyStatsForMonth(userId: String, sampleDate: LocalDate): List<DailyUserStats> {
        val startOfMonth = LocalDate(sampleDate.year, sampleDate.month, 1)
        val endOfMonth = startOfMonth.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        
        return dailyStatsRepository.findByUserAndDateRange(userId, startOfMonth, endOfMonth)
    }
    
    private fun createMonthlyFromDaily(userId: String, month: String, dailyStatsList: List<DailyUserStats>): MonthlyUserStats {
        val now = Clock.System.now()
        
        return MonthlyUserStats(
            userId = userId,
            month = month,
            screenshotsCreated = dailyStatsList.sumOf { it.screenshotsCreated },
            screenshotsCompleted = dailyStatsList.sumOf { it.screenshotsCompleted },
            screenshotsFailed = dailyStatsList.sumOf { it.screenshotsFailed },
            screenshotsRetried = dailyStatsList.sumOf { it.screenshotsRetried },
            creditsUsed = dailyStatsList.sumOf { it.creditsUsed },
            apiCallsCount = dailyStatsList.sumOf { it.apiCallsCount },
            creditsAdded = dailyStatsList.sumOf { it.creditsAdded },
            peakDailyScreenshots = dailyStatsList.maxOfOrNull { it.screenshotsCreated } ?: 0,
            activeDays = dailyStatsList.count { it.hasActivity },
            createdAt = now,
            updatedAt = now
        )
    }
    
    private fun incrementMonthlyStats(existing: MonthlyUserStats, dailyStats: DailyUserStats): MonthlyUserStats {
        val now = Clock.System.now()
        
        return existing.copy(
            screenshotsCreated = existing.screenshotsCreated + dailyStats.screenshotsCreated,
            screenshotsCompleted = existing.screenshotsCompleted + dailyStats.screenshotsCompleted,
            screenshotsFailed = existing.screenshotsFailed + dailyStats.screenshotsFailed,
            screenshotsRetried = existing.screenshotsRetried + dailyStats.screenshotsRetried,
            creditsUsed = existing.creditsUsed + dailyStats.creditsUsed,
            apiCallsCount = existing.apiCallsCount + dailyStats.apiCallsCount,
            creditsAdded = existing.creditsAdded + dailyStats.creditsAdded,
            peakDailyScreenshots = maxOf(existing.peakDailyScreenshots, dailyStats.screenshotsCreated),
            activeDays = if (dailyStats.hasActivity) existing.activeDays + 1 else existing.activeDays,
            updatedAt = now,
            version = existing.version + 1
        )
    }
    
    private fun createYearlyFromMonthly(userId: String, year: Int, monthlyStatsList: List<MonthlyUserStats>): YearlyUserStats {
        val now = Clock.System.now()
        
        return YearlyUserStats(
            userId = userId,
            year = year,
            screenshotsCreated = monthlyStatsList.sumOf { it.screenshotsCreated },
            screenshotsCompleted = monthlyStatsList.sumOf { it.screenshotsCompleted },
            screenshotsFailed = monthlyStatsList.sumOf { it.screenshotsFailed },
            screenshotsRetried = monthlyStatsList.sumOf { it.screenshotsRetried },
            creditsUsed = monthlyStatsList.sumOf { it.creditsUsed },
            apiCallsCount = monthlyStatsList.sumOf { it.apiCallsCount },
            creditsAdded = monthlyStatsList.sumOf { it.creditsAdded },
            peakMonthlyScreenshots = monthlyStatsList.maxOfOrNull { it.screenshotsCreated } ?: 0,
            activeMonths = monthlyStatsList.count { it.screenshotsCreated > 0 || it.creditsUsed > 0 },
            createdAt = now,
            updatedAt = now
        )
    }
    
    private fun incrementYearlyStats(existing: YearlyUserStats, monthlyStats: MonthlyUserStats): YearlyUserStats {
        val now = Clock.System.now()
        
        return existing.copy(
            screenshotsCreated = existing.screenshotsCreated + monthlyStats.screenshotsCreated,
            screenshotsCompleted = existing.screenshotsCompleted + monthlyStats.screenshotsCompleted,
            screenshotsFailed = existing.screenshotsFailed + monthlyStats.screenshotsFailed,
            screenshotsRetried = existing.screenshotsRetried + monthlyStats.screenshotsRetried,
            creditsUsed = existing.creditsUsed + monthlyStats.creditsUsed,
            apiCallsCount = existing.apiCallsCount + monthlyStats.apiCallsCount,
            creditsAdded = existing.creditsAdded + monthlyStats.creditsAdded,
            peakMonthlyScreenshots = maxOf(existing.peakMonthlyScreenshots, monthlyStats.screenshotsCreated),
            activeMonths = if (monthlyStats.screenshotsCreated > 0 || monthlyStats.creditsUsed > 0) existing.activeMonths + 1 else existing.activeMonths,
            updatedAt = now,
            version = existing.version + 1
        )
    }
    
    private suspend fun getUsersWithMonthlyStats(month: String): List<String> {
        return try {
            // Get all users who have monthly stats for the given month
            // This is a simplified implementation - in a real scenario you might want
            // to query the monthly stats table directly for better performance
            val startDate = LocalDate.parse("${month}-01")
            val endDate = startDate.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
            
            dailyStatsRepository.getUsersWithStatsInPeriod(startDate, endDate).toList()
        } catch (e: Exception) {
            logger.error("Error getting users with monthly stats for month: $month", e)
            emptyList()
        }
    }
}