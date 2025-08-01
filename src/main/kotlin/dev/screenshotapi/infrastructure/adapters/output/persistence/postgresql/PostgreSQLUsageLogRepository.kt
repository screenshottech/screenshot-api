package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.UsageLog
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.repositories.UsageLogRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.UsageLogs
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Screenshots
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpsertStatement

class PostgreSQLUsageLogRepository(
    private val database: Database
) : UsageLogRepository {

    override suspend fun save(usageLog: UsageLog): UsageLog = dbQuery(database) {
        // Validate screenshot exists if screenshot_id is provided
        usageLog.screenshotId?.let { screenshotId ->
            val screenshotExists = Screenshots.select {
                Screenshots.id eq screenshotId
            }.singleOrNull() != null
            
            if (!screenshotExists) {
                throw DatabaseException.OperationFailed(
                    "Cannot create usage log: Screenshot with ID $screenshotId does not exist"
                )
            }
        }
        
        // Use upsert to handle potential duplicates (idempotent operations)
        UsageLogs.upsert(
            keys = arrayOf(UsageLogs.id)
        ) {
            it[id] = usageLog.id
            it[userId] = usageLog.userId
            it[apiKeyId] = usageLog.apiKeyId
            it[screenshotId] = usageLog.screenshotId
            it[action] = usageLog.action.name
            it[creditsUsed] = usageLog.creditsUsed
            it[metadata] = usageLog.metadata?.let { map ->
                Json.encodeToString(
                    serializer<Map<String, String>>(),
                    map
                )
            }
            it[ipAddress] = usageLog.ipAddress
            it[userAgent] = usageLog.userAgent
            it[timestamp] = usageLog.timestamp
        }
        usageLog
    }

    override suspend fun findById(id: String): UsageLog? = dbQuery(database) {
        UsageLogs.select { UsageLogs.id eq id }
            .singleOrNull()
            ?.toUsageLog()
    }

    override suspend fun findByUserId(
        userId: String,
        limit: Int,
        offset: Int
    ): List<UsageLog> = dbQuery(database) {
        UsageLogs.select { UsageLogs.userId eq userId }
            .orderBy(UsageLogs.timestamp, SortOrder.DESC)
            .limit(limit, offset.toLong())
            .map { it.toUsageLog() }
    }

    override suspend fun findByUserAndAction(
        userId: String,
        action: UsageLogAction,
        limit: Int
    ): List<UsageLog> = dbQuery(database) {
        UsageLogs.select {
            (UsageLogs.userId eq userId) and (UsageLogs.action eq action.name)
        }
            .orderBy(UsageLogs.timestamp, SortOrder.DESC)
            .limit(limit)
            .map { it.toUsageLog() }
    }

    override suspend fun findByUserAndTimeRange(
        userId: String,
        startTime: Instant,
        endTime: Instant,
        limit: Int
    ): List<UsageLog> = dbQuery(database) {
        UsageLogs.select {
            (UsageLogs.userId eq userId) and
            (UsageLogs.timestamp greaterEq startTime) and
            (UsageLogs.timestamp lessEq endTime)
        }
            .orderBy(UsageLogs.timestamp, SortOrder.DESC)
            .limit(limit)
            .map { it.toUsageLog() }
    }

    override suspend fun findByScreenshotId(screenshotId: String): List<UsageLog> = dbQuery(database) {
        UsageLogs.select { UsageLogs.screenshotId eq screenshotId }
            .orderBy(UsageLogs.timestamp, SortOrder.DESC)
            .map { it.toUsageLog() }
    }

    override suspend fun findByApiKeyId(apiKeyId: String, limit: Int): List<UsageLog> = dbQuery(database) {
        UsageLogs.select { UsageLogs.apiKeyId eq apiKeyId }
            .orderBy(UsageLogs.timestamp, SortOrder.DESC)
            .limit(limit)
            .map { it.toUsageLog() }
    }

    override suspend fun getTotalCreditsUsedByUser(
        userId: String,
        startTime: Instant,
        endTime: Instant
    ): Int = dbQuery(database) {
        UsageLogs.slice(UsageLogs.creditsUsed.sum())
            .select {
                (UsageLogs.userId eq userId) and
                (UsageLogs.timestamp greaterEq startTime) and
                (UsageLogs.timestamp lessEq endTime)
            }
            .singleOrNull()
            ?.get(UsageLogs.creditsUsed.sum()) ?: 0
    }

    override suspend fun getActionCountByUser(
        userId: String,
        action: UsageLogAction,
        startTime: Instant,
        endTime: Instant
    ): Long = dbQuery(database) {
        UsageLogs.select {
            (UsageLogs.userId eq userId) and
            (UsageLogs.action eq action.name) and
            (UsageLogs.timestamp greaterEq startTime) and
            (UsageLogs.timestamp lessEq endTime)
        }.count()
    }

    private fun ResultRow.toUsageLog(): UsageLog {
        val metadataJson = this[UsageLogs.metadata]
        val metadata = metadataJson?.let {
            try {
                kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(it)
            } catch (e: Exception) {
                null
            }
        }

        return UsageLog(
            id = this[UsageLogs.id].value,
            userId = this[UsageLogs.userId].value,
            apiKeyId = this[UsageLogs.apiKeyId]?.value,
            screenshotId = this[UsageLogs.screenshotId]?.value,
            action = UsageLogAction.valueOf(this[UsageLogs.action]),
            creditsUsed = this[UsageLogs.creditsUsed],
            metadata = metadata,
            ipAddress = this[UsageLogs.ipAddress],
            userAgent = this[UsageLogs.userAgent],
            timestamp = this[UsageLogs.timestamp]
        )
    }
}
