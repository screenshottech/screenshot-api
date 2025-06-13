package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.usecases.admin.ScreenshotStats
import kotlinx.datetime.*
import kotlinx.datetime.Clock

object InMemoryDatabase {
    private val users = mutableMapOf<String, User>()
    private val apiKeys = mutableMapOf<String, ApiKey>()
    private val screenshots = mutableMapOf<String, ScreenshotJob>()
    private val subscriptions = mutableMapOf<String, Subscription>()
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
            roles = setOf(UserRole.USER), // Default user role
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

        val adminUser = freeUser.copy(
            id = "user_admin_1",
            email = "admin@test.com",
            name = "Admin User",
            roles = setOf(UserRole.ADMIN), // Admin role
            creditsRemaining = 1000000 // High credits for admin
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

        // Create initial subscriptions
        val freeSubscription = Subscription(
            id = "sub_free_1",
            userId = freeUser.id,
            planId = freePlan.id,
            billingCycle = BillingCycle.MONTHLY,
            status = SubscriptionStatus.ACTIVE,
            stripeSubscriptionId = null,
            stripeCustomerId = null,
            currentPeriodStart = Clock.System.now().minus(15, DateTimeUnit.DAY, TimeZone.UTC),
            currentPeriodEnd = Clock.System.now().plus(15, DateTimeUnit.DAY, TimeZone.UTC),
            createdAt = Clock.System.now().minus(15, DateTimeUnit.DAY, TimeZone.UTC),
            updatedAt = Clock.System.now()
        )

        val starterSubscription = Subscription(
            id = "sub_starter_1",
            userId = starterUser.id,
            planId = starterPlan.id,
            billingCycle = BillingCycle.MONTHLY,
            status = SubscriptionStatus.ACTIVE,
            stripeSubscriptionId = "sub_stripe_starter_123",
            stripeCustomerId = "cus_stripe_starter_123",
            currentPeriodStart = Clock.System.now().minus(10, DateTimeUnit.DAY, TimeZone.UTC),
            currentPeriodEnd = Clock.System.now().plus(20, DateTimeUnit.DAY, TimeZone.UTC),
            createdAt = Clock.System.now().minus(10, DateTimeUnit.DAY, TimeZone.UTC),
            updatedAt = Clock.System.now()
        )

        // Initialize database with default users, API keys, and subscriptions
        users[freeUser.id] = freeUser
        users[starterUser.id] = starterUser
        users[adminUser.id] = adminUser
        apiKeys[freeApiKey.id] = freeApiKey
        apiKeys[starterApiKey.id] = starterApiKey
        subscriptions[freeSubscription.id] = freeSubscription
        subscriptions[starterSubscription.id] = starterSubscription
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
        synchronized(subscriptions) { subscriptions.clear() }
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

    // === SUBSCRIPTION METHODS ===

    fun saveSubscription(subscription: Subscription): Subscription {
        synchronized(subscriptions) {
            subscriptions[subscription.id] = subscription
            return subscription
        }
    }

    fun findSubscriptionById(id: String): Subscription? {
        synchronized(subscriptions) {
            return subscriptions[id]
        }
    }

    fun findSubscriptionsByUserId(userId: String): List<Subscription> {
        synchronized(subscriptions) {
            return subscriptions.values
                .filter { it.userId == userId }
                .sortedByDescending { it.createdAt }
        }
    }

    fun findActiveSubscriptionByUserId(userId: String): Subscription? {
        synchronized(subscriptions) {
            return subscriptions.values
                .filter { it.userId == userId && it.status == SubscriptionStatus.ACTIVE }
                .maxByOrNull { it.createdAt }
        }
    }

    fun findSubscriptionByStripeSubscriptionId(stripeSubscriptionId: String): Subscription? {
        synchronized(subscriptions) {
            return subscriptions.values
                .firstOrNull { it.stripeSubscriptionId == stripeSubscriptionId }
        }
    }

    fun findSubscriptionsByStripeCustomerId(stripeCustomerId: String): List<Subscription> {
        synchronized(subscriptions) {
            return subscriptions.values
                .filter { it.stripeCustomerId == stripeCustomerId }
                .sortedByDescending { it.createdAt }
        }
    }

    fun findSubscriptionsByStatus(status: SubscriptionStatus): List<Subscription> {
        synchronized(subscriptions) {
            return subscriptions.values
                .filter { it.status == status }
                .sortedByDescending { it.createdAt }
        }
    }

    fun findSubscriptionsToRenewSoon(beforeDate: kotlinx.datetime.Instant): List<Subscription> {
        synchronized(subscriptions) {
            return subscriptions.values
                .filter { 
                    it.currentPeriodEnd <= beforeDate && 
                    it.status == SubscriptionStatus.ACTIVE 
                }
                .sortedBy { it.currentPeriodEnd }
        }
    }

    fun deleteSubscription(id: String) {
        synchronized(subscriptions) {
            subscriptions.remove(id)
        }
    }

    fun getAllSubscriptions(): List<Subscription> {
        synchronized(subscriptions) {
            return subscriptions.values
                .sortedByDescending { it.createdAt }
        }
    }

    fun countAllSubscriptions(): Long {
        synchronized(subscriptions) {
            return subscriptions.size.toLong()
        }
    }

    fun findSubscriptionsWithPagination(
        page: Int,
        limit: Int,
        searchQuery: String?,
        statusFilter: SubscriptionStatus?,
        planIdFilter: String?
    ): List<Subscription> {
        synchronized(subscriptions) {
            var filtered = subscriptions.values.asSequence()

            // Apply filters
            if (!searchQuery.isNullOrBlank()) {
                val query = searchQuery.lowercase()
                filtered = filtered.filter { subscription ->
                    // Search in user email/name by looking up the user
                    val user = findUser(subscription.userId)
                    val userMatch = user?.let { 
                        it.email.lowercase().contains(query) || 
                        it.name?.lowercase()?.contains(query) == true 
                    } ?: false
                    
                    // Search in subscription fields
                    userMatch ||
                    subscription.id.lowercase().contains(query) ||
                    subscription.planId.lowercase().contains(query) ||
                    subscription.stripeSubscriptionId?.lowercase()?.contains(query) == true
                }
            }

            if (statusFilter != null) {
                filtered = filtered.filter { it.status == statusFilter }
            }

            if (!planIdFilter.isNullOrBlank()) {
                filtered = filtered.filter { it.planId == planIdFilter }
            }

            // Sort by creation date (newest first)
            val sorted = filtered.sortedByDescending { it.createdAt }

            // Apply pagination
            val startIndex = (page - 1) * limit
            return sorted.drop(startIndex).take(limit).toList()
        }
    }

    fun countSubscriptionsWithFilters(
        searchQuery: String?,
        statusFilter: SubscriptionStatus?,
        planIdFilter: String?
    ): Long {
        synchronized(subscriptions) {
            var filtered = subscriptions.values.asSequence()

            // Apply same filters as pagination method
            if (!searchQuery.isNullOrBlank()) {
                val query = searchQuery.lowercase()
                filtered = filtered.filter { subscription ->
                    val user = findUser(subscription.userId)
                    val userMatch = user?.let { 
                        it.email.lowercase().contains(query) || 
                        it.name?.lowercase()?.contains(query) == true 
                    } ?: false
                    
                    userMatch ||
                    subscription.id.lowercase().contains(query) ||
                    subscription.planId.lowercase().contains(query) ||
                    subscription.stripeSubscriptionId?.lowercase()?.contains(query) == true
                }
            }

            if (statusFilter != null) {
                filtered = filtered.filter { it.status == statusFilter }
            }

            if (!planIdFilter.isNullOrBlank()) {
                filtered = filtered.filter { it.planId == planIdFilter }
            }

            return filtered.count().toLong()
        }
    }
}
