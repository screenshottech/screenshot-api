package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.DailyUserStats
import dev.screenshotapi.core.domain.entities.MonthlyUserStats
import dev.screenshotapi.core.domain.entities.YearlyUserStats
import kotlinx.datetime.LocalDate

/**
 * Repository interface for daily, monthly, and yearly user statistics (Domain Layer)
 * 
 * This repository provides atomic operations for maintaining aggregated statistics
 * to ensure data consistency in a distributed environment.
 */
interface DailyStatsRepository {
    
    // Daily Stats Operations
    suspend fun findByUserAndDate(userId: String, date: LocalDate): DailyUserStats?
    suspend fun findByUserAndDateRange(userId: String, startDate: LocalDate, endDate: LocalDate): List<DailyUserStats>
    suspend fun create(stats: DailyUserStats): DailyUserStats
    suspend fun atomicUpdate(stats: DailyUserStats): DailyUserStats
    suspend fun atomicIncrement(
        userId: String, 
        date: LocalDate, 
        field: StatsField, 
        amount: Int = 1
    ): DailyUserStats?
    
    // Batch operations for reconciliation jobs
    suspend fun batchCreate(statsList: List<DailyUserStats>): List<DailyUserStats>
    suspend fun findUsersWithActivityOnDate(date: LocalDate): List<String>
    
    // Data retention and cleanup
    suspend fun deleteOlderThan(date: LocalDate): Int
    suspend fun findDatesForUser(userId: String, year: Int): List<LocalDate>
    
    // Monthly Stats Operations
    suspend fun findMonthlyByUserAndMonth(userId: String, month: String): MonthlyUserStats?
    suspend fun createMonthly(stats: MonthlyUserStats): MonthlyUserStats
    suspend fun updateMonthly(stats: MonthlyUserStats): MonthlyUserStats
    suspend fun findMonthlyByUserAndYear(userId: String, year: Int): List<MonthlyUserStats>
    suspend fun deleteMonthlyOlderThan(month: String): Int
    
    // Yearly Stats Operations
    suspend fun findYearlyByUserAndYear(userId: String, year: Int): YearlyUserStats?
    suspend fun createYearly(stats: YearlyUserStats): YearlyUserStats
    suspend fun updateYearly(stats: YearlyUserStats): YearlyUserStats
    suspend fun findYearlyByUser(userId: String): List<YearlyUserStats>
    suspend fun deleteYearlyOlderThan(year: Int): Int
    
    // Aggregation support methods
    suspend fun aggregateDailyToMonthly(userId: String, month: String): MonthlyUserStats?
    suspend fun aggregateMonthlyToYearly(userId: String, year: Int): YearlyUserStats?
    
    // Statistics and health check methods
    suspend fun getTableSizes(): Map<String, Long>
    suspend fun getOldestRecord(): LocalDate?
    suspend fun getUsersWithStatsInPeriod(startDate: LocalDate, endDate: LocalDate): Set<String>
}

/**
 * Enum for atomic increment operations on specific fields
 */
enum class StatsField {
    SCREENSHOTS_CREATED,
    SCREENSHOTS_COMPLETED,
    SCREENSHOTS_FAILED,
    SCREENSHOTS_RETRIED,
    CREDITS_USED,
    CREDITS_ADDED,
    API_CALLS_COUNT,
    API_KEYS_CREATED,
    PLAN_CHANGES,
    PAYMENTS_PROCESSED
}