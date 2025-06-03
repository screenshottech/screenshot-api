package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.DailyUsage
import dev.screenshotapi.core.domain.entities.ShortTermUsage
import dev.screenshotapi.core.domain.entities.UserUsage
import dev.screenshotapi.core.domain.repositories.UsageRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.ports.output.CachePort
import dev.screenshotapi.core.ports.output.UsageTrackingPort
import dev.screenshotapi.core.ports.output.get
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Scalable implementation of usage tracking service with distributed cache support
 */
class UsageTrackingServiceImpl(
    private val userRepository: UserRepository,
    private val usageRepository: UsageRepository,
    private val shortTermCache: CachePort, // For minute/hour rate limiting data
    private val monthlyCache: CachePort     // For monthly usage data
) : UsageTrackingPort {

    companion object {
        private const val SHORT_TERM_PREFIX = "short_term:"
        private const val MONTHLY_PREFIX = "monthly:"
        private const val DAILY_PREFIX = "daily:"
        private const val CONCURRENT_PREFIX = "concurrent:"
    }

    override suspend fun trackUsage(userId: String, amount: Int): UserUsage {
        val now = Clock.System.now()

        // Update short-term counters
        updateShortTermUsage(userId, now)

        // Track daily usage for rate limiting
        trackDailyUsage(userId, amount)

        // Update monthly usage in database
        val currentMonth = getCurrentMonth()
        val updated = usageRepository.incrementUsage(userId, currentMonth, amount)

        // Update cache
        monthlyCache.put("$MONTHLY_PREFIX$userId:$currentMonth", updated, 30.minutes)
        return updated
    }

    override suspend fun getRemainingCredits(userId: String): Int {
        val usage = getUserUsage(userId, getCurrentMonth())
        return usage?.remainingCredits ?: 0
    }

    override suspend fun hasCredits(userId: String, amount: Int): Boolean {
        val usage = getUserUsage(userId, getCurrentMonth())
        return usage?.remainingCredits?.let { it >= amount } ?: false
    }

    override suspend fun getUserUsage(userId: String, month: String): UserUsage? {
        val cacheKey = "$MONTHLY_PREFIX$userId:$month"

        // Try cache first
        monthlyCache.get<UserUsage>(cacheKey)?.let { return it }

        // Fetch from database
        return try {
            val usage = usageRepository.findByUserAndMonth(userId, month)
                ?: createNewMonthlyUsage(userId, month)

            // Cache for 30 minutes
            monthlyCache.put(cacheKey, usage, 30.minutes)
            usage
        } catch (e: Exception) {
            // Fallback to default usage
            createNewMonthlyUsage(userId, month)
        }
    }

    override suspend fun getUserMonthlyStats(userId: String, year: Int): List<UserUsage> {
        return usageRepository.getUserMonthlyStats(userId, year)
    }

    /**
     * Get daily usage for rate limiting
     */
    suspend fun getDailyUsage(userId: String): DailyUsage {
        val today = getCurrentDate()
        val cacheKey = "$DAILY_PREFIX$userId:$today"

        // Try cache first
        shortTermCache.get<DailyUsage>(cacheKey)?.let { return it }

        // Calculate daily limit based on user's plan
        val user = userRepository.findById(userId)
        val dailyLimit = calculateDailyLimit(user?.planId)

        // Create new daily usage record
        val now = Clock.System.now()
        val dailyUsage = DailyUsage(
            userId = userId,
            date = today,
            requestsUsed = 0,
            dailyLimit = dailyLimit,
            lastRequestAt = now,
            createdAt = now,
            updatedAt = now
        )

        // Cache for remainder of day
        val secondsUntilMidnight = getSecondsUntilMidnight()
        shortTermCache.put(cacheKey, dailyUsage, secondsUntilMidnight.toInt().seconds)

        return dailyUsage
    }

    /**
     * Track daily usage
     */
    suspend fun trackDailyUsage(userId: String, amount: Int = 1): DailyUsage {
        val today = getCurrentDate()
        val cacheKey = "$DAILY_PREFIX$userId:$today"
        val now = Clock.System.now()

        val current = getDailyUsage(userId)
        val updated = current.copy(
            requestsUsed = current.requestsUsed + amount,
            lastRequestAt = now,
            updatedAt = now
        )

        // Cache for remainder of day
        val secondsUntilMidnight = getSecondsUntilMidnight()
        shortTermCache.put(cacheKey, updated, secondsUntilMidnight.toInt().seconds)

        return updated
    }

    /**
     * Check if user has remaining daily quota
     */
    suspend fun hasRemainingDailyQuota(userId: String): Boolean {
        val dailyUsage = getDailyUsage(userId)
        return dailyUsage.hasRemainingRequests
    }

    /**
     * Get short-term usage for rate limiting (scalable version)
     */
    fun getShortTermUsage(userId: String): ShortTermUsage {
        val now = Clock.System.now()
        val cacheKey = "$SHORT_TERM_PREFIX$userId"

        return runCatching {
            // Use coroutine blocking call since this is called from non-suspend context
            kotlinx.coroutines.runBlocking {
                shortTermCache.get<ShortTermUsage>(cacheKey)
            }
        }.getOrNull() ?: ShortTermUsage(
            userId = userId,
            hourlyRequests = 0,
            hourlyTimestamp = now,
            minutelyRequests = 0,
            minutelyTimestamp = now,
            concurrentRequests = 0
        )
    }

    /**
     * Update short-term usage counters with distributed cache
     */
    private suspend fun updateShortTermUsage(userId: String, now: Instant) {
        val cacheKey = "$SHORT_TERM_PREFIX$userId"
        val current = shortTermCache.get<ShortTermUsage>(cacheKey) ?: ShortTermUsage(
            userId = userId,
            hourlyRequests = 0,
            hourlyTimestamp = now,
            minutelyRequests = 0,
            minutelyTimestamp = now,
            concurrentRequests = 0
        )

        // Reset counters if time windows have passed
        val hoursPassed = now >= current.hourlyTimestamp.plus(1.hours)
        val minutesPassed = now >= current.minutelyTimestamp.plus(1.minutes)

        val updated = when {
            hoursPassed && minutesPassed -> ShortTermUsage(
                userId = userId,
                hourlyRequests = 1,
                hourlyTimestamp = now,
                minutelyRequests = 1,
                minutelyTimestamp = now,
                concurrentRequests = current.concurrentRequests
            )
            hoursPassed -> current.copy(
                hourlyRequests = 1,
                hourlyTimestamp = now,
                minutelyRequests = current.minutelyRequests + 1
            )
            minutesPassed -> current.copy(
                hourlyRequests = current.hourlyRequests + 1,
                minutelyRequests = 1,
                minutelyTimestamp = now
            )
            else -> current.copy(
                hourlyRequests = current.hourlyRequests + 1,
                minutelyRequests = current.minutelyRequests + 1
            )
        }

        // Cache for 1 hour (will auto-expire)
        shortTermCache.put(cacheKey, updated, 1.hours)
    }

    /**
     * Increment concurrent request counter with atomic operation
     */
    fun incrementConcurrentRequests(userId: String) {
        runCatching {
            kotlinx.coroutines.runBlocking {
                val concurrentKey = "$CONCURRENT_PREFIX$userId"
                shortTermCache.increment(concurrentKey, 1)
                // Expire after 10 minutes of inactivity
                shortTermCache.expire(concurrentKey, 10.minutes)
            }
        }
    }

    /**
     * Decrement concurrent request counter with atomic operation
     */
    fun decrementConcurrentRequests(userId: String) {
        runCatching {
            kotlinx.coroutines.runBlocking {
                val concurrentKey = "$CONCURRENT_PREFIX$userId"
                val current = shortTermCache.increment(concurrentKey, -1)

                // Remove key if count reaches 0
                if (current <= 0) {
                    shortTermCache.remove(concurrentKey)
                }
            }
        }
    }

    /**
     * Get current concurrent requests count
     */
    fun getConcurrentRequestsCount(userId: String): Int {
        return runCatching {
            kotlinx.coroutines.runBlocking {
                val concurrentKey = "$CONCURRENT_PREFIX$userId"
                shortTermCache.get<Long>(concurrentKey)?.toInt() ?: 0
            }
        }.getOrElse { 0 }
    }

    /**
     * Create new monthly usage record
     */
    private suspend fun createNewMonthlyUsage(userId: String, month: String): UserUsage {
        val now = Clock.System.now()
        val user = userRepository.findById(userId)
        val planCredits = user?.let {
            // Get plan credits from user's plan
            // This would need to be implemented based on your plan structure
            1000 // Default for now
        } ?: 300 // Free plan default

        return UserUsage(
            userId = userId,
            month = month,
            totalRequests = 0,
            planCreditsLimit = planCredits,
            remainingCredits = planCredits,
            lastRequestAt = now,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Get current month in YYYY-MM format
     */
    private fun getCurrentMonth(): String {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.UTC)
        return "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}"
    }

    /**
     * Get current date in YYYY-MM-DD format
     */
    private fun getCurrentDate(): String {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.UTC)
        return "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')}"
    }

    /**
     * Calculate daily limit based on plan
     */
    private fun calculateDailyLimit(planId: String?): Int {
        // Daily limits based on plan tier from analysis document
        return when (planId) {
            "plan_free" -> 50
            "plan_starter_monthly", "plan_starter_annual" -> 400
            "plan_pro_monthly", "plan_pro_annual" -> 2000
            "plan_enterprise_monthly", "plan_enterprise_annual" -> 10000
            else -> 50 // Default to free plan
        }
    }

    /**
     * Get seconds until midnight UTC
     */
    private fun getSecondsUntilMidnight(): Long {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.UTC)
        val tomorrow = LocalDateTime(localDateTime.year, localDateTime.month, localDateTime.dayOfMonth + 1, 0, 0, 0)
        val tomorrowInstant = tomorrow.toInstant(TimeZone.UTC)
        return (tomorrowInstant.epochSeconds - now.epochSeconds).coerceAtLeast(0)
    }

    /**
     * Clear all caches (useful for testing)
     */
    fun clearCaches() {
        runCatching {
            kotlinx.coroutines.runBlocking {
                shortTermCache.clear()
                monthlyCache.clear()
            }
        }
    }
}
