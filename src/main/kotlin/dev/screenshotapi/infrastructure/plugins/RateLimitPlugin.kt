package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.core.domain.services.RateLimitingService
import dev.screenshotapi.infrastructure.auth.ApiKeyPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject

fun Application.configureRateLimit() {
    val rateLimitingService by inject<RateLimitingService>()
    install(RateLimit) {
        register(RateLimitName("screenshots")) {
            // Set a high limit - your service will do the real limiting
            rateLimiter(limit = 10000, refillPeriod = 1.hours)

            requestKey { call ->
                // Use user ID as key, fallback to "anonymous"
                call.principal<ApiKeyPrincipal>()?.userId ?: "anonymous"
            }

            requestWeight { call, key ->
                // Skip rate limiting for non-API endpoints
                if (!call.request.local.uri.startsWith("/api/")) {
                    return@requestWeight 0
                }

                // Check if rate limiting is enabled
                if (!isRateLimitingEnabled()) {
                    return@requestWeight 0
                }

                // Block anonymous requests
                if (key == "anonymous") {
                    return@requestWeight 10000
                }

                val userId = key as String

                return@requestWeight runBlocking {
                    try {
                        // Check rate limits with your service
                        val result = rateLimitingService.checkRateLimit(userId)

                        // Add rate limit headers
                        addRateLimitHeaders(call, result)

                        if (result.allowed) {
                            0 // Allow request
                        } else {
                            // Add retry after header if available
                            result.retryAfterSeconds?.let { retryAfter ->
                                call.response.headers.append("Retry-After", retryAfter.toString())
                            }
                            10000 // Block request
                        }

                    } catch (e: Exception) {
                        // Log error but allow request (fail open)
                        call.application.log.warn("Rate limiting error for $userId: ${e.message}")
                        0 // Allow on error
                    }
                }
            }
        }
    }
}

/**
 * Add comprehensive rate limit headers
 */
private fun addRateLimitHeaders(call: ApplicationCall, result: dev.screenshotapi.core.domain.entities.RateLimitResult) {
    call.response.headers.apply {
        // Standard rate limit headers
        append("X-RateLimit-Limit", result.requestsPerHour.toString())
        append("X-RateLimit-Remaining", result.remainingRequests.toString())
        append("X-RateLimit-Reset", result.resetTimeHourly.epochSeconds.toString())
        
        // Detailed rate limit headers
        append("X-RateLimit-Limit-Hourly", result.requestsPerHour.toString())
        append("X-RateLimit-Remaining-Hourly", result.remainingHourly.toString())
        append("X-RateLimit-Reset-Hourly", result.resetTimeHourly.epochSeconds.toString())
        
        append("X-RateLimit-Limit-Minutely", result.requestsPerMinute.toString())
        append("X-RateLimit-Remaining-Minutely", result.remainingMinutely.toString())
        append("X-RateLimit-Reset-Minutely", result.resetTimeMinutely.epochSeconds.toString())
        
        // Monthly credits
        append("X-RateLimit-Credits-Remaining", result.remainingCredits.toString())
        append("X-RateLimit-Credits-Used", (result.remainingCredits).toString()) // This would need monthly usage data
        
        // Plan information
        append("X-RateLimit-Plan", "auto-detected") // Could be enhanced to show actual plan name
    }
}

/**
 * Check if rate limiting is enabled
 */
private fun isRateLimitingEnabled(): Boolean {
    return System.getenv("RATE_LIMITING_ENABLED")?.toBoolean() ?: true
}
