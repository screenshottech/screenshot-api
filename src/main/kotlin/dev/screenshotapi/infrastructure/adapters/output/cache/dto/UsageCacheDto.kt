package dev.screenshotapi.infrastructure.adapters.output.cache.dto

import dev.screenshotapi.core.domain.entities.DailyUsage
import dev.screenshotapi.core.domain.entities.ShortTermUsage
import dev.screenshotapi.core.domain.entities.UserUsage
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Cache DTO for UserUsage entity
 */
@Serializable
data class UserUsageCacheDto(
    val userId: String,
    val month: String,
    val totalRequests: Int,
    val planCreditsLimit: Int,
    val remainingCredits: Int,
    val lastRequestAt: String, // ISO string representation
    val createdAt: String,     // ISO string representation
    val updatedAt: String      // ISO string representation
) {
    companion object {
        fun fromDomain(entity: UserUsage): UserUsageCacheDto = UserUsageCacheDto(
            userId = entity.userId,
            month = entity.month,
            totalRequests = entity.totalRequests,
            planCreditsLimit = entity.planCreditsLimit,
            remainingCredits = entity.remainingCredits,
            lastRequestAt = entity.lastRequestAt.toString(),
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString()
        )
    }

    fun toDomain(): UserUsage = UserUsage(
        userId = userId,
        month = month,
        totalRequests = totalRequests,
        planCreditsLimit = planCreditsLimit,
        remainingCredits = remainingCredits,
        lastRequestAt = Instant.parse(lastRequestAt),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt)
    )
}

/**
 * Cache DTO for ShortTermUsage entity
 */
@Serializable
data class ShortTermUsageCacheDto(
    val userId: String,
    val hourlyRequests: Int,
    val hourlyTimestamp: String,
    val minutelyRequests: Int,
    val minutelyTimestamp: String,
    val concurrentRequests: Int
) {
    companion object {
        fun fromDomain(entity: ShortTermUsage): ShortTermUsageCacheDto = ShortTermUsageCacheDto(
            userId = entity.userId,
            hourlyRequests = entity.hourlyRequests,
            hourlyTimestamp = entity.hourlyTimestamp.toString(),
            minutelyRequests = entity.minutelyRequests,
            minutelyTimestamp = entity.minutelyTimestamp.toString(),
            concurrentRequests = entity.concurrentRequests
        )
    }

    fun toDomain(): ShortTermUsage = ShortTermUsage(
        userId = userId,
        hourlyRequests = hourlyRequests,
        hourlyTimestamp = Instant.parse(hourlyTimestamp),
        minutelyRequests = minutelyRequests,
        minutelyTimestamp = Instant.parse(minutelyTimestamp),
        concurrentRequests = concurrentRequests
    )
}

/**
 * Cache DTO for DailyUsage entity
 */
@Serializable
data class DailyUsageCacheDto(
    val userId: String,
    val date: String,
    val requestsUsed: Int,
    val dailyLimit: Int,
    val lastRequestAt: String,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun fromDomain(entity: DailyUsage): DailyUsageCacheDto = DailyUsageCacheDto(
            userId = entity.userId,
            date = entity.date,
            requestsUsed = entity.requestsUsed,
            dailyLimit = entity.dailyLimit,
            lastRequestAt = entity.lastRequestAt.toString(),
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString()
        )
    }

    fun toDomain(): DailyUsage = DailyUsage(
        userId = userId,
        date = date,
        requestsUsed = requestsUsed,
        dailyLimit = dailyLimit,
        lastRequestAt = Instant.parse(lastRequestAt),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt)
    )
} 