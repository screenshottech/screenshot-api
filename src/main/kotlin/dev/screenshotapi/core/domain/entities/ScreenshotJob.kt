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
    val lockedAt: Instant? = null,
    // OCR Integration - GitHub Issue #4
    val ocrResultId: String? = null,
    val ocrRequested: Boolean = false,
    // Page metadata
    val metadata: PageMetadata? = null
) {
    fun markAsProcessing(): ScreenshotJob = copy(
        status = ScreenshotStatus.PROCESSING,
        updatedAt = Clock.System.now()
    )

    fun markAsCompleted(url: String, processingTime: Long, fileSize: Long? = null, metadata: PageMetadata? = null): ScreenshotJob = copy(
        status = ScreenshotStatus.COMPLETED,
        resultUrl = url,
        processingTimeMs = processingTime,
        fileSizeBytes = fileSize,
        metadata = metadata,
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

    companion object {
        /**
         * Creates a ScreenshotJob for OCR operations on uploaded images.
         * For OCR jobs, we create a synthetic ScreenshotRequest since the domain
         * is built around screenshots, but OCR is conceptually similar work.
         * 
         * @param apiKeyId Required API key ID (controller should resolve from auth context)
         */
        fun createOcrJob(
            id: String,
            userId: String,
            apiKeyId: String,
            ocrRequest: OcrRequest,
            webhookUrl: String? = null
        ): ScreenshotJob {
            // Create a synthetic ScreenshotRequest for OCR jobs
            // We'll use a special "ocr://" URL scheme to distinguish from real URLs
            val syntheticRequest = ScreenshotRequest(
                url = "ocr://image/${ocrRequest.id}",
                width = 1920, // Default dimensions for OCR context
                height = 1080,
                fullPage = false,
                format = ScreenshotFormat.PNG // OCR typically works with PNG
            )

            return ScreenshotJob(
                id = id,
                userId = userId,
                apiKeyId = apiKeyId,
                request = syntheticRequest,
                status = ScreenshotStatus.QUEUED,
                jobType = JobType.OCR,
                webhookUrl = webhookUrl,
                ocrRequested = true, // Mark as OCR operation
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
        }
    }
}
