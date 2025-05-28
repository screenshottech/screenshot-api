package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.ScreenshotJob

interface QueueRepository {
    suspend fun enqueue(job: ScreenshotJob)
    suspend fun dequeue(): ScreenshotJob?
    suspend fun size(): Long
    suspend fun peek(): ScreenshotJob?
}
