package dev.screenshotapi.infrastructure.adapters.input.rest.dto

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.usecases.screenshot.GetScreenshotStatusUseCase
import dev.screenshotapi.core.usecases.screenshot.ListScreenshotsUseCase
import dev.screenshotapi.core.usecases.screenshot.TakeScreenshotUseCase
import dev.screenshotapi.core.usecases.screenshot.BulkStatusResponse
import kotlinx.serialization.Serializable
import dev.screenshotapi.core.domain.entities.ScreenshotFormat as DomainFormat
import dev.screenshotapi.core.domain.entities.ScreenshotRequest as DomainScreenshotRequest

@Serializable
data class TakeScreenshotRequestDto(
    val url: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val fullPage: Boolean = false,
    val waitTime: Long? = null,
    val waitForSelector: String? = null,
    val quality: Int = 80,
    val format: String = "PNG",
    val webhookUrl: String? = null
)

@Serializable
data class TakeScreenshotResponseDto(
    val jobId: String,
    val status: String,
    val estimatedCompletion: String,
    val queuePosition: Int
)

@Serializable
data class GetScreenshotStatusResponseDto(
    val jobId: String,
    val status: String,
    val resultUrl: String?,
    val createdAt: String,
    val completedAt: String?,
    val processingTimeMs: Long?,
    val fileSizeBytes: Long?,
    val errorMessage: String?,
    val request: ScreenshotRequestDto
)

@Serializable
data class ListScreenshotsResponseDto(
    val screenshots: List<ScreenshotResponseDto>,
    val pagination: PaginationDto
)

@Serializable
data class ScreenshotResponseDto(
    val id: String,
    val status: String,
    val resultUrl: String?,
    val createdAt: String,
    val completedAt: String?,
    val processingTimeMs: Long?,
    val fileSizeBytes: Long?,
    val errorMessage: String?,
    val request: ScreenshotRequestDto
)

@Serializable
data class ScreenshotRequestDto(
    val url: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val fullPage: Boolean = false,
    val waitTime: Long? = null,
    val waitForSelector: String? = null,
    val quality: Int = 80,
    val format: String = "PNG"
)

@Serializable
data class PaginationDto(
    val page: Int,
    val limit: Int,
    val total: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

@Serializable
data class BulkStatusRequestDto(
    val jobIds: List<String>
)

@Serializable
data class BulkStatusJobResponseDto(
    val jobId: String,
    val status: String,
    val resultUrl: String? = null,
    val processingTimeMs: Long? = null,
    val fileSizeBytes: Long? = null,
    val completedAt: String? = null,
    val errorMessage: String? = null
)

@Serializable
data class BulkStatusResponseDto(
    val jobs: List<BulkStatusJobResponseDto>,
    val requestedAt: String,
    val totalJobs: Int,
    val foundJobs: Int
)

// Mapping functions for domain -> DTO
fun TakeScreenshotUseCase.Response.toDto() = TakeScreenshotResponseDto(
    jobId = jobId,
    status = status.name,
    estimatedCompletion = estimatedCompletion,
    queuePosition = queuePosition
)

fun GetScreenshotStatusUseCase.Response.toDto() = GetScreenshotStatusResponseDto(
    jobId = jobId,
    status = status.name,
    resultUrl = resultUrl,
    createdAt = createdAt.toString(),
    completedAt = completedAt?.toString(),
    processingTimeMs = processingTimeMs,
    fileSizeBytes = fileSizeBytes,
    errorMessage = errorMessage,
    request = request.toDto()
)

fun ListScreenshotsUseCase.Response.toDto() = ListScreenshotsResponseDto(
    screenshots = screenshots.map { it.toResponseDto() },
    pagination = PaginationDto(
        page = page,
        limit = limit,
        total = total,
        totalPages = ((total + limit - 1) / limit).toInt(),
        hasNext = (page * limit) < total,
        hasPrevious = page > 1
    )
)

fun BulkStatusResponse.toDto() = BulkStatusResponseDto(
    jobs = jobs.map { job ->
        BulkStatusJobResponseDto(
            jobId = job.jobId,
            status = job.status,
            resultUrl = job.resultUrl,
            processingTimeMs = job.processingTimeMs,
            fileSizeBytes = job.fileSizeBytes,
            completedAt = job.completedAt?.toString(),
            errorMessage = job.errorMessage
        )
    },
    requestedAt = requestedAt.toString(),
    totalJobs = totalJobs,
    foundJobs = foundJobs
)

// Mapping functions for DTO -> domain
fun TakeScreenshotRequestDto.toDomainRequest(userId: String, apiKeyId: String): TakeScreenshotUseCase.Request {
    val screenshotRequest = DomainScreenshotRequest(
        url = url,
        width = width,
        height = height,
        fullPage = fullPage,
        waitTime = waitTime,
        waitForSelector = waitForSelector,
        quality = quality,
        format = DomainFormat.fromString(format)
    )

    return TakeScreenshotUseCase.Request(
        userId = userId,
        apiKeyId = apiKeyId,
        screenshotRequest = screenshotRequest,
        webhookUrl = webhookUrl
    )
}

fun ScreenshotRequestDto.toDomain() = DomainScreenshotRequest(
    url = url,
    width = width,
    height = height,
    fullPage = fullPage,
    waitTime = waitTime,
    waitForSelector = waitForSelector,
    quality = quality,
    format = DomainFormat.fromString(format)
)

// Helper mapping functions
fun DomainScreenshotRequest.toDto() = ScreenshotRequestDto(
    url = url,
    width = width,
    height = height,
    fullPage = fullPage,
    waitTime = waitTime,
    waitForSelector = waitForSelector,
    quality = quality,
    format = format.name
)

fun ScreenshotJob.toResponseDto() = ScreenshotResponseDto(
    id = id,
    status = status.name,
    resultUrl = resultUrl,
    createdAt = createdAt.toString(),
    completedAt = completedAt?.toString(),
    processingTimeMs = processingTimeMs,
    fileSizeBytes = fileSizeBytes,
    errorMessage = errorMessage,
    request = request.toDto()
)
