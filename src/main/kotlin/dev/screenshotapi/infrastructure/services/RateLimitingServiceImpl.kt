package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.services.RateLimitingService
import dev.screenshotapi.core.domain.services.RateLimitOperationType
import dev.screenshotapi.core.ports.output.UsageTrackingPort
import kotlinx.datetime.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RateLimitingServiceImpl(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val usageTrackingService: UsageTrackingPort,
    private val metricsService: MetricsService? = null
) : RateLimitingService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val planCache = ConcurrentHashMap<String, Pair<Plan, Instant>>()
    private val cacheExpirationMinutes = 5

    companion object {
        private const val FREE_PLAN_CREDITS = 300
    }

    override suspend fun isAllowed(userId: String): Boolean {
        return isAllowed(userId, RateLimitOperationType.SCREENSHOTS)
    }

    override suspend fun isAllowed(userId: String, operationType: RateLimitOperationType): Boolean {
        val now = Clock.System.now()
        
        // FIRST: Update short-term usage counters for accurate rate limiting by operation type
        usageTrackingService.updateShortTermUsage(userId, now, operationType)
        
        val status = getRateLimitStatus(userId, operationType)
        
        logger.info("Rate limit check for user $userId (${operationType.operationName}): allowed=${status.isAllowed}, " +
                "remainingRequests=${status.remainingRequests}, " +
                "remainingCredits=${status.remainingCredits}")

        if (status.isAllowed) {
            // Record the request in persistent storage (monthly tracking)
            usageTrackingService.trackUsage(userId, 1)
        } else {
            logger.warn("Rate limit BLOCKED for user $userId (${operationType.operationName}): " +
                    "retryAfterSeconds=${status.retryAfterSeconds}, " +
                    "hasMonthlyCredits=${status.hasMonthlyCredits}")
        }

        return status.isAllowed
    }

    override suspend fun checkRateLimit(userId: String): RateLimitResult {
        return checkRateLimit(userId, RateLimitOperationType.SCREENSHOTS)
    }

    override suspend fun checkRateLimit(userId: String, operationType: RateLimitOperationType): RateLimitResult {
        val now = Clock.System.now()
        // Update short-term usage first for accurate rate limiting by operation type
        usageTrackingService.updateShortTermUsage(userId, now, operationType)
        val shortTermUsage = usageTrackingService.getShortTermUsage(userId, operationType)
        val remainingCredits = usageTrackingService.getRemainingCredits(userId)

        // Get user's plan
        val user = userRepository.findById(userId)
        val planId = user?.planId
        val rateLimitInfo = getRateLimitInfo(planId)
        val planType = user?.planId ?: "free"

        // For analysis operations, check credits but skip rate limiting (no hourly/minutely limits)
        if (operationType == RateLimitOperationType.ANALYSIS) {
            // Check if user has remaining credits
            if (remainingCredits <= 0) {
                metricsService?.recordRateLimitCheck(userId, false, planType)
                return RateLimitResult(
                    allowed = false,
                    remainingRequests = 0,
                    resetTimeSeconds = getSecondsUntilNextMonth(),
                    hasMonthlyCredits = false,
                    remainingCredits = 0,
                    requestsPerHour = rateLimitInfo.requestsPerHour,
                    requestsPerMinute = rateLimitInfo.requestsPerMinute,
                    remainingHourly = rateLimitInfo.requestsPerHour,
                    remainingMinutely = rateLimitInfo.requestsPerMinute,
                    resetTimeHourly = getNextMonthReset(),
                    resetTimeMinutely = getNextMonthReset(),
                    retryAfterSeconds = getSecondsUntilNextMonth().toInt()
                )
            }

            // Allow analysis if user has credits (no rate limiting for analysis)
            metricsService?.recordRateLimitCheck(userId, true, planType)
            return RateLimitResult(
                allowed = true,
                remainingRequests = remainingCredits,
                resetTimeSeconds = 0,
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits,
                requestsPerHour = rateLimitInfo.requestsPerHour,
                requestsPerMinute = rateLimitInfo.requestsPerMinute,
                remainingHourly = rateLimitInfo.requestsPerHour,
                remainingMinutely = rateLimitInfo.requestsPerMinute,
                resetTimeHourly = shortTermUsage.hourlyTimestamp.plus(1.hours),
                resetTimeMinutely = shortTermUsage.minutelyTimestamp.plus(1.minutes),
                retryAfterSeconds = 0
            )
        }

        // For screenshots operations, apply normal rate limiting
        // Check if user has remaining credits
        if (remainingCredits <= 0) {
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
                retryAfterSeconds = getSecondsUntilNextMonth().toInt()
            )
        }

        // Check hourly limit
        if (shortTermUsage.hourlyRequests >= rateLimitInfo.requestsPerHour) {
            val resetTime = shortTermUsage.hourlyTimestamp.plus(1.hours)
            val retryAfter = maxOf(0L, (resetTime.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000)

            metricsService?.recordRateLimitCheck(userId, false, planType)
            return RateLimitResult(
                allowed = false,
                remainingRequests = 0,
                resetTimeSeconds = retryAfter,
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits,
                requestsPerHour = rateLimitInfo.requestsPerHour,
                requestsPerMinute = rateLimitInfo.requestsPerMinute,
                remainingHourly = 0,
                remainingMinutely = rateLimitInfo.requestsPerMinute - shortTermUsage.minutelyRequests,
                resetTimeHourly = resetTime,
                resetTimeMinutely = shortTermUsage.minutelyTimestamp.plus(1.minutes),
                retryAfterSeconds = retryAfter.toInt()
            )
        }

        // Check minutely limit
        if (shortTermUsage.minutelyRequests >= rateLimitInfo.requestsPerMinute) {
            val resetTime = shortTermUsage.minutelyTimestamp.plus(1.minutes)
            val retryAfter = (resetTime.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000

            metricsService?.recordRateLimitCheck(userId, false, planType)
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
                retryAfterSeconds = retryAfter.toInt()
            )
        }

        // Record successful rate limit check
        metricsService?.recordRateLimitCheck(userId, true, planType)

        return RateLimitResult(
            allowed = true,
            remainingRequests = minOf(
                rateLimitInfo.requestsPerHour - shortTermUsage.hourlyRequests,
                rateLimitInfo.requestsPerMinute - shortTermUsage.minutelyRequests,
                remainingCredits
            ),
            resetTimeSeconds = 0,
            hasMonthlyCredits = true,
            remainingCredits = remainingCredits,
            requestsPerHour = rateLimitInfo.requestsPerHour,
            requestsPerMinute = rateLimitInfo.requestsPerMinute,
            remainingHourly = rateLimitInfo.requestsPerHour - shortTermUsage.hourlyRequests,
            remainingMinutely = rateLimitInfo.requestsPerMinute - shortTermUsage.minutelyRequests,
            resetTimeHourly = shortTermUsage.hourlyTimestamp.plus(1.hours),
            resetTimeMinutely = shortTermUsage.minutelyTimestamp.plus(1.minutes),
            retryAfterSeconds = 0
        )
    }

    override suspend fun getRateLimitStatus(userId: String): RateLimitStatus {
        return getRateLimitStatus(userId, RateLimitOperationType.SCREENSHOTS)
    }

    override suspend fun getRateLimitStatus(userId: String, operationType: RateLimitOperationType): RateLimitStatus {
        val now = Clock.System.now()
        // Update short-term usage first for accurate rate limiting by operation type
        usageTrackingService.updateShortTermUsage(userId, now, operationType)
        val shortTermUsage = usageTrackingService.getShortTermUsage(userId, operationType)
        val remainingCredits = usageTrackingService.getRemainingCredits(userId)

        // Get user's plan
        val user = userRepository.findById(userId)
        val rateLimitInfo = getRateLimitInfo(user?.planId)
        val planType = user?.planId ?: "free"
        
        logger.info("Rate limit status for user $userId (plan: $planType, operation: ${operationType.operationName}): " +
                "hourlyRequests=${shortTermUsage.hourlyRequests}/${rateLimitInfo.requestsPerHour}, " +
                "minutelyRequests=${shortTermUsage.minutelyRequests}/${rateLimitInfo.requestsPerMinute}, " +
                "remainingCredits=$remainingCredits")

        // For analysis operations, check credits but skip rate limiting (no hourly/minutely limits)
        if (operationType == RateLimitOperationType.ANALYSIS) {
            // Check if user has remaining credits
            if (remainingCredits <= 0) {
                metricsService?.recordRateLimitCheck(userId, false, planType)
                return RateLimitStatus(
                    isAllowed = false,
                    remainingRequests = 0,
                    resetTimeHourly = now.plus(1.hours),
                    resetTimeMinutely = now.plus(1.minutes),
                    retryAfterSeconds = getSecondsUntilNextMonth().toInt(),
                    hasMonthlyCredits = false,
                    remainingCredits = 0,
                    resetTimeSeconds = getSecondsUntilNextMonth()
                )
            }

            // Allow analysis if user has credits (no rate limiting for analysis)
            metricsService?.recordRateLimitCheck(userId, true, planType)
            return RateLimitStatus(
                isAllowed = true,
                remainingRequests = remainingCredits,
                resetTimeHourly = shortTermUsage.hourlyTimestamp.plus(1.hours),
                resetTimeMinutely = shortTermUsage.minutelyTimestamp.plus(1.minutes),
                retryAfterSeconds = 0,
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits,
                resetTimeSeconds = 0
            )
        }

        // For screenshots operations, apply normal rate limiting
        // Check if user has remaining credits
        if (remainingCredits <= 0) {
            metricsService?.recordRateLimitCheck(userId, false, planType)
            return RateLimitStatus(
                isAllowed = false,
                remainingRequests = 0,
                resetTimeHourly = now.plus(1.hours),
                resetTimeMinutely = now.plus(1.minutes),
                retryAfterSeconds = 3600, // Try again in 1 hour
                hasMonthlyCredits = false,
                remainingCredits = 0,
                resetTimeSeconds = getSecondsUntilNextMonth()
            )
        }

        // Check hourly limit
        if (shortTermUsage.hourlyRequests >= rateLimitInfo.requestsPerHour) {
            val resetTime = shortTermUsage.hourlyTimestamp.plus(1.hours)
            val retryAfter = (resetTime.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000

            metricsService?.recordRateLimitCheck(userId, false, planType)
            return RateLimitStatus(
                isAllowed = false,
                remainingRequests = 0,
                resetTimeHourly = resetTime,
                resetTimeMinutely = resetTime,
                retryAfterSeconds = retryAfter.toInt(),
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits,
                resetTimeSeconds = retryAfter
            )
        }

        // Check minutely limit
        if (shortTermUsage.minutelyRequests >= rateLimitInfo.requestsPerMinute) {
            val resetTime = shortTermUsage.minutelyTimestamp.plus(1.minutes)
            val retryAfter = (resetTime.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000

            metricsService?.recordRateLimitCheck(userId, false, planType)
            return RateLimitStatus(
                isAllowed = false,
                remainingRequests = 0,
                resetTimeHourly = shortTermUsage.hourlyTimestamp.plus(1.hours),
                resetTimeMinutely = resetTime,
                retryAfterSeconds = retryAfter.toInt(),
                hasMonthlyCredits = true,
                remainingCredits = remainingCredits,
                resetTimeSeconds = retryAfter
            )
        }

        // Record successful rate limit check
        metricsService?.recordRateLimitCheck(userId, true, planType)

        return RateLimitStatus(
            isAllowed = true,
            remainingRequests = minOf(
                rateLimitInfo.requestsPerHour - shortTermUsage.hourlyRequests,
                rateLimitInfo.requestsPerMinute - shortTermUsage.minutelyRequests,
                remainingCredits
            ),
            resetTimeHourly = shortTermUsage.hourlyTimestamp.plus(1.hours),
            resetTimeMinutely = shortTermUsage.minutelyTimestamp.plus(1.minutes),
            retryAfterSeconds = 0,
            hasMonthlyCredits = true,
            remainingCredits = remainingCredits,
            resetTimeSeconds = 0
        )
    }

    private suspend fun getRateLimitInfo(planId: String?): RateLimitInfo {
        val cacheKey = planId ?: "free"
        
        // Check cache first
        val cached = planCache[cacheKey]
        if (cached != null && cached.second > Clock.System.now()) {
            return RateLimitInfo.fromPlan(cached.first)
        }

        // Get fresh plan data
        val plan = if (planId != null) {
            planRepository.findById(planId)
        } else null

        // Cache for 5 minutes
        if (plan != null) {
            planCache[cacheKey] = plan to Clock.System.now().plus(cacheExpirationMinutes.minutes)
        }

        return plan?.let { RateLimitInfo.fromPlan(it) } ?: RateLimitInfo(
            requestsPerMinute = 50,  // Increased for testing
            requestsPerHour = 200,   // Increased for testing
            concurrentRequests = 10,
            requestsPerDay = 1000
        )
    }

    private fun getSecondsUntilNextMonth(): Long {
        val now = Clock.System.now()
        val nextMonth = getNextMonthReset()
        return (nextMonth.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000
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
}
