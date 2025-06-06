package dev.screenshotapi.core.usecases.logging

import dev.screenshotapi.core.domain.entities.UsageLog
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.repositories.UsageLogRepository
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.Instant

class LogUsageUseCase(
    private val usageLogRepository: UsageLogRepository
) : UseCase<LogUsageUseCase.Request, LogUsageUseCase.Response> {

    override suspend fun invoke(request: Request): Response {
        val usageLog = UsageLog.create(
            userId = request.userId,
            action = request.action,
            creditsUsed = request.creditsUsed,
            apiKeyId = request.apiKeyId,
            screenshotId = request.screenshotId,
            metadata = request.metadata,
            ipAddress = request.ipAddress,
            userAgent = request.userAgent,
            timestamp = request.timestamp ?: kotlinx.datetime.Clock.System.now()
        )

        val savedLog = usageLogRepository.save(usageLog)
        
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