package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

enum class ScreenshotStatus {
    QUEUED, PROCESSING, COMPLETED, FAILED
}

data class ScreenshotJob(
    val id: String,
    val userId: String,
    val apiKeyId: String,
    val request: ScreenshotRequest,
    val status: ScreenshotStatus,
    val jobType: JobType = JobType.SCREENSHOT, // Default to screenshot for backward compatibility
    val resultUrl: String? = null,
    val errorMessage: String? = null,
    val processingTimeMs: Long? = null,
    val fileSizeBytes: Long? = null, // New field for file size
    val webhookUrl: String? = null,
    val webhookSent: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val completedAt: Instant? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val nextRetryAt: Instant? = null,
    val lastFailureReason: String? = null,
    val isRetryable: Boolean = true,
    val retryType: RetryType = RetryType.AUTOMATIC,
    val lockedBy: String? = null,
    val lockedAt: Instant? = null
) {
    fun markAsProcessing(): ScreenshotJob = copy(
        status = ScreenshotStatus.PROCESSING,
        updatedAt = Clock.System.now()
    )

    fun markAsCompleted(url: String, processingTime: Long, fileSize: Long? = null): ScreenshotJob = copy(
        status = ScreenshotStatus.COMPLETED,
        resultUrl = url,
        processingTimeMs = processingTime,
        fileSizeBytes = fileSize,
        completedAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    fun markAsFailed(error: String, processingTime: Long): ScreenshotJob = copy(
        status = ScreenshotStatus.FAILED,
        errorMessage = error,
        processingTimeMs = processingTime,
        completedAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    fun markWebhookSent(): ScreenshotJob = copy(
        webhookSent = true,
        updatedAt = Clock.System.now()
    )
    
    fun canRetry(): Boolean = isRetryable && retryCount < maxRetries
    
    fun isStuck(): Boolean = status == ScreenshotStatus.PROCESSING && 
        updatedAt.plus(30.minutes) < Clock.System.now()
    
    fun isLocked(): Boolean = lockedBy != null && 
        lockedAt?.plus(5.minutes) ?: Instant.DISTANT_PAST > Clock.System.now()
    
    fun scheduleRetry(reason: String, delay: kotlin.time.Duration): ScreenshotJob = copy(
        status = ScreenshotStatus.QUEUED,
        retryCount = retryCount + 1,
        nextRetryAt = Clock.System.now() + delay,
        lastFailureReason = reason,
        errorMessage = reason,
        updatedAt = Clock.System.now(),
        lockedBy = null,
        lockedAt = null
    )
    
    fun markAsNonRetryable(reason: String): ScreenshotJob = copy(
        isRetryable = false,
        lastFailureReason = reason,
        errorMessage = reason,
        updatedAt = Clock.System.now()
    )
    
    fun resetForManualRetry(): ScreenshotJob = copy(
        status = ScreenshotStatus.QUEUED,
        retryCount = 0,
        retryType = RetryType.MANUAL,
        isRetryable = true,
        errorMessage = null,
        lastFailureReason = "Manual retry requested",
        updatedAt = Clock.System.now(),
        lockedBy = null,
        lockedAt = null,
        nextRetryAt = null
    )
    
    fun lock(workerId: String): ScreenshotJob = copy(
        lockedBy = workerId,
        lockedAt = Clock.System.now()
    )
    
    fun unlock(): ScreenshotJob = copy(
        lockedBy = null,
        lockedAt = null
    )
}
