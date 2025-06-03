package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

/**
 * Domain entity for tracking user usage (Domain Layer)
 */
data class UserUsage(
    val userId: String,
    val month: String, // Format: "2025-01" 
    val totalRequests: Int,
    val planCreditsLimit: Int,
    val remainingCredits: Int,
    val lastRequestAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Domain entity for short-term usage tracking (Domain Layer)
 */
data class ShortTermUsage(
    val userId: String,
    val hourlyRequests: Int,
    val hourlyTimestamp: Instant,
    val minutelyRequests: Int,
    val minutelyTimestamp: Instant,
    val concurrentRequests: Int
)

/**
 * Domain entity for daily usage tracking (Domain Layer)
 */
data class DailyUsage(
    val userId: String,
    val date: String, // Format: "2025-01-02"
    val requestsUsed: Int,
    val dailyLimit: Int,
    val lastRequestAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val remainingRequests: Int get() = (dailyLimit - requestsUsed).coerceAtLeast(0)
    val hasRemainingRequests: Boolean get() = remainingRequests > 0
}