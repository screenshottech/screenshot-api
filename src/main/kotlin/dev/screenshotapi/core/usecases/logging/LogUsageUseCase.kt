package dev.screenshotapi.core.usecases.logging

import dev.screenshotapi.core.domain.entities.UsageLog
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.repositories.UsageLogRepository
import dev.screenshotapi.core.usecases.common.UseCase
import dev.screenshotapi.core.usecases.stats.UpdateDailyStatsUseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

class LogUsageUseCase(
    private val usageLogRepository: UsageLogRepository,
    private val updateDailyStatsUseCase: UpdateDailyStatsUseCase
) : UseCase<LogUsageUseCase.Request, LogUsageUseCase.Response> {
    
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun invoke(request: Request): Response {
        val timestamp = request.timestamp ?: Clock.System.now()
        
        val usageLog = UsageLog.create(
            userId = request.userId,
            action = request.action,
            creditsUsed = request.creditsUsed,
            apiKeyId = request.apiKeyId,
            screenshotId = request.screenshotId,
            metadata = request.metadata,
            ipAddress = request.ipAddress,
            userAgent = request.userAgent,
            timestamp = timestamp
        )

        // Save the usage log
        val savedLog = usageLogRepository.save(usageLog)

        // Update daily statistics in real-time (async, don't fail if this fails)
        try {
            val date = timestamp.toLocalDateTime(TimeZone.UTC).date
            val statsUpdateRequest = UpdateDailyStatsUseCase.Request(
                userId = request.userId,
                action = request.action,
                date = date,
                creditsUsed = request.creditsUsed,
                metadata = request.metadata ?: emptyMap()
            )
            
            updateDailyStatsUseCase.invoke(statsUpdateRequest)
            logger.debug("Updated daily stats for user ${request.userId}, action ${request.action}")
            
        } catch (e: Exception) {
            // Don't fail the usage logging if stats update fails
            // This ensures the primary logging functionality continues to work
            logger.warn("Failed to update daily stats for user ${request.userId}, but usage log was saved", e)
        }

        return Response(
            logId = savedLog.id,
            success = true
        )
    }

    data class Request(
        val userId: String,
        val action: UsageLogAction,
        val creditsUsed: Int = 0,
        val apiKeyId: String? = null,
        val screenshotId: String? = null,
        val metadata: Map<String, String>? = null,
        val ipAddress: String? = null,
        val userAgent: String? = null,
        val timestamp: Instant? = null
    )

    data class Response(
        val logId: String,
        val success: Boolean
    )
}
