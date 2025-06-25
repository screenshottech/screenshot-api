package dev.screenshotapi.infrastructure.adapters.input.rest.dto

import dev.screenshotapi.core.usecases.auth.AuthenticateUserResponse
import dev.screenshotapi.core.usecases.auth.CreateApiKeyResponse
import dev.screenshotapi.core.usecases.auth.GetUserProfileResponse
import dev.screenshotapi.core.usecases.auth.GetUserUsageTimelineResponse
import dev.screenshotapi.core.usecases.auth.RegisterUserResponse
import dev.screenshotapi.core.usecases.auth.UpdateApiKeyResponse
import dev.screenshotapi.core.domain.entities.UsageTimelineEntry
import dev.screenshotapi.core.domain.entities.UsageTimelineSummary
import kotlinx.serialization.Serializable

// Request DTOs
@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val name: String? = null
)

@Serializable
data class UpdateProfileRequestDto(
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class CreateApiKeyRequestDto(
    val name: String,
    val permissions: List<String>? = null,
    val setAsDefault: Boolean = false
)

@Serializable
data class UpdateApiKeyRequestDto(
    val isActive: Boolean? = null,
    val name: String? = null,
    val setAsDefault: Boolean? = null
)

// Response DTOs
@Serializable
data class LoginResponseDto(
    val token: String,
    val userId: String,
    val email: String,
    val name: String?,
    val expiresAt: String
)

@Serializable
data class RegisterResponseDto(
    val userId: String,
    val email: String,
    val name: String?,
    val status: String,
    val planId: String,
    val creditsRemaining: Int
)

@Serializable
data class UserProfileResponseDto(
    val userId: String,
    val email: String,
    val name: String?,
    val status: String,
    val planId: String,
    val creditsRemaining: Int,
    val createdAt: String,
    val lastActivity: String?
)

@Serializable
data class ApiKeyResponseDto(
    val id: String,
    val name: String,
    val keyValue: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val createdAt: String
)

@Serializable
data class ApiKeysListResponseDto(
    val apiKeys: List<ApiKeySummaryResponseDto>
)

@Serializable
data class ApiKeySummaryResponseDto(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val maskedKey: String,
    val usageCount: Long,
    val createdAt: String,
    val lastUsedAt: String?,
)

@Serializable
data class UserUsageResponseDto(
    val userId: String,
    val creditsRemaining: Int,
    val totalScreenshots: Long,
    val screenshotsLast30Days: Long,
    val planId: String,
    val planLimit: Int,
    val currentPeriodStart: String,
    val currentPeriodEnd: String
)

@Serializable
data class UsageTimelineEntryDto(
    val date: String, // ISO date string
    val screenshots: Int,
    val creditsUsed: Int,
    val apiCalls: Int,
    val successfulScreenshots: Int,
    val failedScreenshots: Int
)

@Serializable
data class UsageTimelineSummaryDto(
    val totalScreenshots: Int,
    val totalCreditsUsed: Int,
    val totalApiCalls: Int,
    val averageDaily: Double,
    val successRate: Double,
    val peakDay: String?, // ISO date string
    val peakDayScreenshots: Int
)

@Serializable
data class UsageTimelineResponseDto(
    val timeline: List<UsageTimelineEntryDto>,
    val summary: UsageTimelineSummaryDto,
    val period: String,
    val granularity: String
)

// Extension functions for conversion
fun AuthenticateUserResponse.toDto(): LoginResponseDto = LoginResponseDto(
    token = token ?: "jwt_token_placeholder",
    userId = userId ?: "unknown_user",
    email = email ?: "unknown@example.com",
    name = null,
    expiresAt = "placeholder_expiry"
)

fun RegisterUserResponse.toDto(): RegisterResponseDto = RegisterResponseDto(
    userId = userId ?: "unknown_user",
    email = email ?: "unknown@example.com",
    name = null,
    status = status?.name?.lowercase() ?: "active",
    planId = "basic_plan",
    creditsRemaining = 100
)

fun GetUserProfileResponse.toDto(): UserProfileResponseDto = UserProfileResponseDto(
    userId = userId,
    email = email,
    name = name,
    status = status.name.lowercase(),
    planId = "basic_plan",
    creditsRemaining = creditsRemaining,
    createdAt = "2023-01-01T00:00:00Z",
    lastActivity = null
)

fun CreateApiKeyResponse.toDto(): ApiKeyResponseDto = ApiKeyResponseDto(
    id = id,
    name = name,
    keyValue = keyValue,
    isActive = isActive,
    isDefault = isDefault,
    createdAt = createdAt
)

fun UpdateApiKeyResponse.toDto(): ApiKeySummaryResponseDto = ApiKeySummaryResponseDto(
    id = apiKey.id,
    name = apiKey.name,
    isActive = apiKey.isActive,
    isDefault = apiKey.isDefault,
    maskedKey = "sk_****${apiKey.id.takeLast(4)}",
    usageCount = apiKey.usageCount,
    createdAt = apiKey.createdAt.toString(),
    lastUsedAt = apiKey.lastUsed?.toString()
)

fun dev.screenshotapi.core.usecases.auth.GetUserUsageResponse.toDto(): UserUsageResponseDto = UserUsageResponseDto(
    userId = userId,
    creditsRemaining = creditsRemaining,
    totalScreenshots = totalScreenshots,
    screenshotsLast30Days = screenshotsLast30Days,
    planId = planId,
    planLimit = planLimit,
    currentPeriodStart = currentPeriodStart.toString(),
    currentPeriodEnd = currentPeriodEnd.toString()
)

// Timeline conversion functions
fun UsageTimelineEntry.toDto(): UsageTimelineEntryDto = UsageTimelineEntryDto(
    date = date.toString(),
    screenshots = screenshots,
    creditsUsed = creditsUsed,
    apiCalls = apiCalls,
    successfulScreenshots = successfulScreenshots,
    failedScreenshots = failedScreenshots
)

fun UsageTimelineSummary.toDto(): UsageTimelineSummaryDto = UsageTimelineSummaryDto(
    totalScreenshots = totalScreenshots,
    totalCreditsUsed = totalCreditsUsed,
    totalApiCalls = totalApiCalls,
    averageDaily = averageDaily,
    successRate = successRate,
    peakDay = peakDay?.toString(),
    peakDayScreenshots = peakDayScreenshots
)

fun GetUserUsageTimelineResponse.toDto(): UsageTimelineResponseDto = UsageTimelineResponseDto(
    timeline = timeline.map { it.toDto() },
    summary = summary.toDto(),
    period = when(period) {
        dev.screenshotapi.core.domain.entities.TimePeriod.SEVEN_DAYS -> "7d"
        dev.screenshotapi.core.domain.entities.TimePeriod.THIRTY_DAYS -> "30d"
        dev.screenshotapi.core.domain.entities.TimePeriod.NINETY_DAYS -> "90d"
        dev.screenshotapi.core.domain.entities.TimePeriod.ONE_YEAR -> "1y"
    },
    granularity = granularity.name
)
