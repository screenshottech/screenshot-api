package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import kotlin.time.Duration

interface QueueRepository {
    suspend fun enqueue(job: ScreenshotJob)
    suspend fun dequeue(): ScreenshotJob?
    suspend fun size(): Long
    suspend fun peek(): ScreenshotJob?
    
    // Retry-related methods
    suspend fun enqueueDelayed(job: ScreenshotJob, delay: Duration)
    suspend fun dequeueReadyRetries(): List<ScreenshotJob>
    suspend fun requeueFailedJob(job: ScreenshotJob): Boolean
    suspend fun cancelDelayedJob(jobId: String): Boolean
}
