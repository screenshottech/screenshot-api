package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.UserActivityType
import dev.screenshotapi.core.domain.repositories.ActivityRepository
import dev.screenshotapi.core.usecases.admin.UserActivity
import org.jetbrains.exposed.sql.Database

class PostgreSQLActivityRepository(private val database: Database) : ActivityRepository {
    override suspend fun save(activity: UserActivity): UserActivity {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findByUserId(userId: String, days: Int, limit: Int): List<UserActivity> {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findByType(type: UserActivityType, limit: Int): List<UserActivity> {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun deleteOlderThan(days: Int): Long {
        TODO("PostgreSQL implementation not yet completed")
    }
}
