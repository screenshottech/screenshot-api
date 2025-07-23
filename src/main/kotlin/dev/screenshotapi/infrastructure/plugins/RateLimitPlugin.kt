package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.core.domain.entities.RateLimitResult
import dev.screenshotapi.core.domain.services.RateLimitingService
import dev.screenshotapi.core.domain.services.RateLimitOperationType
import dev.screenshotapi.infrastructure.auth.ApiKeyPrincipal
import dev.screenshotapi.infrastructure.auth.UserPrincipal
import dev.screenshotapi.infrastructure.auth.requireHybridUserId
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
                // Use hybrid authentication to get user ID (JWT OR API Key)
                val apiKeyPrincipal = call.principal<ApiKeyPrincipal>()
                val userPrincipal = call.principal<UserPrincipal>()
                val authHeader = call.request.headers["Authorization"]
                val apiKeyHeader = call.request.headers["X-API-Key"]
                
                val userId = try {
                    call.requireHybridUserId()
                } catch (e: Exception) {
                    call.application.log.warn("📷 SCREENSHOTS requestKey - No valid authentication: ${e.message}")
                    null
                }
                
                call.application.log.info("📷 SCREENSHOTS requestKey - ApiKeyPrincipal: $apiKeyPrincipal, UserPrincipal: $userPrincipal, UserId: $userId, URI: ${call.request.local.uri}")
                call.application.log.info("📷 SCREENSHOTS Headers - Authorization: ${authHeader?.take(20)}..., X-API-Key: ${apiKeyHeader?.take(10)}...")
                userId ?: "anonymous"
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
                        call.application.log.info("📷 SCREENSHOTS Rate Limit Check - User: $userId, URI: ${call.request.local.uri}")

                        // Check rate limits with your service
                        val result = rateLimitingService.checkRateLimit(userId)

                        call.application.log.info("📷 SCREENSHOTS Rate Limit Result - User: $userId, Allowed: ${result.allowed}, RemainingCredits: ${result.remainingCredits}")

                        // Add rate limit headers
                        addRateLimitHeaders(call, result)

                        if (result.allowed) {
                            call.application.log.info("✅ SCREENSHOTS Request ALLOWED for user $userId")
                            0 // Allow request
                        } else {
                            call.application.log.warn("❌ SCREENSHOTS Request BLOCKED for user $userId - Reason: retryAfter=${result.retryAfterSeconds}, hasCredits=${result.hasMonthlyCredits}")
                            // Add retry after header if available
                            result.retryAfterSeconds.let { retryAfter ->
                                call.response.headers.append("Retry-After", retryAfter.toString())
                            }
                            10000 // Block request
                        }

                    } catch (e: Exception) {
                        // Log error but allow request (fail open)
                        call.application.log.error("🚨 Screenshots rate limiting ERROR for $userId: ${e.message}", e)
                        0 // Allow on error
                    }
                }
            }
        }

        register(RateLimitName("analysis")) {
            // Analysis rate limiting with slightly higher cost since AI analysis is more expensive
            rateLimiter(limit = 5000, refillPeriod = 1.hours)

            requestKey { call ->
                // Use hybrid authentication to get user ID (JWT OR API Key)
                val apiKeyPrincipal = call.principal<ApiKeyPrincipal>()
                val userPrincipal = call.principal<UserPrincipal>()
                val authHeader = call.request.headers["Authorization"]
                val apiKeyHeader = call.request.headers["X-API-Key"]
                
                val userId = try {
                    call.requireHybridUserId()
                } catch (e: Exception) {
                    call.application.log.warn("🔍 ANALYSIS requestKey - No valid authentication: ${e.message}")
                    null
                }
                
                call.application.log.info("🔍 ANALYSIS requestKey - ApiKeyPrincipal: $apiKeyPrincipal, UserPrincipal: $userPrincipal, UserId: $userId, URI: ${call.request.local.uri}")
                call.application.log.info("🔍 ANALYSIS Headers - Authorization: ${authHeader?.take(20)}..., X-API-Key: ${apiKeyHeader?.take(10)}...")
                userId ?: "anonymous"
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
                    return@requestWeight 5000
                }

                val userId = key as String

                return@requestWeight runBlocking {
                    try {
                        call.application.log.info("🔍 ANALYSIS Rate Limit Check - User: $userId, URI: ${call.request.local.uri}")

                        // Check rate limits with your service using ANALYSIS operation type
                        val result = rateLimitingService.checkRateLimit(userId, RateLimitOperationType.ANALYSIS)

                        call.application.log.info("🔍 ANALYSIS Rate Limit Result - User: $userId, Allowed: ${result.allowed}, RemainingCredits: ${result.remainingCredits}")

                        // Add rate limit headers
                        addRateLimitHeaders(call, result)

                        if (result.allowed) {
                            call.application.log.info("✅ ANALYSIS Request ALLOWED for user $userId")
                            0 // Allow request - analysis uses independent rate limiting
                        } else {
                            call.application.log.warn("❌ ANALYSIS Request BLOCKED for user $userId - Reason: retryAfter=${result.retryAfterSeconds}, hasCredits=${result.hasMonthlyCredits}")
                            // Add retry after header if available
                            result.retryAfterSeconds.let { retryAfter ->
                                call.response.headers.append("Retry-After", retryAfter.toString())
                            }
                            5000 // Block request
                        }

                    } catch (e: Exception) {
                        // Log error but allow request (fail open)
                        call.application.log.error("🚨 Analysis rate limiting ERROR for $userId: ${e.message}", e)
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
private fun addRateLimitHeaders(call: ApplicationCall, result: RateLimitResult) {
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
