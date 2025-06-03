package dev.screenshotapi.core.domain.services

import dev.screenshotapi.core.domain.entities.RateLimitResult
import dev.screenshotapi.core.domain.entities.RateLimitStatus

/**
 * Port for rate limiting operations in hexagonal architecture
 */
interface RateLimitingService {
    /**
     * Check if a user is allowed to make a request and consume tokens if allowed
     */
    suspend fun isAllowed(userId: String): Boolean

    /**
     * Check rate limits and get complete result with all information for HTTP headers
     */
    suspend fun checkRateLimit(userId: String): RateLimitResult

    /**
     * Get current rate limit status for user (lightweight version)
     */
    suspend fun getRateLimitStatus(userId: String): RateLimitStatus
}
