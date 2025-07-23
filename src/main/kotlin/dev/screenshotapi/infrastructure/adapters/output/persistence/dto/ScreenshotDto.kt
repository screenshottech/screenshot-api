package dev.screenshotapi.infrastructure.adapters.output.persistence.dto

import dev.screenshotapi.core.domain.entities.ScreenshotFormat
import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class ScreenshotFormatDto {
    PNG, JPEG, WEBP, PDF;
    
    fun toDomain(): ScreenshotFormat = ScreenshotFormat.valueOf(name)
    
    companion object {
        fun fromDomain(format: ScreenshotFormat): ScreenshotFormatDto = valueOf(format.name)
        fun fromString(format: String): ScreenshotFormatDto = valueOf(format.uppercase())
    }
}

@Serializable
enum class ScreenshotStatusDto {
    QUEUED, PROCESSING, COMPLETED, FAILED;
    
    fun toDomain(): ScreenshotStatus = ScreenshotStatus.valueOf(name)
    
    companion object {
        fun fromDomain(status: ScreenshotStatus): ScreenshotStatusDto = valueOf(status.name)
    }
}

@Serializable
data class ScreenshotRequestDto(
    val url: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val fullPage: Boolean = false,
    val waitTime: Long? = null,
    val waitForSelector: String? = null,
    val quality: Int = 80,
    val format: ScreenshotFormatDto = ScreenshotFormatDto.PNG,
    val includeMetadata: Boolean = false
) {
    fun toDomain(): ScreenshotRequest = ScreenshotRequest(
        url = url,
        width = width,
        height = height,
        fullPage = fullPage,
        waitTime = waitTime,
        waitForSelector = waitForSelector,
        quality = quality,
        format = format.toDomain(),
        includeMetadata = includeMetadata
    )

    companion object {
        fun fromDomain(request: ScreenshotRequest): ScreenshotRequestDto = ScreenshotRequestDto(
            url = request.url,
            width = request.width,
            height = request.height,
            fullPage = request.fullPage,
            waitTime = request.waitTime,
            waitForSelector = request.waitForSelector,
            quality = request.quality,
            format = ScreenshotFormatDto.fromDomain(request.format),
            includeMetadata = request.includeMetadata
        )
    }
}

@Serializable
data class ScreenshotJobDto(
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
        fun fromDomain(job: ScreenshotJob): ScreenshotJobDto = ScreenshotJobDto(
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
