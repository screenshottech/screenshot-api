package dev.screenshotapi.infrastructure.services.models

data class RateLimitInfo(
    val requestsPerHour: Int,
    val requestsPerMinute: Int,
    val concurrentRequests: Int
)

