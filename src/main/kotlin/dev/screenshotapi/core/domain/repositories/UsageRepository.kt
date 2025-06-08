package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.UserUsage
import dev.screenshotapi.core.domain.entities.UsageTimelineEntry
import dev.screenshotapi.core.domain.entities.TimeGranularity
import kotlinx.datetime.LocalDate

/**
 * Repository interface for usage persistence (Domain Layer)
 */
interface UsageRepository {
    suspend fun findByUserAndMonth(userId: String, month: String): UserUsage?
    suspend fun incrementUsage(userId: String, month: String, amount: Int): UserUsage
    suspend fun createUsage(usage: UserUsage): UserUsage
    suspend fun updateUsage(usage: UserUsage): UserUsage
    suspend fun getUserMonthlyStats(userId: String, year: Int): List<UserUsage>
    
    /**
     * Get usage timeline data for analytics
     */
    suspend fun getUsageTimeline(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        granularity: TimeGranularity = TimeGranularity.DAILY
    ): List<UsageTimelineEntry>
}