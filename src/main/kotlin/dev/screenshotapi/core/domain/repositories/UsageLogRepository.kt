package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.UsageLog
import dev.screenshotapi.core.domain.entities.UsageLogAction
import kotlinx.datetime.Instant

interface UsageLogRepository {
    
    suspend fun save(usageLog: UsageLog): UsageLog
    
    suspend fun findById(id: String): UsageLog?
    
    suspend fun findByUserId(
        userId: String, 
        limit: Int = 100,
        offset: Int = 0
    ): List<UsageLog>
    
    suspend fun findByUserAndAction(
        userId: String,
        action: UsageLogAction,
        limit: Int = 100
    ): List<UsageLog>
    
    suspend fun findByUserAndTimeRange(
        userId: String,
        startTime: Instant,
        endTime: Instant,
        limit: Int = 100
    ): List<UsageLog>
    
    suspend fun findByScreenshotId(screenshotId: String): List<UsageLog>
    
    suspend fun findByApiKeyId(apiKeyId: String, limit: Int = 100): List<UsageLog>
    
    suspend fun getTotalCreditsUsedByUser(
        userId: String,
        startTime: Instant,
        endTime: Instant
    ): Int
    
    suspend fun getActionCountByUser(
        userId: String,
        action: UsageLogAction,
        startTime: Instant,
        endTime: Instant
    ): Long
}