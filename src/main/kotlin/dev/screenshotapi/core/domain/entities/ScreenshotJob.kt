package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant


data class ScreenshotJob(
    val id: String,
    val userId: String,
    val apiKeyId: String,
    val request: ScreenshotRequest,
    val status: ScreenshotStatus,
    val resultUrl: String? = null,
    val errorMessage: String? = null,
    val processingTimeMs: Long? = null,
    val webhookUrl: String? = null,
    val webhookSent: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val completedAt: Instant? = null
) {
    fun markAsProcessing(): ScreenshotJob = copy(status = ScreenshotStatus.PROCESSING)

    fun markAsCompleted(url: String, processingTime: Long): ScreenshotJob = copy(
        status = ScreenshotStatus.COMPLETED,
        resultUrl = url,
        processingTimeMs = processingTime,
        completedAt = Clock.System.now(),
    )

    fun markAsFailed(error: String, processingTime: Long): ScreenshotJob = copy(
        status = ScreenshotStatus.FAILED,
        errorMessage = error,
        processingTimeMs = processingTime,
        completedAt = Clock.System.now()
    )
}

enum class ScreenshotStatus {
    QUEUED, PROCESSING, COMPLETED, FAILED
}
