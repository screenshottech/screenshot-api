package dev.screenshotapi.core.domain.services

import dev.screenshotapi.core.domain.entities.RateLimitResult
import dev.screenshotapi.core.domain.entities.RateLimitStatus

/**
 * Operation types for rate limiting
 */
enum class RateLimitOperationType(val operationName: String) {
    SCREENSHOTS("screenshots"),
    ANALYSIS("analysis")
}

/**
 * Port for rate limiting operations in hexagonal architecture
 */
interface RateLimitingService {
    /**
     * Check if a user is allowed to make a request and consume tokens if allowed
     * Default to SCREENSHOTS for backward compatibility
     */
    suspend fun isAllowed(userId: String): Boolean

    /**
     * Check if a user is allowed to make a request and consume tokens if allowed for specific operation type
     */
    suspend fun isAllowed(userId: String, operationType: RateLimitOperationType): Boolean

    /**
     * Check rate limits and get complete result with all information for HTTP headers
     * Default to SCREENSHOTS for backward compatibility
     */
    suspend fun checkRateLimit(userId: String): RateLimitResult

    /**
     * Check rate limits and get complete result with all information for HTTP headers for specific operation type
     */
    suspend fun checkRateLimit(userId: String, operationType: RateLimitOperationType): RateLimitResult

    /**
     * Get current rate limit status for user (lightweight version)
     * Default to SCREENSHOTS for backward compatibility
     */
    suspend fun getRateLimitStatus(userId: String): RateLimitStatus

    /**
     * Get current rate limit status for user (lightweight version) for specific operation type
     */
    suspend fun getRateLimitStatus(userId: String, operationType: RateLimitOperationType): RateLimitStatus
}
