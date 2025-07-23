package dev.screenshotapi.core.ports.output

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.services.RateLimitOperationType
import kotlinx.datetime.Instant

interface UsageTrackingPort {
    /**
     * Track usage for a user in the current month
     */
    suspend fun trackUsage(userId: String, amount: Int = 1): UserUsage

    /**
     * Get remaining credits for a user in the current month
     */
    suspend fun getRemainingCredits(userId: String): Int

    /**
     * Check if user has sufficient credits
     */
    suspend fun hasCredits(userId: String, amount: Int = 1): Boolean

    /**
     * Get usage statistics for a user
     */
    suspend fun getUserUsage(userId: String, month: String): UserUsage?

    /**
     * Get monthly statistics for a user
     */
    suspend fun getUserMonthlyStats(userId: String, year: Int): List<UserUsage>

    /**
     * Get short-term usage for rate limiting (backward compatibility)
     */
    fun getShortTermUsage(userId: String): ShortTermUsage

    /**
     * Get short-term usage for rate limiting by operation type
     */
    fun getShortTermUsage(userId: String, operationType: RateLimitOperationType): ShortTermUsage

    /**
     * Update short-term usage counters (backward compatibility)
     */
    suspend fun updateShortTermUsage(userId: String, now: Instant)

    /**
     * Update short-term usage counters by operation type
     */
    suspend fun updateShortTermUsage(userId: String, now: Instant, operationType: RateLimitOperationType)

    /**
     * Track daily usage
     */
    suspend fun trackDailyUsage(userId: String, amount: Int = 1): DailyUsage

    /**
     * Get daily usage for rate limiting
     */
    suspend fun getDailyUsage(userId: String): DailyUsage

    /**
     * Check if user has remaining daily quota
     */
    suspend fun hasRemainingDailyQuota(userId: String): Boolean

    /**
     * Get current month in YYYY-MM format
     */
    fun getCurrentMonth(): String
}
