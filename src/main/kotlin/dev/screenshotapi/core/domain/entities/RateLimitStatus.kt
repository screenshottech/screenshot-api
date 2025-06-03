package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

data class RateLimitStatus(
    val isAllowed: Boolean,
    val remainingRequests: Int,
    val resetTimeHourly: Instant,
    val resetTimeMinutely: Instant,
    val retryAfterSeconds: Int,
    val hasMonthlyCredits: Boolean,
    val remainingCredits: Int,
    val resetTimeSeconds: Long
)
