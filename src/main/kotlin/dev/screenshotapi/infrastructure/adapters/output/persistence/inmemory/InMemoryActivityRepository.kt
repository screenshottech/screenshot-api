package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.UserActivityType
import dev.screenshotapi.core.domain.repositories.ActivityRepository
import dev.screenshotapi.core.usecases.admin.UserActivity
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.days

class InMemoryActivityRepository : ActivityRepository {
    private val activities = ConcurrentHashMap<String, UserActivity>()

    override suspend fun save(activity: UserActivity): UserActivity {
        activities[activity.id] = activity
        return activity
    }

    override suspend fun findByUserId(userId: String, days: Int, limit: Int): List<UserActivity> {
        val cutoffDate = Clock.System.now().minus(days.days)

        return activities.values
            .filter { it.userId == userId && it.timestamp >= cutoffDate }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun findByType(type: UserActivityType, limit: Int): List<UserActivity> {
        return activities.values
            .filter { it.type == type }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun deleteOlderThan(days: Int): Long {
        val cutoffDate = Clock.System.now().minus(days.days)
        val toDelete = activities.values.filter { it.timestamp < cutoffDate }

        toDelete.forEach { activities.remove(it.id) }

        return toDelete.size.toLong()
    }
}
