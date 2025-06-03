package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.Plan
import dev.screenshotapi.core.domain.entities.RateLimitResult
import dev.screenshotapi.core.domain.entities.RateLimitStatus
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.services.RateLimitingService
import dev.screenshotapi.infrastructure.services.models.RateLimitInfo
import kotlinx.datetime.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


/**
 * Implementation of rate limiting service in infrastructure layer
 */
class RateLimitingServiceImpl(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val usageTrackingService: UsageTrackingServiceImpl,
    private val metricsService: MetricsService? = null
) : RateLimitingService {
    private val planCache = ConcurrentHashMap<String, Pair<Plan, Instant>>()
    private val cacheExpirationMinutes = 5

    companion object {
        private const val FREE_PLAN_CREDITS = 300
    }

    override suspend fun isAllowed(userId: String): Boolean {
        val status = getRateLimitStatus(userId)

        if (status.isAllowed) {
            // Record the request in persistent storage
            usageTrackingService.trackUsage(userId, 1)
        }

        return status.isAllowed
    }

    override suspend fun checkRateLimit(userId: String): RateLimitResult {
        val plan = getCachedPlan(userId) ?: getFreePlanDefaults()
        val rateLimitInfo = calculateRateLimitInfo(plan)
        val planType = getPlanType(plan)
        
        // Check monthly credits first
        val remainingCredits = usageTrackingService.getRemainingCredits(userId)
        if (remainingCredits <= 0) {
            metricsService?.recordRateLimitHit(userId, "monthly_credits", planType)
            metricsService?.recordRateLimitCheck(userId, false, planType)
            
            return RateLimitResult(
                allowed = false,
                remainingRequests = 0,
                resetTimeSeconds = getSecondsUntilNextMonth(),
                hasMonthlyCredits = false,
                remainingCredits = 0,
                requestsPerHour = rateLimitInfo.requestsPerHour,
                requestsPerMinute = rateLimitInfo.requestsPerMinute,
                remainingHourly = 0,
                remainingMinutely = 0,
                resetTimeHourly = getNextMonthReset(),
                resetTimeMinutely = getNextMonthReset(),
                retryAfterSeconds = getSecondsUntilNextMonth()
            )
        }
        
        // Get short-term usage for rate limiting
        val shortTermUsage = usageTrackingService.getShortTermUsage(userId)
        val now = Clock.System.now()

        // Check hourly limit
        if (shortTermUsage.hourlyRequests >= rateLimitInfo.requestsPerHour) {
            metricsService?.recordRateLimitHit(userId, "hourly", planType)
            metricsService?.recordRateLimitCheck(userId, false, planType)
            
            val resetTime = shortTermUsage.hourlyTimestamp.plus(1.hours)
            val retryAfter = (resetTime.epochSeconds - now.epochSeconds).coerceAtLeast(0)
            
            return RateLimitResult(
                allowed = false,
                remainingRequests = 0,
                resetTimeSeconds = retryAfter,
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits,
                requestsPerHour = rateLimitInfo.requestsPerHour,
                requestsPerMinute = rateLimitInfo.requestsPerMinute,
                remainingHourly = 0,
                remainingMinutely = (rateLimitInfo.requestsPerMinute - shortTermUsage.minutelyRequests).coerceAtLeast(0),
                resetTimeHourly = resetTime,
                resetTimeMinutely = shortTermUsage.minutelyTimestamp.plus(1.minutes),
                retryAfterSeconds = retryAfter
            )
        }

        // Check minutely limit
        if (shortTermUsage.minutelyRequests >= rateLimitInfo.requestsPerMinute) {
            metricsService?.recordRateLimitHit(userId, "minutely", planType)
            metricsService?.recordRateLimitCheck(userId, false, planType)
            
            val resetTime = shortTermUsage.minutelyTimestamp.plus(1.minutes)
            val retryAfter = (resetTime.epochSeconds - now.epochSeconds).coerceAtLeast(0)
            
            return RateLimitResult(
                allowed = false,
                remainingRequests = 0,
                resetTimeSeconds = retryAfter,
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits,
                requestsPerHour = rateLimitInfo.requestsPerHour,
                requestsPerMinute = rateLimitInfo.requestsPerMinute,
                remainingHourly = rateLimitInfo.requestsPerHour - shortTermUsage.hourlyRequests,
                remainingMinutely = 0,
                resetTimeHourly = shortTermUsage.hourlyTimestamp.plus(1.hours),
                resetTimeMinutely = resetTime,
                retryAfterSeconds = retryAfter
            )
        }

        // Record successful rate limit check
        metricsService?.recordRateLimitCheck(userId, true, planType)
        
        return RateLimitResult(
            allowed = true,
            remainingRequests = minOf(
                rateLimitInfo.requestsPerHour - shortTermUsage.hourlyRequests - 1,
                rateLimitInfo.requestsPerMinute - shortTermUsage.minutelyRequests - 1,
                remainingCredits
            ),
            resetTimeSeconds = 0,
            hasMonthlyCredits = true,
            remainingCredits = remainingCredits,
            requestsPerHour = rateLimitInfo.requestsPerHour,
            requestsPerMinute = rateLimitInfo.requestsPerMinute,
            remainingHourly = rateLimitInfo.requestsPerHour - shortTermUsage.hourlyRequests - 1,
            remainingMinutely = rateLimitInfo.requestsPerMinute - shortTermUsage.minutelyRequests - 1,
            resetTimeHourly = shortTermUsage.hourlyTimestamp.plus(1.hours),
            resetTimeMinutely = shortTermUsage.minutelyTimestamp.plus(1.minutes)
        )
    }

    override suspend fun getRateLimitStatus(userId: String): RateLimitStatus {
        val plan = getCachedPlan(userId) ?: getFreePlanDefaults()
        val rateLimitInfo = calculateRateLimitInfo(plan)

        // Check monthly credits first
        val remainingCredits = usageTrackingService.getRemainingCredits(userId)
        if (remainingCredits <= 0) {
            return RateLimitStatus(
                isAllowed = false,
                remainingRequests = 0,
                resetTimeSeconds = getSecondsUntilNextMonth(),
                hasMonthlyCredits = false,
                remainingCredits = 0
            )
        }

        // Get short-term usage for rate limiting
        val shortTermUsage = usageTrackingService.getShortTermUsage(userId)
        val now = Clock.System.now()

        // Check hourly limit
        if (shortTermUsage.hourlyRequests >= rateLimitInfo.requestsPerHour) {
            val resetTime = shortTermUsage.hourlyTimestamp.plus(1.hours)
            val retryAfter = (resetTime.epochSeconds - now.epochSeconds).coerceAtLeast(0)

            return RateLimitStatus(
                isAllowed = false,
                remainingRequests = 0,
                resetTimeSeconds = retryAfter,
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits
            )
        }

        // Check minutely limit
        if (shortTermUsage.minutelyRequests >= rateLimitInfo.requestsPerMinute) {
            val resetTime = shortTermUsage.minutelyTimestamp.plus(1.minutes)
            val retryAfter = (resetTime.epochSeconds - now.epochSeconds).coerceAtLeast(0)

            return RateLimitStatus(
                isAllowed = false,
                remainingRequests = 0,
                resetTimeSeconds = retryAfter,
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits
            )
        }

        return RateLimitStatus(
            isAllowed = true,
            remainingRequests = minOf(
                rateLimitInfo.requestsPerHour - shortTermUsage.hourlyRequests - 1,
                rateLimitInfo.requestsPerMinute - shortTermUsage.minutelyRequests - 1,
                remainingCredits
            ),
            resetTimeSeconds = 0,
            hasMonthlyCredits = true,
            remainingCredits = remainingCredits
        )
    }

    fun incrementConcurrentRequests(userId: String) {
        usageTrackingService.incrementConcurrentRequests(userId)
    }

    fun decrementConcurrentRequests(userId: String) {
        usageTrackingService.decrementConcurrentRequests(userId)
    }

    fun checkConcurrentLimit(userId: String): Boolean {
        val plan = planCache[userId]?.first ?: getFreePlanDefaults()
        val rateLimitInfo = calculateRateLimitInfo(plan)
        val concurrentCount = usageTrackingService.getConcurrentRequestsCount(userId)

        return concurrentCount < rateLimitInfo.concurrentRequests
    }

    private suspend fun getCachedPlan(userId: String): Plan? {
        val now = Clock.System.now()
        val cached = planCache[userId]

        // Return cached plan if still valid
        if (cached != null && cached.second.plus(cacheExpirationMinutes.minutes) > now) {
            return cached.first
        }

        // Fetch fresh plan from database
        return try {
            val user = userRepository.findById(userId)
            if (user != null) {
                val plan = getUserPlan(user.planId)
                if (plan != null) {
                    planCache[userId] = plan to now
                    plan
                } else null
            } else null
        } catch (e: Exception) {
            // Log error but don't throw - use fallback
            null
        }
    }

    private suspend fun getUserPlan(planId: String): Plan? {
        return try {
            planRepository.findById(planId)
        } catch (e: Exception) {
            null
        }
    }

    private fun getFreePlanDefaults(): Plan = Plan(
        id = "plan_free",
        name = "Free Forever",
        creditsPerMonth = FREE_PLAN_CREDITS,
        priceCentsMonthly = 0,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private fun calculateRateLimitInfo(plan: Plan): RateLimitInfo {
        val creditsPerMonth = plan.creditsPerMonth
        
        // User-friendly rate limits based on competitive analysis
        val (requestsPerHour, requestsPerMinute, concurrentRequests) = when {
            creditsPerMonth <= 300 -> Triple(10, 1, 2)        // Free: 10/hour, 1/minute, 2 concurrent
            creditsPerMonth <= 2000 -> Triple(60, 1, 5)       // Starter: 60/hour, 1/minute, 5 concurrent  
            creditsPerMonth <= 10000 -> Triple(300, 5, 10)    // Professional: 300/hour, 5/minute, 10 concurrent
            else -> Triple(1500, 25, Int.MAX_VALUE)           // Enterprise: 1500/hour, 25/minute, unlimited concurrent
        }

        return RateLimitInfo(
            requestsPerHour = requestsPerHour,
            requestsPerMinute = requestsPerMinute,
            concurrentRequests = concurrentRequests
        )
    }

    private fun getSecondsUntilNextMonth(): Long {
        val now = Clock.System.now()
        val nextMonth = getNextMonthReset()
        return (nextMonth.epochSeconds - now.epochSeconds).coerceAtLeast(0)
    }
    
    private fun getNextMonthReset(): Instant {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.UTC)
        val nextMonth = if (localDateTime.monthNumber == 12) {
            LocalDateTime(localDateTime.year + 1, 1, 1, 0, 0, 0)
        } else {
            LocalDateTime(localDateTime.year, localDateTime.monthNumber + 1, 1, 0, 0, 0)
        }
        return nextMonth.toInstant(TimeZone.UTC)
    }
    
    private fun getPlanType(plan: Plan): String {
        return when {
            plan.creditsPerMonth <= 300 -> "free"
            plan.creditsPerMonth <= 2000 -> "starter"
            plan.creditsPerMonth <= 10000 -> "professional"
            else -> "enterprise"
        }
    }

    fun clearUserLimits() {
        usageTrackingService.clearCaches()
        planCache.clear()
    }
}
