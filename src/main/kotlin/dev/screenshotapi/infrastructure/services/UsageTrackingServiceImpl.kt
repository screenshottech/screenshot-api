package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.DailyUsage
import dev.screenshotapi.core.domain.entities.ShortTermUsage
import dev.screenshotapi.core.domain.entities.UserUsage
import dev.screenshotapi.core.domain.repositories.UsageRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.services.RateLimitOperationType
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
        private const val SHORT_TERM_PREFIX = "short_term"
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

    override suspend fun getDailyUsage(userId: String): DailyUsage {
        val today = getCurrentDate()
        val cacheKey = "$DAILY_PREFIX$userId:$today"

        // Try cache first
        shortTermCache.get<DailyUsage>(cacheKey)?.let { return it }

        // Create new daily usage record
        val now = Clock.System.now()
        val dailyUsage = DailyUsage(
            userId = userId,
            date = getCurrentDate(),
            requestsUsed = 0,
            dailyLimit = 1000, // Default daily limit
            lastRequestAt = now,
            createdAt = now,
            updatedAt = now
        )

        // Cache for remainder of day
        val secondsUntilMidnight = getSecondsUntilMidnight()
        shortTermCache.put(cacheKey, dailyUsage, secondsUntilMidnight.toInt().seconds)

        return dailyUsage
    }

    override suspend fun trackDailyUsage(userId: String, amount: Int): DailyUsage {
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

    override suspend fun hasRemainingDailyQuota(userId: String): Boolean {
        val dailyUsage = getDailyUsage(userId)
        return dailyUsage.hasRemainingRequests
    }

    override fun getShortTermUsage(userId: String): ShortTermUsage {
        return getShortTermUsage(userId, RateLimitOperationType.SCREENSHOTS)
    }

    override fun getShortTermUsage(userId: String, operationType: RateLimitOperationType): ShortTermUsage {
        val now = Clock.System.now()
        val cacheKey = "$SHORT_TERM_PREFIX:${operationType.operationName}:$userId"

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
            concurrentRequests = getConcurrentRequestsCount(userId)
        )
    }

    override suspend fun updateShortTermUsage(userId: String, now: Instant) {
        updateShortTermUsage(userId, now, RateLimitOperationType.SCREENSHOTS)
    }

    override suspend fun updateShortTermUsage(userId: String, now: Instant, operationType: RateLimitOperationType) {
        val cacheKey = "$SHORT_TERM_PREFIX:${operationType.operationName}:$userId"
        val current = shortTermCache.get<ShortTermUsage>(cacheKey) ?: ShortTermUsage(
            userId = userId,
            hourlyRequests = 0,
            hourlyTimestamp = now,
            minutelyRequests = 0,
            minutelyTimestamp = now,
            concurrentRequests = getConcurrentRequestsCount(userId)
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

        val logger = org.slf4j.LoggerFactory.getLogger(this::class.java)
        logger.info("Short-term usage updated for user $userId (${operationType.operationName}): " +
                "before=[hourly=${current.hourlyRequests}, minutely=${current.minutelyRequests}] " +
                "after=[hourly=${updated.hourlyRequests}, minutely=${updated.minutelyRequests}] " +
                "hoursPassed=$hoursPassed, minutesPassed=$minutesPassed")

        // Cache for 1 hour
        shortTermCache.put(cacheKey, updated, 1.hours)
    }

    override fun getCurrentMonth(): String {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.UTC)
        return "${local.year}-${local.monthNumber.toString().padStart(2, '0')}"
    }

    private fun getCurrentDate(): String {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.UTC)
        return "${local.year}-${local.monthNumber.toString().padStart(2, '0')}-${local.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun getSecondsUntilMidnight(): Long {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.UTC)
        val tomorrow = LocalDate(local.year, local.monthNumber, local.dayOfMonth).plus(DatePeriod(days = 1))
        val midnight = LocalDateTime(tomorrow.year, tomorrow.month, tomorrow.dayOfMonth, 0, 0)
        return (midnight.toInstant(TimeZone.UTC).toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000
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

    private suspend fun createNewMonthlyUsage(userId: String, month: String): UserUsage {
        val user = userRepository.findById(userId)
        val userWithDetails = user?.let { userRepository.findWithDetails(userId) }
        val planCredits = userWithDetails?.plan?.creditsPerMonth ?: 100 // Default to 100 credits for free plan
        val now = Clock.System.now()
        
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
}
