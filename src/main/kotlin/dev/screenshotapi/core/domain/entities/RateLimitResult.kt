package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

/**
 * Complete rate limit result with all information needed for HTTP responses
 */
data class RateLimitResult(
    val allowed: Boolean,
    val remainingRequests: Int,
    val resetTimeSeconds: Long,
    val hasMonthlyCredits: Boolean,
    val remainingCredits: Int,
    val requestsPerHour: Int,
    val requestsPerMinute: Int,
    val remainingHourly: Int,
    val remainingMinutely: Int,
    val resetTimeHourly: Instant,
    val resetTimeMinutely: Instant,
    val retryAfterSeconds: Long? = null
)