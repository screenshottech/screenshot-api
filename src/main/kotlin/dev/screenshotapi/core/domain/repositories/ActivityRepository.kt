package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.UserActivityType
import dev.screenshotapi.core.usecases.admin.UserActivity

interface ActivityRepository {
    suspend fun save(activity: UserActivity): UserActivity
    suspend fun findByUserId(userId: String, days: Int, limit: Int): List<UserActivity>
    suspend fun findByType(type: UserActivityType, limit: Int): List<UserActivity>
    suspend fun deleteOlderThan(days: Int): Long
}
