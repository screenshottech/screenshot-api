package dev.screenshotapi.core.ports.output

import dev.screenshotapi.core.domain.entities.UserUsage

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
}
