package dev.screenshotapi.infrastructure.adapters.input.rest.dto

import dev.screenshotapi.core.usecases.admin.*
import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserStatusRequestDto(
    val status: String,
    val reason: String? = null
)

// Response DTOs
@Serializable
data class UsersListResponseDto(
    val users: List<UserSummaryDto>,
    val pagination: PaginationDto
)

@Serializable
data class UserSummaryDto(
    val id: String,
    val email: String,
    val name: String?,
    val status: String,
    val plan: PlanDetailDto,
    val creditsRemaining: Int,
    val totalScreenshots: Long,
    val lastActivity: String?,
    val createdAt: String
)

@Serializable
data class UserDetailsResponseDto(
    val user: UserDetailDto,
    val activity: List<UserActivityDto>? = null,
    val apiKeys: List<ApiKeySummaryDto>? = null
)

@Serializable
data class UserDetailDto(
    val id: String,
    val email: String,
    val name: String?,
    val status: String,
    val roles: List<String>,
    val plan: PlanDetailDto,
    val creditsRemaining: Int,
    val totalScreenshots: Long,
    val successfulScreenshots: Long,
    val failedScreenshots: Long,
    val totalCreditsUsed: Long,
    val lastActivity: String?,
    val createdAt: String,
    val updatedAt: String,
    val stripeCustomerId: String?
)

@Serializable
data class PlanDetailDto(
    val id: String,
    val name: String,
    val creditsPerMonth: Int,
    val priceCents: Int
)

@Serializable
data class UserActivityDto(
    val id: String,
    val type: String, // SCREENSHOT_CREATED, SCREENSHOT_COMPLETED, API_KEY_CREATED, etc.
    val description: String,
    val metadata: Map<String, String>? = null,
    val timestamp: String
)

@Serializable
data class ApiKeySummaryDto(
    val id: String,
    val name: String,
    val permissions: List<String>,
    val isActive: Boolean,
    val lastUsed: String?,
    val createdAt: String
)

@Serializable
data class SystemStatsResponseDto(
    val overview: SystemOverviewDto,
    val screenshots: ScreenshotStatsDto,
    val users: UserStatsDto,
    val performance: PerformanceStatsDto,
    val breakdown: List<StatsBreakdownDto>
)

@Serializable
data class SystemOverviewDto(
    val totalUsers: Long,
    val activeUsers: Long,
    val totalScreenshots: Long,
    val screenshotsToday: Long,
    val successRate: Double,
    val averageProcessingTime: Long,
    val queueSize: Long,
    val activeWorkers: Int
)

@Serializable
data class ScreenshotStatsDto(
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

@Serializable
data class UserStatsDto(
    val total: Long,
    val active: Long,
    val suspended: Long,
    val newToday: Long,
    val newThisWeek: Long,
    val newThisMonth: Long,
    val topUsers: List<TopUserDto>
)

@Serializable
data class TopUserDto(
    val userId: String,
    val email: String,
    val name: String?,
    val screenshotCount: Long,
    val creditsUsed: Long
)

@Serializable
data class PerformanceStatsDto(
    val averageResponseTime: Long,
    val p95ResponseTime: Long,
    val p99ResponseTime: Long,
    val errorRate: Double,
    val throughput: Double, // requests per minute
    val memoryUsage: MemoryUsageDto,
    val workerUtilization: Double
)

@Serializable
data class MemoryUsageDto(
    val used: Long,
    val total: Long,
    val percentage: Int
)

@Serializable
data class StatsBreakdownDto(
    val period: String, // "2024-01-15", "2024-01-15T14:00:00Z", etc.
    val screenshots: Long,
    val users: Long,
    val errors: Long,
    val averageProcessingTime: Long?
)

@Serializable
data class ScreenshotStatsResponseDto(
    val totalScreenshots: Long,
    val period: String,
    val groupBy: String,
    val data: List<ScreenshotStatDataDto>
)

@Serializable
data class ScreenshotStatDataDto(
    val period: String,
    val count: Long,
    val successful: Long,
    val failed: Long,
    val averageProcessingTime: Long?,
    val formats: Map<String, Long>? = null
)

fun ListUsersResponse.toDto() = UsersListResponseDto(
    users = users.map { user ->
        UserSummaryDto(
            id = user.id,
            email = user.email,
            name = user.name,
            status = user.status.name.lowercase(),
            plan = PlanDetailDto(
                id = user.plan.id,
                name = user.plan.name,
                creditsPerMonth = user.plan.creditsPerMonth,
                priceCents = user.plan.priceCents
            ),
            creditsRemaining = user.creditsRemaining,
            totalScreenshots = user.totalScreenshots,
            lastActivity = user.lastActivity?.toString(),
            createdAt = user.createdAt.toString()
        )
    },
    pagination = PaginationDto(
        page = page,
        limit = limit,
        total = total,
        totalPages = ((total + limit - 1) / limit).toInt(),
        hasNext = page * limit < total,
        hasPrevious = page > 1
    )
)

fun GetUserDetailsResponse.toDto() = UserDetailsResponseDto(
    user = UserDetailDto(
        id = user.id,
        email = user.email,
        name = user.name,
        status = user.status.name.lowercase(),
        roles = user.roles,
        plan = PlanDetailDto(
            id = user.plan.id,
            name = user.plan.name,
            creditsPerMonth = user.plan.creditsPerMonth,
            priceCents = user.plan.priceCents
        ),
        creditsRemaining = user.creditsRemaining,
        totalScreenshots = user.totalScreenshots,
        successfulScreenshots = user.successfulScreenshots,
        failedScreenshots = user.failedScreenshots,
        totalCreditsUsed = user.totalCreditsUsed,
        lastActivity = user.lastActivity?.toString(),
        createdAt = user.createdAt.toString(),
        updatedAt = user.updatedAt.toString(),
        stripeCustomerId = user.stripeCustomerId
    ),
    activity = activity?.map { activity ->
        UserActivityDto(
            id = activity.id,
            type = activity.type.name,
            description = activity.description,
            metadata = activity.metadata,
            timestamp = activity.timestamp.toString()
        )
    },
    apiKeys = apiKeys?.map { apiKey ->
        ApiKeySummaryDto(
            id = apiKey.id,
            name = apiKey.name,
            permissions = apiKey.permissions.map { it.name.lowercase() },
            isActive = apiKey.isActive,
            lastUsed = apiKey.lastUsed?.toString(),
            createdAt = apiKey.createdAt.toString()
        )
    }
)

fun GetSystemStatsResponse.toDto() = SystemStatsResponseDto(
    overview = SystemOverviewDto(
        totalUsers = overview.totalUsers,
        activeUsers = overview.activeUsers,
        totalScreenshots = overview.totalScreenshots,
        screenshotsToday = overview.screenshotsToday,
        successRate = overview.successRate,
        averageProcessingTime = overview.averageProcessingTime,
        queueSize = overview.queueSize,
        activeWorkers = overview.activeWorkers
    ),
    screenshots = ScreenshotStatsDto(
        total = screenshots.total,
        completed = screenshots.completed,
        failed = screenshots.failed,
        queued = screenshots.queued,
        processing = screenshots.processing,
        successRate = screenshots.successRate,
        averageProcessingTime = screenshots.averageProcessingTime,
        byFormat = screenshots.byFormat,
        byStatus = screenshots.byStatus
    ),
    users = UserStatsDto(
        total = users.total,
        active = users.active,
        suspended = users.suspended,
        newToday = users.newToday,
        newThisWeek = users.newThisWeek,
        newThisMonth = users.newThisMonth,
        topUsers = users.topUsers.map { topUser ->
            TopUserDto(
                userId = topUser.userId,
                email = topUser.email,
                name = topUser.name,
                screenshotCount = topUser.screenshotCount,
                creditsUsed = topUser.creditsUsed
            )
        }
    ),
    performance = PerformanceStatsDto(
        averageResponseTime = performance.averageResponseTime,
        p95ResponseTime = performance.p95ResponseTime,
        p99ResponseTime = performance.p99ResponseTime,
        errorRate = performance.errorRate,
        throughput = performance.throughput,
        memoryUsage = MemoryUsageDto(
            used = performance.memoryUsage.used,
            total = performance.memoryUsage.total,
            percentage = performance.memoryUsage.percentage
        ),
        workerUtilization = performance.workerUtilization
    ),
    breakdown = breakdown.map { item ->
        StatsBreakdownDto(
            period = item.period,
            screenshots = item.screenshots,
            users = item.users,
            errors = item.errors,
            averageProcessingTime = item.averageProcessingTime
        )
    }
)

fun GetScreenshotStatsResponse.toDto() = ScreenshotStatsResponseDto(
    totalScreenshots = totalScreenshots,
    period = period.name.lowercase(),
    groupBy = groupBy.name.lowercase(),
    data = data.map { item ->
        ScreenshotStatDataDto(
            period = item.period,
            count = item.count,
            successful = item.successful,
            failed = item.failed,
            averageProcessingTime = item.averageProcessingTime,
            formats = item.formats
        )
    }
)

fun UpdateUserStatusResponse.toDto() = mapOf(
    "userId" to userId,
    "status" to status.name.lowercase(),
    "updatedAt" to updatedAt.toString(),
    "message" to "User status updated successfully"
)

fun GetUserActivityResponse.toDto() = mapOf(
    "userId" to userId,
    "period" to "$days days",
    "activities" to activities.map { activity ->
        UserActivityDto(
            id = activity.id,
            type = activity.type.name,
            description = activity.description,
            metadata = activity.metadata,
            timestamp = activity.timestamp.toString()
        )
    }
)

// Billing & Plan Synchronization DTOs - Following Hexagonal Architecture Principles
@Serializable
data class SynchronizeUserPlanResponseDto(
    val userId: String,
    val subscriptionId: String,
    val previousPlanId: String,
    val newPlanId: String,
    val planUpdated: Boolean,
    val creditsAdjusted: Boolean,
    val newCreditBalance: Int,
    val message: String,
    val adminUserId: String,
    val executedAt: String
)

@Serializable
data class ProvisionCreditsResponseDto(
    val subscriptionId: String,
    val userId: String,
    val planId: String,
    val creditsProvisioned: Int,
    val newCreditBalance: Int,
    val userPlanUpdated: Boolean,
    val processed: Boolean,
    val message: String,
    val adminUserId: String,
    val executedAt: String
)

// Extension functions for domain -> DTO mapping (maintaining clean architecture)
fun SynchronizeUserPlanAdminResponse.toDto() = SynchronizeUserPlanResponseDto(
    userId = userId,
    subscriptionId = subscriptionId ?: "",
    previousPlanId = previousPlanId,
    newPlanId = newPlanId,
    planUpdated = planUpdated,
    creditsAdjusted = creditsAdjusted,
    newCreditBalance = newCreditBalance,
    message = message,
    adminUserId = adminUserId,
    executedAt = executedAt.toString()
)

fun ProvisionSubscriptionCreditsAdminResponse.toDto() = ProvisionCreditsResponseDto(
    subscriptionId = subscriptionId,
    userId = userId,
    planId = planId,
    creditsProvisioned = creditsProvisioned,
    newCreditBalance = newCreditBalance,
    userPlanUpdated = userPlanUpdated,
    processed = processed,
    message = message,
    adminUserId = adminUserId,
    executedAt = executedAt.toString()
)

// Subscription Management DTOs
@Serializable
data class SubscriptionsListResponseDto(
    val subscriptions: List<SubscriptionSummaryDto>,
    val pagination: PaginationDto
)

@Serializable
data class SubscriptionSummaryDto(
    val id: String,
    val userId: String,
    val userEmail: String,
    val userName: String?,
    val planId: String,
    val planName: String,
    val status: String,
    val stripeSubscriptionId: String?,
    val stripeCustomerId: String?,
    val currentPeriodStart: String?,
    val currentPeriodEnd: String?,
    val creditsUsed: Int,
    val creditsLimit: Int,
    val createdAt: String,
    val updatedAt: String
)

// Extension function for clean domain -> DTO conversion
fun ListSubscriptionsResponse.toDto() = SubscriptionsListResponseDto(
    subscriptions = subscriptions.map { subscription ->
        SubscriptionSummaryDto(
            id = subscription.id,
            userId = subscription.userId,
            userEmail = subscription.userEmail,
            userName = subscription.userName,
            planId = subscription.planId,
            planName = subscription.planName,
            status = subscription.status.name.lowercase(),
            stripeSubscriptionId = subscription.stripeSubscriptionId,
            stripeCustomerId = subscription.stripeCustomerId,
            currentPeriodStart = subscription.currentPeriodStart?.toString(),
            currentPeriodEnd = subscription.currentPeriodEnd?.toString(),
            creditsUsed = subscription.creditsUsed,
            creditsLimit = subscription.creditsLimit,
            createdAt = subscription.createdAt.toString(),
            updatedAt = subscription.updatedAt.toString()
        )
    },
    pagination = PaginationDto(
        page = page,
        limit = limit,
        total = total,
        totalPages = ((total + limit - 1) / limit).toInt(),
        hasNext = page * limit < total,
        hasPrevious = page > 1
    )
)


// === SUBSCRIPTION DETAILS ===

@Serializable
data class SubscriptionDetailDto(
    // Core subscription info (reuses SubscriptionSummaryDto fields)
    val id: String,
    val userId: String,
    val userEmail: String,
    val userName: String?,
    val planId: String,
    val planName: String,
    val status: String,
    val stripeSubscriptionId: String?,
    val stripeCustomerId: String?,
    val currentPeriodStart: String?,
    val currentPeriodEnd: String?,
    val creditsUsed: Int,
    val creditsLimit: Int,
    val createdAt: String,
    val updatedAt: String,
    
    // Extended detail fields
    val billingCycle: String,
    val cancelAtPeriodEnd: Boolean,
    val plan: PlanDetailDto,
    val isActive: Boolean,
    val creditsForPeriod: Int,
    val priceForPeriod: Int,
    val totalScreenshots: Long,
    val screenshotsLast30Days: Long,
    val creditsRemaining: Int
)

// Extension function for clean domain -> DTO conversion (composition pattern)
fun dev.screenshotapi.core.usecases.admin.GetSubscriptionDetailsResponse.toDto() = SubscriptionDetailDto(
    // Map core fields from the subscription entity
    id = subscription?.id ?: subscriptionId,
    userId = userId,
    userEmail = userEmail,
    userName = userName,
    planId = plan.id,
    planName = plan.name,
    status = subscription?.status?.name?.lowercase() ?: "unknown",
    stripeSubscriptionId = subscription?.stripeSubscriptionId,
    stripeCustomerId = subscription?.stripeCustomerId,
    currentPeriodStart = subscription?.currentPeriodStart?.toString(),
    currentPeriodEnd = subscription?.currentPeriodEnd?.toString(),
    creditsUsed = 0, // Simplified: avoid negative numbers
    creditsLimit = creditsRemaining, // Show actual remaining credits
    createdAt = subscription?.createdAt?.toString() ?: "",
    updatedAt = subscription?.updatedAt?.toString() ?: "",
    
    // Extended fields from the composed response
    billingCycle = subscription?.billingCycle?.name?.lowercase() ?: "monthly",
    cancelAtPeriodEnd = subscription?.cancelAtPeriodEnd ?: false,
    plan = PlanDetailDto(
        id = plan.id,
        name = plan.name,
        creditsPerMonth = plan.creditsPerMonth,
        priceCents = plan.priceCentsMonthly
    ),
    isActive = isActive,
    creditsForPeriod = creditsForPeriod,
    priceForPeriod = priceForPeriod,
    totalScreenshots = totalScreenshots, // Real usage data
    screenshotsLast30Days = screenshotsLast30Days, // Real usage data
    creditsRemaining = creditsRemaining // Real remaining credits from usage
)
