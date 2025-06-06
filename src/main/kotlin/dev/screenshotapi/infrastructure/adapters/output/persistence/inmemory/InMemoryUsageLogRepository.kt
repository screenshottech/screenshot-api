package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.UsageLog
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.repositories.UsageLogRepository
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryUsageLogRepository : UsageLogRepository {
    
    private val usageLogs = ConcurrentHashMap<String, UsageLog>()

    override suspend fun save(usageLog: UsageLog): UsageLog {
        usageLogs[usageLog.id] = usageLog
        return usageLog
    }

    override suspend fun findById(id: String): UsageLog? {
        return usageLogs[id]
    }

    override suspend fun findByUserId(
        userId: String,
        limit: Int,
        offset: Int
    ): List<UsageLog> {
        return usageLogs.values
            .filter { it.userId == userId }
            .sortedByDescending { it.timestamp }
            .drop(offset)
            .take(limit)
    }

    override suspend fun findByUserAndAction(
        userId: String,
        action: UsageLogAction,
        limit: Int
    ): List<UsageLog> {
        return usageLogs.values
            .filter { it.userId == userId && it.action == action }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun findByUserAndTimeRange(
        userId: String,
        startTime: Instant,
        endTime: Instant,
        limit: Int
    ): List<UsageLog> {
        return usageLogs.values
            .filter { 
                it.userId == userId && 
                it.timestamp >= startTime && 
                it.timestamp <= endTime 
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun findByScreenshotId(screenshotId: String): List<UsageLog> {
        return usageLogs.values
            .filter { it.screenshotId == screenshotId }
            .sortedByDescending { it.timestamp }
    }

    override suspend fun findByApiKeyId(apiKeyId: String, limit: Int): List<UsageLog> {
        return usageLogs.values
            .filter { it.apiKeyId == apiKeyId }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun getTotalCreditsUsedByUser(
        userId: String,
        startTime: Instant,
        endTime: Instant
    ): Int {
        return usageLogs.values
            .filter { 
                it.userId == userId && 
                it.timestamp >= startTime && 
                it.timestamp <= endTime 
            }
            .sumOf { it.creditsUsed }
    }

    override suspend fun getActionCountByUser(
        userId: String,
        action: UsageLogAction,
        startTime: Instant,
        endTime: Instant
    ): Long {
        return usageLogs.values
            .filter { 
                it.userId == userId && 
                it.action == action &&
                it.timestamp >= startTime && 
                it.timestamp <= endTime 
            }
            .size.toLong()
    }

    fun clear() {
        usageLogs.clear()
    }

    fun getAll(): List<UsageLog> {
        return usageLogs.values.toList()
    }
}