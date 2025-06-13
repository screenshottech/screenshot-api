package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.domain.entities.StatsBreakdown
import dev.screenshotapi.core.domain.entities.StatsGroupBy
import dev.screenshotapi.core.domain.entities.StatsPeriod
import dev.screenshotapi.core.domain.entities.UserStatus
import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.usecases.admin.*
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import dev.screenshotapi.infrastructure.auth.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AdminController : KoinComponent {
    private val listUsersUseCase: ListUsersUseCase by inject()
    private val getUserDetailsUseCase: GetUserDetailsUseCase by inject()
    private val getSystemStatsUseCase: GetSystemStatsUseCase by inject()
    private val updateUserStatusUseCase: UpdateUserStatusUseCase by inject()
    private val getUserActivityUseCase: GetUserActivityUseCase by inject()
    private val getScreenshotStatsUseCase: GetScreenshotStatsUseCase by inject()
    private val provisionSubscriptionCreditsAdminUseCase: ProvisionSubscriptionCreditsAdminUseCase by inject()
    private val synchronizeUserPlanAdminUseCase: SynchronizeUserPlanAdminUseCase by inject()
    private val listSubscriptionsUseCase: ListSubscriptionsUseCase by inject()
    private val getSubscriptionDetailsUseCase: GetSubscriptionDetailsUseCase by inject()

    suspend fun listUsers(call: ApplicationCall) {
        try {
            println("[AdminController] Extracting principal from call...")
            val principal = call.principal<UserPrincipal>()!!
            println("[AdminController] Principal extracted successfully")

            // Debug logging
            println("[AdminController] listUsers - User: ${principal.email}")
            println("[AdminController] listUsers - Status: ${principal.status}")
            println("[AdminController] listUsers - Roles: ${principal.roles.map { it.name }}")
            println("[AdminController] listUsers - canManageUsers(): ${principal.canManageUsers()}")

            // Let's also check the raw Authorization header
            val authHeader = call.request.headers["Authorization"]
            println("[AdminController] Authorization header: ${authHeader?.take(50)}...")

            if (!principal.canManageUsers()) {
                println("[AdminController] listUsers - Access denied for user ${principal.email}")
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto.forbidden("User management access required")
                )
                return
            }

            println("[AdminController] listUsers - Access granted for user ${principal.email}")

            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val limit = call.parameters["limit"]?.toIntOrNull()?.coerceAtMost(100) ?: 20
            val search = call.parameters["search"]
            val status = call.parameters["status"]

            val request = ListUsersRequest(
                page = page,
                limit = limit,
                searchQuery = search,
                statusFilter = status?.let { UserStatus.valueOf(it.uppercase()) }
            )

            val response = listUsersUseCase(request)
            call.respond(HttpStatusCode.OK, convertToUsersListDto(response))

        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation("Invalid status parameter")
            )
        } catch (e: Exception) {
            call.application.log.error("List users error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to list users")
            )
        }
    }

    suspend fun getUser(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            if (!principal.canManageUsers()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto.forbidden("User management access required")
                )
                return
            }

            val userId = call.parameters["userId"]!!
            val includeActivity = call.parameters["includeActivity"]?.toBoolean() ?: false
            val includeApiKeys = call.parameters["includeApiKeys"]?.toBoolean() ?: false

            val request = GetUserDetailsRequest(
                userId = userId,
                includeActivity = includeActivity,
                includeApiKeys = includeApiKeys
            )

            val response = getUserDetailsUseCase(request)
            call.respond(HttpStatusCode.OK, convertToUserDetailsDto(response))

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("User", call.parameters["userId"] ?: "unknown")
            )
        } catch (e: Exception) {
            call.application.log.error("Get user error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to get user details")
            )
        }
    }

    suspend fun getStats(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            if (!principal.canViewSystemStats()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto.forbidden("System statistics access required")
                )
                return
            }

            val period = call.parameters["period"] ?: "30d"
            val breakdown = call.parameters["breakdown"] ?: "daily"

            val request = GetSystemStatsRequest(
                period = parsePeriod(period),
                breakdown = parseBreakdown(breakdown)
            )

            val response = getSystemStatsUseCase(request)
            call.respond(HttpStatusCode.OK, convertToSystemStatsDto(response))

        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation("Invalid period or breakdown parameter")
            )
        } catch (e: Exception) {
            call.application.log.error("Get stats error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to get system statistics")
            )
        }
    }

    suspend fun updateUserStatus(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            if (!principal.canManageUsers()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto.forbidden("User management access required")
                )
                return
            }

            val userId = call.parameters["userId"]!!
            val dto = call.receive<UpdateUserStatusRequestDto>()

            val request = UpdateUserStatusRequest(
                userId = userId,
                status = UserStatus.valueOf(dto.status.uppercase()),
                reason = dto.reason,
                adminUserId = principal.userId
            )

            val response = updateUserStatusUseCase(request)
            call.respond(HttpStatusCode.OK, convertToUpdateStatusDto(response))

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("User", call.parameters["userId"] ?: "unknown")
            )
        } catch (e: ValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation(e.message ?: "Validation failed", e.field)
            )
        } catch (e: Exception) {
            call.application.log.error("Update user status error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to update user status")
            )
        }
    }

    suspend fun getUserActivity(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            if (!principal.canManageUsers()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto.forbidden("User management access required")
                )
                return
            }

            val userId = call.parameters["userId"]!!
            val days = call.parameters["days"]?.toIntOrNull() ?: 30
            val limit = call.parameters["limit"]?.toIntOrNull()?.coerceAtMost(1000) ?: 100

            val request = GetUserActivityRequest(
                userId = userId,
                days = days,
                limit = limit
            )

            val response = getUserActivityUseCase(request)
            call.respond(HttpStatusCode.OK, convertToUserActivityDto(response))

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("User", call.parameters["userId"] ?: "unknown")
            )
        } catch (e: Exception) {
            call.application.log.error("Get user activity error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to get user activity")
            )
        }
    }

    suspend fun getScreenshotStats(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            if (!principal.canViewSystemStats()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto.forbidden("System statistics access required")
                )
                return
            }

            val period = call.parameters["period"] ?: "7d"
            val groupBy = call.parameters["groupBy"] ?: "day"

            val request = GetScreenshotStatsRequest(
                period = parsePeriod(period),
                groupBy = parseGroupBy(groupBy)
            )

            val response = getScreenshotStatsUseCase(request)
            call.respond(HttpStatusCode.OK, convertToScreenshotStatsDto(response))

        } catch (e: Exception) {
            call.application.log.error("Get screenshotapi stats error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to get screenshotapi statistics")
            )
        }
    }

    // Helper methods
    private fun isAdmin(principal: UserPrincipal): Boolean {
        return principal.canAccessAdmin()
    }

    private fun parsePeriod(period: String): StatsPeriod {
        return when (period.lowercase()) {
            "1d", "day", "today" -> StatsPeriod.DAY
            "7d", "week" -> StatsPeriod.WEEK
            "30d", "month" -> StatsPeriod.MONTH
            "90d", "quarter" -> StatsPeriod.QUARTER
            "365d", "year" -> StatsPeriod.YEAR
            else -> throw IllegalArgumentException("Invalid period: $period")
        }
    }

    private fun parseBreakdown(breakdown: String): StatsBreakdown {
        return when (breakdown.lowercase()) {
            "hourly", "hour" -> StatsBreakdown.HOURLY
            "daily", "day" -> StatsBreakdown.DAILY
            "weekly", "week" -> StatsBreakdown.WEEKLY
            "monthly", "month" -> StatsBreakdown.MONTHLY
            else -> throw IllegalArgumentException("Invalid breakdown: $breakdown")
        }
    }

    private fun parseGroupBy(groupBy: String): StatsGroupBy {
        return when (groupBy.lowercase()) {
            "hour" -> StatsGroupBy.HOUR
            "day" -> StatsGroupBy.DAY
            "week" -> StatsGroupBy.WEEK
            "month" -> StatsGroupBy.MONTH
            "status" -> StatsGroupBy.STATUS
            "format" -> StatsGroupBy.FORMAT
            else -> throw IllegalArgumentException("Invalid groupBy: $groupBy")
        }
    }

    // DTO conversion functions to avoid ambiguity
    private fun convertToUsersListDto(response: ListUsersResponse): UsersListResponseDto {
        return UsersListResponseDto(
            users = response.users.map { user ->
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
                page = response.page,
                limit = response.limit,
                total = response.total,
                totalPages = ((response.total + response.limit - 1) / response.limit).toInt(),
                hasNext = response.page * response.limit < response.total,
                hasPrevious = response.page > 1
            )
        )
    }

    private fun convertToUserDetailsDto(response: GetUserDetailsResponse): UserDetailsResponseDto {
        // For now, return a simple map - implement proper conversion later
        return UserDetailsResponseDto(
            user = UserDetailDto(
                id = response.user.id,
                email = response.user.email,
                name = response.user.name,
                status = response.user.status.name.lowercase(),
                plan = PlanDetailDto(
                    id = response.user.plan.id,
                    name = response.user.plan.name,
                    creditsPerMonth = response.user.plan.creditsPerMonth,
                    priceCents = response.user.plan.priceCents
                ),
                roles = response.user.roles,
                creditsRemaining = response.user.creditsRemaining,
                totalScreenshots = response.user.totalScreenshots,
                successfulScreenshots = response.user.successfulScreenshots,
                failedScreenshots = response.user.failedScreenshots,
                totalCreditsUsed = response.user.totalCreditsUsed,
                lastActivity = response.user.lastActivity?.toString(),
                createdAt = response.user.createdAt.toString(),
                updatedAt = response.user.updatedAt.toString(),
                stripeCustomerId = response.user.stripeCustomerId
            ),
            activity = response.activity?.map {
                UserActivityDto(
                    id = it.id,
                    type = it.type.name,
                    description = it.description,
                    metadata = it.metadata,
                    timestamp = it.timestamp.toString()
                )
            },
            apiKeys = response.apiKeys?.map {
                ApiKeySummaryDto(
                    id = it.id,
                    name = it.name,
                    permissions = it.permissions.map { perm -> perm.name.lowercase() },
                    isActive = it.isActive,
                    lastUsed = it.lastUsed?.toString(),
                    createdAt = it.createdAt.toString()
                )
            }
        )
    }

    private fun convertToSystemStatsDto(response: GetSystemStatsResponse): Map<String, Any> {
        // Simple map conversion for now
        return mapOf(
            "overview" to mapOf(
                "totalUsers" to response.overview.totalUsers,
                "activeUsers" to response.overview.activeUsers,
                "totalScreenshots" to response.overview.totalScreenshots,
                "screenshotsToday" to response.overview.screenshotsToday,
                "successRate" to response.overview.successRate
            )
        )
    }

    private fun convertToUpdateStatusDto(response: UpdateUserStatusResponse): Map<String, Any> {
        return mapOf(
            "userId" to response.userId,
            "status" to response.status.name.lowercase(),
            "updatedAt" to response.updatedAt.toString()
        )
    }

    private fun convertToUserActivityDto(response: GetUserActivityResponse): Map<String, Any> {
        return mapOf(
            "userId" to response.userId,
            "activities" to response.activities.map { activity ->
                mapOf(
                    "id" to activity.id,
                    "type" to activity.type.name,
                    "description" to activity.description,
                    "timestamp" to activity.timestamp.toString()
                )
            }
        )
    }

    suspend fun listSubscriptions(call: ApplicationCall) {
        val principal = call.principal<UserPrincipal>()!!
        if (!principal.canManageBilling()) {
            throw AuthorizationException.InsufficientPermissions("Billing management access required")
        }

        val page = call.parameters["page"]?.toIntOrNull() ?: 1
        val limit = call.parameters["limit"]?.toIntOrNull()?.coerceAtMost(100) ?: 20
        val search = call.parameters["search"]
        val status = call.parameters["status"]
        val planId = call.parameters["planId"]

        val request = ListSubscriptionsRequest(
            page = page,
            limit = limit,
            searchQuery = search,
            statusFilter = status?.let { 
                try {
                    SubscriptionStatus.valueOf(it.uppercase())
                } catch (e: IllegalArgumentException) {
                    throw ValidationException("Invalid status parameter: $it", "status")
                }
            },
            planIdFilter = planId
        )

        val response = listSubscriptionsUseCase(request)
        call.respond(HttpStatusCode.OK, response.toDto())
    }

    suspend fun getSubscriptionDetails(call: ApplicationCall) {
        val principal = call.principal<UserPrincipal>()!!
        if (!principal.canManageBilling()) {
            throw AuthorizationException.InsufficientPermissions("Billing management access required")
        }

        val subscriptionId = call.parameters["subscriptionId"]
            ?: throw ValidationException("Subscription ID is required", "subscriptionId")

        val request = GetSubscriptionDetailsRequest(subscriptionId = subscriptionId)
        val response = getSubscriptionDetailsUseCase(request)
        call.respond(HttpStatusCode.OK, response.toDto())
    }

    suspend fun provisionSubscriptionCredits(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            if (!principal.canManageBilling()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto.forbidden("Billing management access required")
                )
                return
            }

            val subscriptionId = call.parameters["subscriptionId"]
                ?: throw ValidationException("Subscription ID is required", "subscriptionId")

            val body = call.receive<Map<String, String>>()
            val reason = body["reason"] ?: "admin_manual_provision"

            val request = ProvisionSubscriptionCreditsAdminRequest(
                subscriptionId = subscriptionId,
                reason = reason,
                adminUserId = principal.userId
            )

            val response = provisionSubscriptionCreditsAdminUseCase(request)
            call.respond(HttpStatusCode.OK, response.toDto())

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("Subscription", call.parameters["subscriptionId"] ?: "unknown")
            )
        } catch (e: ValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation(e.message ?: "Invalid request", e.field)
            )
        } catch (e: Exception) {
            call.application.log.error("Provision subscription credits error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to provision credits")
            )
        }
    }

    suspend fun synchronizeUserPlan(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            if (!principal.canManageBilling()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto.forbidden("Billing management access required")
                )
                return
            }

            val userId = call.parameters["userId"]
                ?: throw ValidationException("User ID is required", "userId")

            val body = call.receive<Map<String, String>>()
            val subscriptionId = body["subscriptionId"] // Optional
            val reason = body["reason"] ?: "admin_manual_sync"

            val request = SynchronizeUserPlanAdminRequest(
                userId = userId,
                subscriptionId = subscriptionId,
                adminUserId = principal.userId,
                reason = reason
            )

            val response = synchronizeUserPlanAdminUseCase(request)
            call.respond(HttpStatusCode.OK, response.toDto())

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("User or Subscription", call.parameters["userId"] ?: "unknown")
            )
        } catch (e: ValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation(e.message ?: "Invalid request", e.field)
            )
        } catch (e: Exception) {
            call.application.log.error("Synchronize user plan error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to synchronize user plan")
            )
        }
    }


    private fun convertToScreenshotStatsDto(response: GetScreenshotStatsResponse): Map<String, Any> {
        return mapOf(
            "totalScreenshots" to response.totalScreenshots,
            "period" to response.period.name.lowercase(),
            "data" to response.data.map { item ->
                mapOf(
                    "period" to item.period,
                    "count" to item.count,
                    "successful" to item.successful,
                    "failed" to item.failed
                )
            }
        )
    }
}
