package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.entities.*
import kotlinx.datetime.Instant

// === LIST USERS ===
data class ListUsersRequest(
    val page: Int = 1,
    val limit: Int = 20,
    val searchQuery: String? = null,
    val statusFilter: UserStatus? = null
)

data class ListUsersResponse(
    val users: List<UserSummary>,
    val page: Int,
    val limit: Int,
    val total: Long
)

data class UserSummary(
    val id: String,
    val email: String,
    val name: String?,
    val status: UserStatus,
    val roles: List<String>,
    val plan: PlanInfo,
    val creditsRemaining: Int,
    val totalScreenshots: Long,
    val lastActivity: Instant?,
    val createdAt: Instant
)

// === GET USER DETAILS ===
data class GetUserDetailsRequest(
    val userId: String,
    val includeActivity: Boolean = false,
    val includeApiKeys: Boolean = false
)

data class GetUserDetailsResponse(
    val user: UserDetail,
    val activity: List<UserActivity>? = null,
    val apiKeys: List<ApiKeyDetail>? = null
)

data class UserDetail(
    val id: String,
    val email: String,
    val name: String?,
    val status: UserStatus,
    val roles: List<String>,
    val plan: PlanInfo,
    val creditsRemaining: Int,
    val totalScreenshots: Long,
    val successfulScreenshots: Long,
    val failedScreenshots: Long,
    val totalCreditsUsed: Long,
    val lastActivity: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val stripeCustomerId: String?
)

data class PlanInfo(
    val id: String,
    val name: String,
    val creditsPerMonth: Int,
    val priceCents: Int
)

data class ApiKeyDetail(
    val id: String,
    val name: String,
    val permissions: Set<Permission>,
    val isActive: Boolean,
    val lastUsed: Instant?,
    val createdAt: Instant
)

// === UPDATE USER STATUS ===
data class UpdateUserStatusRequest(
    val userId: String,
    val status: UserStatus,
    val reason: String? = null,
    val adminUserId: String
)

data class UpdateUserStatusResponse(
    val userId: String,
    val status: UserStatus,
    val updatedAt: Instant,
    val updatedBy: String
)

// === GET USER ACTIVITY ===
data class GetUserActivityRequest(
    val userId: String,
    val days: Int = 30,
    val limit: Int = 100
)

data class GetUserActivityResponse(
    val userId: String,
    val days: Int,
    val activities: List<UserActivity>
)

data class UserActivity(
    val id: String,
    val userId: String,
    val type: UserActivityType,
    val description: String,
    val metadata: Map<String, String>? = null,
    val timestamp: Instant
)

// === GET SYSTEM STATS ===
data class GetSystemStatsRequest(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val breakdown: StatsBreakdown = StatsBreakdown.DAILY
)

data class GetSystemStatsResponse(
    val overview: SystemOverview,
    val screenshots: ScreenshotStats,
    val users: UserStats,
    val performance: PerformanceStats,
    val breakdown: List<StatsBreakdownItem>
)

data class SystemOverview(
    val totalUsers: Long,
    val activeUsers: Long,
    val totalScreenshots: Long,
    val screenshotsToday: Long,
    val successRate: Double,
    val averageProcessingTime: Long,
    val queueSize: Long,
    val activeWorkers: Int
)

data class ScreenshotStats(
    val total: Long,
    val completed: Long,
    val failed: Long,
    val queued: Long,
    val processing: Long,
    val successRate: Double,
    val averageProcessingTime: Long,
    val byFormat: Map<String, Long>,
    val byStatus: Map<String, Long>
)

data class UserStats(
    val total: Long,
    val active: Long,
    val suspended: Long,
    val newToday: Long,
    val newThisWeek: Long,
    val newThisMonth: Long,
    val topUsers: List<TopUser>
)

data class TopUser(
    val userId: String,
    val email: String,
    val name: String?,
    val screenshotCount: Long,
    val creditsUsed: Long
)

data class PerformanceStats(
    val averageResponseTime: Long,
    val p95ResponseTime: Long,
    val p99ResponseTime: Long,
    val errorRate: Double,
    val throughput: Double,
    val memoryUsage: MemoryUsage,
    val workerUtilization: Double
)

data class MemoryUsage(
    val used: Long,
    val total: Long,
    val percentage: Int
)

data class StatsBreakdownItem(
    val period: String,
    val screenshots: Long,
    val users: Long,
    val errors: Long,
    val averageProcessingTime: Long?
)

// === GET SCREENSHOT STATS ===
data class GetScreenshotStatsRequest(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val groupBy: StatsGroupBy = StatsGroupBy.DAY
)

data class GetScreenshotStatsResponse(
    val totalScreenshots: Long,
    val period: StatsPeriod,
    val groupBy: StatsGroupBy,
    val data: List<ScreenshotStatItem>
)

data class ScreenshotStatItem(
    val period: String,
    val count: Long,
    val successful: Long,
    val failed: Long,
    val averageProcessingTime: Long?,
    val formats: Map<String, Long>? = null
)

// === PROVISION SUBSCRIPTION CREDITS (ADMIN) ===
data class ProvisionSubscriptionCreditsAdminRequest(
    val subscriptionId: String,
    val reason: String = "admin_manual_provision",
    val adminUserId: String
)

data class ProvisionSubscriptionCreditsAdminResponse(
    val subscriptionId: String,
    val userId: String,
    val planId: String,
    val creditsProvisioned: Int,
    val newCreditBalance: Int,
    val userPlanUpdated: Boolean,
    val processed: Boolean,
    val message: String,
    val adminUserId: String,
    val executedAt: Instant
)

// === SYNCHRONIZE USER PLAN (ADMIN) ===
data class SynchronizeUserPlanAdminRequest(
    val userId: String,
    val subscriptionId: String? = null, // If null, finds active subscription
    val adminUserId: String,
    val reason: String = "admin_manual_sync"
)

data class SynchronizeUserPlanAdminResponse(
    val userId: String,
    val subscriptionId: String?,
    val previousPlanId: String,
    val newPlanId: String,
    val planUpdated: Boolean,
    val creditsAdjusted: Boolean,
    val newCreditBalance: Int,
    val message: String,
    val adminUserId: String,
    val executedAt: Instant
)

// === LIST SUBSCRIPTIONS (ADMIN) ===
data class ListSubscriptionsRequest(
    val page: Int = 1,
    val limit: Int = 20,
    val searchQuery: String? = null,
    val statusFilter: SubscriptionStatus? = null,
    val planIdFilter: String? = null
)

data class ListSubscriptionsResponse(
    val subscriptions: List<SubscriptionSummary>,
    val page: Int,
    val limit: Int,
    val total: Long
)

data class SubscriptionSummary(
    val id: String,
    val userId: String,
    val userEmail: String,
    val userName: String?,
    val planId: String,
    val planName: String,
    val status: SubscriptionStatus,
    val stripeSubscriptionId: String?,
    val stripeCustomerId: String?,
    val currentPeriodStart: Instant?,
    val currentPeriodEnd: Instant?,
    val creditsUsed: Int,
    val creditsLimit: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)
