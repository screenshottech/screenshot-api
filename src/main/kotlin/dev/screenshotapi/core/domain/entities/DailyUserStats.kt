package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Domain entity for daily aggregated user statistics (Domain Layer)
 * 
 * This entity represents pre-aggregated daily statistics for efficient querying
 * and eliminates the need to scan large usage_logs tables for analytics.
 */
data class DailyUserStats(
    val userId: String,
    val date: LocalDate, // The date for these statistics (YYYY-MM-DD)
    
    // Core metrics
    val screenshotsCreated: Int = 0,
    val screenshotsCompleted: Int = 0,
    val screenshotsFailed: Int = 0,
    val screenshotsRetried: Int = 0,
    
    // Usage metrics
    val creditsUsed: Int = 0,
    val apiCallsCount: Int = 0,
    val apiKeysUsed: Int = 0, // Distinct API keys used in the day
    
    // Billing metrics
    val creditsAdded: Int = 0,
    val paymentsProcessed: Int = 0,
    
    // Administrative metrics
    val apiKeysCreated: Int = 0,
    val planChanges: Int = 0,
    
    // Metadata
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 1 // For optimistic locking
) {
    // Computed properties for business logic
    val totalScreenshots: Int get() = screenshotsCreated
    val successfulScreenshots: Int get() = screenshotsCompleted
    val successRate: Double get() = if (screenshotsCompleted > 0) {
        screenshotsCompleted.toDouble() / screenshotsCreated.toDouble()
    } else 0.0
    
    // Check if this is an empty stats record (no activity)
    val hasActivity: Boolean get() = screenshotsCreated > 0 || creditsUsed > 0 || 
                                   apiCallsCount > 0 || creditsAdded > 0
    
    /**
     * Create an updated version with incremented values
     */
    fun incrementScreenshotsCreated(count: Int = 1): DailyUserStats = 
        copy(screenshotsCreated = screenshotsCreated + count, updatedAt = kotlinx.datetime.Clock.System.now(), version = version + 1)
    
    fun incrementScreenshotsCompleted(count: Int = 1): DailyUserStats = 
        copy(screenshotsCompleted = screenshotsCompleted + count, updatedAt = kotlinx.datetime.Clock.System.now(), version = version + 1)
    
    fun incrementScreenshotsFailed(count: Int = 1): DailyUserStats = 
        copy(screenshotsFailed = screenshotsFailed + count, updatedAt = kotlinx.datetime.Clock.System.now(), version = version + 1)
    
    fun incrementScreenshotsRetried(count: Int = 1): DailyUserStats = 
        copy(screenshotsRetried = screenshotsRetried + count, updatedAt = kotlinx.datetime.Clock.System.now(), version = version + 1)
    
    fun incrementCreditsUsed(count: Int): DailyUserStats = 
        copy(creditsUsed = creditsUsed + count, updatedAt = kotlinx.datetime.Clock.System.now(), version = version + 1)
    
    fun incrementApiCalls(count: Int = 1): DailyUserStats = 
        copy(apiCallsCount = apiCallsCount + count, updatedAt = kotlinx.datetime.Clock.System.now(), version = version + 1)
    
    fun incrementCreditsAdded(count: Int): DailyUserStats = 
        copy(creditsAdded = creditsAdded + count, updatedAt = kotlinx.datetime.Clock.System.now(), version = version + 1)
    
    companion object {
        /**
         * Create a new empty daily stats record for a user and date
         */
        fun createEmpty(userId: String, date: LocalDate): DailyUserStats {
            val now = kotlinx.datetime.Clock.System.now()
            return DailyUserStats(
                userId = userId,
                date = date,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

/**
 * Domain entity for monthly aggregated user statistics
 */
data class MonthlyUserStats(
    val userId: String,
    val month: String, // Format: "2025-01"
    
    // Aggregated metrics from daily stats
    val screenshotsCreated: Int = 0,
    val screenshotsCompleted: Int = 0,
    val screenshotsFailed: Int = 0,
    val screenshotsRetried: Int = 0,
    val creditsUsed: Int = 0,
    val apiCallsCount: Int = 0,
    val creditsAdded: Int = 0,
    
    // Monthly specific metrics
    val peakDailyScreenshots: Int = 0,
    val activeDays: Int = 0, // Days with activity
    
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 1
) {
    val successRate: Double get() = if (screenshotsCompleted > 0) {
        screenshotsCompleted.toDouble() / screenshotsCreated.toDouble()
    } else 0.0
    
    val averageDailyScreenshots: Double get() = if (activeDays > 0) {
        screenshotsCreated.toDouble() / activeDays.toDouble()
    } else 0.0
}

/**
 * Domain entity for yearly aggregated user statistics
 */
data class YearlyUserStats(
    val userId: String,
    val year: Int,
    
    // Aggregated metrics from monthly stats
    val screenshotsCreated: Int = 0,
    val screenshotsCompleted: Int = 0,
    val screenshotsFailed: Int = 0,
    val screenshotsRetried: Int = 0,
    val creditsUsed: Int = 0,
    val apiCallsCount: Int = 0,
    val creditsAdded: Int = 0,
    
    // Yearly specific metrics
    val peakMonthlyScreenshots: Int = 0,
    val activeMonths: Int = 0, // Months with activity
    
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 1
) {
    val successRate: Double get() = if (screenshotsCompleted > 0) {
        screenshotsCompleted.toDouble() / screenshotsCreated.toDouble()
    } else 0.0
    
    val averageMonthlyScreenshots: Double get() = if (activeMonths > 0) {
        screenshotsCreated.toDouble() / activeMonths.toDouble()
    } else 0.0
}