package dev.screenshotapi.core.domain.entities

data class RateLimitStatus(
    val isAllowed: Boolean,
    val remainingRequests: Int,
    val resetTimeSeconds: Long,
    val hasMonthlyCredits: Boolean,
    val remainingCredits: Int
)
