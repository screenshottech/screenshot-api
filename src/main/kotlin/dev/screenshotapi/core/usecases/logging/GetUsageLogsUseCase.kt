package dev.screenshotapi.core.usecases.logging

import dev.screenshotapi.core.domain.entities.UsageLog
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.repositories.UsageLogRepository
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.Instant

class GetUsageLogsUseCase(
    private val usageLogRepository: UsageLogRepository
) : UseCase<GetUsageLogsUseCase.Request, GetUsageLogsUseCase.Response> {

    override suspend fun invoke(request: Request): Response {
        val logs = when {
            request.screenshotId != null -> {
                usageLogRepository.findByScreenshotId(request.screenshotId)
            }
            request.apiKeyId != null -> {
                usageLogRepository.findByApiKeyId(request.apiKeyId, request.limit)
            }
            request.action != null && request.startTime != null && request.endTime != null -> {
                usageLogRepository.findByUserAndTimeRange(
                    request.userId,
                    request.startTime,
                    request.endTime,
                    request.limit
                ).filter { it.action == request.action }
            }
            request.startTime != null && request.endTime != null -> {
                usageLogRepository.findByUserAndTimeRange(
                    request.userId,
                    request.startTime,
                    request.endTime,
                    request.limit
                )
            }
            request.action != null -> {
                usageLogRepository.findByUserAndAction(
                    request.userId,
                    request.action,
                    request.limit
                )
            }
            else -> {
                usageLogRepository.findByUserId(
                    request.userId,
                    request.limit,
                    request.offset
                )
            }
        }

        return Response(
            logs = logs,
            total = logs.size
        )
    }

    data class Request(
        val userId: String,
        val action: UsageLogAction? = null,
        val screenshotId: String? = null,
        val apiKeyId: String? = null,
        val startTime: Instant? = null,
        val endTime: Instant? = null,
        val limit: Int = 100,
        val offset: Int = 0
    )

    data class Response(
        val logs: List<UsageLog>,
        val total: Int
    )
}