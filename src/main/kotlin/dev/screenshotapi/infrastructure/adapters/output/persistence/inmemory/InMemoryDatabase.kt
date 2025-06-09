package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.usecases.admin.ScreenshotStats
import kotlinx.datetime.*
import kotlinx.datetime.Clock

object InMemoryDatabase {
    private val users = mutableMapOf<String, User>()
    private val apiKeys = mutableMapOf<String, ApiKey>()
    private val screenshots = mutableMapOf<String, ScreenshotJob>()
    private val queue = mutableListOf<ScreenshotJob>()

    init {
        // Create initial plans
        val freePlan = Plan(
            id = "plan_free",
            name = "Free Plan",
            description = "Free plan with basic limits",
            creditsPerMonth = 300,
            priceCentsMonthly = 0,
            priceCentsAnnual = 0,
            billingCycle = "monthly",
            currency = "USD",
            features = listOf("Basic screenshots", "PNG/JPEG formats"),
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        val starterPlan = freePlan.copy(
            id = "plan_starter_monthly",
            name = "Starter Monthly",
            description = "12% cheaper than competitors + OCR included",
            creditsPerMonth = 2000,
            priceCentsMonthly = 1499
        )

        // Create initial users
        val freeUser = User(
            id = "user_free_1",
            email = "free@test.com",
            name = "Free User",
            passwordHash = "hash123",
            status = UserStatus.ACTIVE,
            planId = freePlan.id,
            planName = freePlan.name,
            creditsRemaining = freePlan.creditsPerMonth,
            stripeCustomerId = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        val starterUser = freeUser.copy(
            id = "user_starter_1",
            email = "starter@test.com",
            name = "Starter User",
            planId = starterPlan.id,
            planName = starterPlan.name,
            creditsRemaining = starterPlan.creditsPerMonth
        )

        // Create initial API keys
        val freeApiKey = ApiKey(
            id = "key_free_1",
            userId = freeUser.id,
            name = "Test Free Key",
            keyHash = "sk_test_free_123".hashCode().toString(),
            keyPrefix = "sk_test",
            permissions = setOf(Permission.SCREENSHOT_CREATE),
            rateLimit = 10,
            usageCount = 0,
            isActive = true,
            lastUsed = null,
            expiresAt = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC),
            createdAt = Clock.System.now()
        )

        val starterApiKey = ApiKey(
            id = "key_starter_1",
            userId = starterUser.id,
            name = "Test Starter Key",
            keyHash = "sk_test_starter_123".hashCode().toString(),
            keyPrefix = "sk_test",
            permissions = setOf(Permission.SCREENSHOT_CREATE),
            rateLimit = 60,
            usageCount = 0,
            isActive = true,
            lastUsed = null,
            expiresAt = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC),
            createdAt = Clock.System.now()
        )

        // Initialize database with default users and API keys
        users[freeUser.id] = freeUser
        users[starterUser.id] = starterUser
        apiKeys[freeApiKey.id] = freeApiKey
        apiKeys[starterApiKey.id] = starterApiKey
    }

    // Thread-safe operations
    fun saveUser(user: User): User {
        synchronized(users) {
            users[user.id] = user
            return user
        }
    }

    fun findUser(id: String): User? {
        synchronized(users) {
            return users[id]
        }
    }

    fun findUserByEmail(email: String): User? {
        synchronized(users) {
            return users.values.find { it.email == email }
        }
    }

    fun saveApiKey(apiKey: ApiKey): ApiKey {
        synchronized(apiKeys) {
            apiKeys[apiKey.id] = apiKey
            return apiKey
        }
    }

    fun findApiKey(id: String): ApiKey? {
        synchronized(apiKeys) {
            return apiKeys[id]
        }
    }

    fun findApiKeyByHash(hash: String): ApiKey? {
        synchronized(apiKeys) {
            return apiKeys.values.find { it.keyHash == hash }
        }
    }

    fun findApiKeysByUserId(userId: String): List<ApiKey> {
        synchronized(apiKeys) {
            return apiKeys.values.filter { it.userId == userId }
        }
    }

    fun updateApiKey(apiKey: ApiKey): ApiKey {
        synchronized(apiKeys) {
            apiKeys[apiKey.id] = apiKey
            return apiKey
        }
    }

    fun deleteApiKey(id: String): Boolean {
        synchronized(apiKeys) {
            return apiKeys.remove(id) != null
        }
    }

    fun saveScreenshot(job: ScreenshotJob): ScreenshotJob {
        synchronized(screenshots) {
            screenshots[job.id] = job
            return job
        }
    }

    fun findScreenshot(id: String): ScreenshotJob? {
        synchronized(screenshots) {
            return screenshots[id]
        }
    }

    // Queue operations
    fun enqueueJob(job: ScreenshotJob) {
        synchronized(queue) {
            queue.add(job)
        }
    }

    fun dequeueJob(): ScreenshotJob? {
        synchronized(queue) {
            return if (queue.isNotEmpty()) queue.removeAt(0) else null
        }
    }

    fun queueSize(): Long {
        synchronized(queue) {
            return queue.size.toLong()
        }
    }

    fun clearAll() {
        synchronized(users) { users.clear() }
        synchronized(apiKeys) { apiKeys.clear() }
        synchronized(screenshots) { screenshots.clear() }
        synchronized(queue) { queue.clear() }
    }

    fun getAllUsers(): List<User> {
        synchronized(users) {
            return users.values.toList()
        }
    }

    fun getAllScreenshots(): List<ScreenshotJob> {
        synchronized(screenshots) {
            return screenshots.values.toList()
        }
    }

    fun findScreenshotsByUser(userId: String, page: Int = 1, limit: Int = 20): List<ScreenshotJob> {
        synchronized(screenshots) {
            val userScreenshots = screenshots.values
                .filter { it.userId == userId }
                .sortedByDescending { it.createdAt }

            val startIndex = (page - 1) * limit
            return userScreenshots.drop(startIndex).take(limit)
        }
    }

    fun countScreenshotsByUser(userId: String): Long {
        synchronized(screenshots) {
            return screenshots.values.count { it.userId == userId }.toLong()
        }
    }

    fun findScreenshotsByUserAndStatus(userId: String, status: ScreenshotStatus, page: Int = 1, limit: Int = 20): List<ScreenshotJob> {
        synchronized(screenshots) {
            val userScreenshots = screenshots.values
                .filter { it.userId == userId && it.status == status }
                .sortedByDescending { it.createdAt }

            val startIndex = (page - 1) * limit
            return userScreenshots.drop(startIndex).take(limit)
        }
    }

    fun countScreenshotsByUserAndStatus(userId: String, status: ScreenshotStatus): Long {
        synchronized(screenshots) {
            return screenshots.values.count { it.userId == userId && it.status == status }.toLong()
        }
    }

    fun findPendingScreenshots(): List<ScreenshotJob> {
        synchronized(screenshots) {
            return screenshots.values.filter {
                it.status == ScreenshotStatus.QUEUED || it.status == ScreenshotStatus.PROCESSING
            }
        }
    }

    fun findScreenshotsByStatus(status: ScreenshotStatus): List<ScreenshotJob> {
        synchronized(screenshots) {
            return screenshots.values.filter { it.status == status }
        }
    }

    fun findScreenshotsByDateRange(
        startDate: kotlinx.datetime.Instant,
        endDate: kotlinx.datetime.Instant
    ): List<ScreenshotJob> {
        synchronized(screenshots) {
            return screenshots.values.filter {
                it.createdAt >= startDate && it.createdAt <= endDate
            }
        }
    }

    fun getScreenshotStats(): ScreenshotStats {
        synchronized(screenshots) {
            val allScreenshots = screenshots.values
            val total = allScreenshots.size.toLong()
            val completed = allScreenshots.count { it.status == ScreenshotStatus.COMPLETED }.toLong()
            val failed = allScreenshots.count { it.status == ScreenshotStatus.FAILED }.toLong()
            val queued = allScreenshots.count { it.status == ScreenshotStatus.QUEUED }.toLong()
            val processing = allScreenshots.count { it.status == ScreenshotStatus.PROCESSING }.toLong()

            val completedJobs = allScreenshots.filter { it.status == ScreenshotStatus.COMPLETED }
            val avgProcessingTime = if (completedJobs.isNotEmpty()) {
                completedJobs.mapNotNull { it.processingTimeMs }.average().toLong()
            } else 0L

            val successRate = if (total > 0) (completed.toDouble() / total.toDouble()) else 0.0

            return ScreenshotStats(
                total = total,
                completed = completed,
                failed = failed,
                queued = queued,
                processing = processing,
                successRate = successRate,
                averageProcessingTime = avgProcessingTime,
                byFormat = allScreenshots.groupBy { it.request.format.name }
                    .mapValues { it.value.size.toLong() },
                byStatus = allScreenshots.groupBy { it.status.name }
                    .mapValues { it.value.size.toLong() }
            )
        }
    }

    // === HELPER METHODS ===

    fun clearScreenshots() {
        synchronized(screenshots) {
            screenshots.clear()
        }
    }

    fun getScreenshotCount(): Int {
        synchronized(screenshots) {
            return screenshots.size
        }
    }

    fun findScreenshotsByIds(ids: List<String>, userId: String): List<ScreenshotJob> {
        synchronized(screenshots) {
            return screenshots.values.filter { job ->
                ids.contains(job.id) && job.userId == userId
            }
        }
    }
}
