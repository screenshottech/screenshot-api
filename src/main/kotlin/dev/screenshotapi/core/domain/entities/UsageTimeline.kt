package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.LocalDate

/**
 * Domain entity representing usage data for a specific time period
 */
data class UsageTimelineEntry(
    val date: LocalDate,
    val screenshots: Int,
    val creditsUsed: Int,
    val apiCalls: Int,
    val successfulScreenshots: Int = screenshots,
    val failedScreenshots: Int = 0
)

/**
 * Enum for time period granularity
 */
enum class TimeGranularity {
    DAILY,
    WEEKLY,
    MONTHLY
}

/**
 * Enum for time period duration
 */
enum class TimePeriod(val days: Int) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
    ONE_YEAR(365);
    
    companion object {
        fun fromString(period: String?): TimePeriod {
            return when (period?.lowercase()) {
                "7d" -> SEVEN_DAYS
                "30d" -> THIRTY_DAYS
                "90d" -> NINETY_DAYS
                "1y" -> ONE_YEAR
                else -> throw IllegalArgumentException("Invalid period: $period. Valid values: 7d, 30d, 90d, 1y")
            }
        }
        
        fun fromStringOrDefault(period: String?): TimePeriod {
            return when (period?.lowercase()) {
                "7d" -> SEVEN_DAYS
                "30d" -> THIRTY_DAYS
                "90d" -> NINETY_DAYS
                "1y" -> ONE_YEAR
                else -> THIRTY_DAYS // default
            }
        }
    }
}

/**
 * Domain entity for usage timeline summary
 */
data class UsageTimelineSummary(
    val totalScreenshots: Int,
    val totalCreditsUsed: Int,
    val totalApiCalls: Int,
    val averageDaily: Double,
    val successRate: Double,
    val peakDay: LocalDate?,
    val peakDayScreenshots: Int
)