package dev.screenshotapi.infrastructure.adapters.input.rest.dto

import kotlinx.serialization.Serializable

data class TakeScreenshotResponse(
    val jobId: String,
    val status: ScreenshotStatus,
    val estimatedCompletion: String,
    val queuePosition: Int
)

data class GetScreenshotStatusResponse(
    val job: ScreenshotJob
)

data class ListScreenshotsResponse(
    val screenshots: List<ScreenshotJob>,
    val page: Int,
    val limit: Int,
    val total: Long
)

@Serializable
data class ScreenshotJob(
    val id: String,
    val status: ScreenshotStatus,
    val resultUrl: String?,
    val createdAt: String,
    val completedAt: String?,
    val processingTimeMs: Long?,
    val errorMessage: String?,
    val request: ScreenshotRequest
)

@Serializable
data class ScreenshotRequest(
    val url: String,
    val width: Int,
    val height: Int,
    val fullPage: Boolean,
    val waitTime: Long?,
    val waitForSelector: String?,
    val quality: Int,
    val format: ScreenshotFormat
)

enum class ScreenshotFormat { PNG, JPEG, WEBP, PDF }
enum class ScreenshotStatus { PENDING, PROCESSING, COMPLETED, FAILED }

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
    val url: String? = null,
    val createdAt: String,
    val completedAt: String? = null,
    val error: String? = null
)

@Serializable
data class ListScreenshotsResponseDto(
    val screenshots: List<ScreenshotResponseDto>,
    val page: Int,
    val limit: Int,
    val total: Long
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
) {
    fun toDomain(): ScreenshotRequest = ScreenshotRequest(
        url = url.trim(),
        width = width,
        height = height,
        fullPage = fullPage,
        waitTime = waitTime,
        waitForSelector = waitForSelector?.takeIf { it.isNotBlank() },
        quality = quality,
        format = ScreenshotFormat.valueOf(format.uppercase())
    )
}

@Serializable
data class ScreenshotResponseDto(
    val jobId: String,
    val status: String,
    val url: String? = null,
    val createdAt: String,
    val completedAt: String? = null,
    val processingTimeMs: Long? = null,
    val error: String? = null,
    val request: ScreenshotRequestSummaryDto
)

@Serializable
data class ScreenshotRequestSummaryDto(
    val url: String,
    val width: Int,
    val height: Int,
    val fullPage: Boolean,
    val format: String
)

@Serializable
data class ScreenshotsListResponseDto(
    val screenshots: List<ScreenshotResponseDto>,
    val pagination: PaginationDto
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

fun TakeScreenshotResponse.toDto() = TakeScreenshotResponseDto(
    jobId = jobId,
    status = status.name.lowercase(),
    estimatedCompletion = estimatedCompletion,
    queuePosition = queuePosition
)

fun GetScreenshotStatusResponse.toDto() = ScreenshotResponseDto(
    jobId = job.id,
    status = job.status.name.lowercase(),
    url = job.resultUrl,
    createdAt = job.createdAt,
    completedAt = job.completedAt,
    processingTimeMs = job.processingTimeMs,
    error = job.errorMessage,
    request = ScreenshotRequestSummaryDto(
        url = job.request.url,
        width = job.request.width,
        height = job.request.height,
        fullPage = job.request.fullPage,
        format = job.request.format.name
    )
)

fun ListScreenshotsResponse.toDto() = ScreenshotsListResponseDto(
    screenshots = screenshots.map { job ->
        ScreenshotResponseDto(
            jobId = job.id,
            status = job.status.name.lowercase(),
            url = job.resultUrl,
            createdAt = job.createdAt.toString(),
            completedAt = job.completedAt?.toString(),
            processingTimeMs = job.processingTimeMs,
            error = job.errorMessage,
            request = ScreenshotRequestSummaryDto(
                url = job.request.url,
                width = job.request.width,
                height = job.request.height,
                fullPage = job.request.fullPage,
                format = job.request.format.name
            )
        )
    },
    pagination = PaginationDto(
        page = page,
        limit = limit,
        total = total,
        totalPages = ((total + limit - 1) / limit).toInt(),
        hasNext = page * limit < total,
        hasPrevious = page > 1
    )
)
