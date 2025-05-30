package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.UserActivityType
import dev.screenshotapi.core.domain.repositories.ActivityRepository
import dev.screenshotapi.core.usecases.admin.UserActivity
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Activities
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Users
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.days

class PostgreSQLActivityRepository(private val database: Database) : ActivityRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(activity: UserActivity): UserActivity {
        return try {
            newSuspendedTransaction(db = database) {
                val userExists = Users.select { Users.id eq activity.userId }.count() > 0

                if (!userExists) {
                    throw DatabaseException.OperationFailed("User not found: ${activity.userId}")
                }

                val activityId = if (activity.id.isNotBlank()) {
                    activity.id
                } else {
                    UUID.randomUUID().toString() // Generate new UUID string if current ID is blank
                }

                val insertedId = Activities.insertAndGetId {
                    it[id] = activityId
                    it[userId] = activity.userId
                    it[type] = activity.type.name
                    it[description] = activity.description
                    it[metadata] = activity.metadata?.let { metadata -> json.encodeToString(metadata) }
                    it[timestamp] = activity.timestamp
                }

                activity.copy(id = insertedId.value)
            }
        } catch (e: Exception) {
            logger.error("Error saving activity: ${activity.id}", e)
            throw DatabaseException.OperationFailed("Failed to save activity", e)
        }
    }

    override suspend fun findByUserId(userId: String, days: Int, limit: Int): List<UserActivity> {
        return try {
            newSuspendedTransaction(db = database) {
                val cutoffDate = Clock.System.now().minus(days.days)

                Activities.select {
                    (Activities.userId eq userId) and
                    (Activities.timestamp greaterEq cutoffDate)
                }
                .orderBy(Activities.timestamp, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    mapRowToUserActivity(row)
                }
            }
        } catch (e: Exception) {
            logger.error("Error finding activities for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find activities for user", e)
        }
    }

    override suspend fun findByType(type: UserActivityType, limit: Int): List<UserActivity> {
        return try {
            newSuspendedTransaction(db = database) {
                Activities.select { Activities.type eq type.name }
                    .orderBy(Activities.timestamp, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        mapRowToUserActivity(row)
                    }
            }
        } catch (e: Exception) {
            logger.error("Error finding activities by type: $type", e)
            throw DatabaseException.OperationFailed("Failed to find activities by type", e)
        }
    }

    override suspend fun deleteOlderThan(days: Int): Long {
        return try {
            newSuspendedTransaction(db = database) {
                val cutoffDate = Clock.System.now().minus(days.days)

                Activities.deleteWhere { Activities.timestamp less cutoffDate }.toLong()
            }
        } catch (e: Exception) {
            logger.error("Error deleting old activities", e)
            throw DatabaseException.OperationFailed("Failed to delete old activities", e)
        }
    }

    private fun mapRowToUserActivity(row: ResultRow): UserActivity {
        val metadataJson = row[Activities.metadata]
        val metadata = if (metadataJson != null) {
            try {
                json.decodeFromString<Map<String, String>>(metadataJson)
            } catch (e: Exception) {
                logger.warn("Failed to parse metadata JSON: $metadataJson", e)
                null
            }
        } else null

        return UserActivity(
            id = row[Activities.id].value.toString(),
            userId = row[Activities.userId].value.toString(),
            type = UserActivityType.valueOf(row[Activities.type]),
            description = row[Activities.description],
            metadata = metadata,
            timestamp = row[Activities.timestamp]
        )
    }
}
