package dev.screenshotapi.infrastructure.adapters.output.queue.dto

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.infrastructure.adapters.output.persistence.dto.ScreenshotRequestDto
import dev.screenshotapi.infrastructure.adapters.output.persistence.dto.ScreenshotStatusDto
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ScreenshotJobQueueDto(
    val id: String,
    val userId: String,
    val apiKeyId: String,
    val request: ScreenshotRequestDto,
    val status: ScreenshotStatusDto,
    val resultUrl: String? = null,
    val errorMessage: String? = null,
    val processingTimeMs: Long? = null,
    val webhookUrl: String? = null,
    val webhookSent: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null
) {
    fun toDomain(): ScreenshotJob = ScreenshotJob(
        id = id,
        userId = userId,
        apiKeyId = apiKeyId,
        request = request.toDomain(),
        status = status.toDomain(),
        resultUrl = resultUrl,
        errorMessage = errorMessage,
        processingTimeMs = processingTimeMs,
        webhookUrl = webhookUrl,
        webhookSent = webhookSent,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt
    )

    companion object {
        fun fromDomain(job: ScreenshotJob) = ScreenshotJobQueueDto(
            id = job.id,
            userId = job.userId,
            apiKeyId = job.apiKeyId,
            request = ScreenshotRequestDto.fromDomain(job.request),
            status = ScreenshotStatusDto.fromDomain(job.status),
            resultUrl = job.resultUrl,
            errorMessage = job.errorMessage,
            processingTimeMs = job.processingTimeMs,
            webhookUrl = job.webhookUrl,
            webhookSent = job.webhookSent,
            createdAt = job.createdAt,
            updatedAt = job.updatedAt,
            completedAt = job.completedAt
        )
    }
}
