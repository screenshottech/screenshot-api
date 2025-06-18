package dev.screenshotapi.infrastructure.adapters.output.queue.inmemory

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory.InMemoryDatabase
import kotlin.time.Duration

class InMemoryQueueAdapter : QueueRepository {
    override suspend fun enqueue(job: ScreenshotJob) {
        InMemoryDatabase.enqueueJob(job)
    }

    override suspend fun dequeue(): ScreenshotJob? {
        return InMemoryDatabase.dequeueJob()
    }

    override suspend fun size(): Long {
        return InMemoryDatabase.queueSize()
    }

    override suspend fun peek(): ScreenshotJob? {
        // Implementation for peek without removing
        return null
    }

    override suspend fun enqueueDelayed(job: ScreenshotJob, delay: Duration) {
        // For in-memory implementation, we'll just enqueue immediately
        // In a real implementation, this would schedule the job for delayed execution
        InMemoryDatabase.enqueueJob(job)
    }

    override suspend fun dequeueReadyRetries(): List<ScreenshotJob> {
        // For in-memory implementation, return empty list
        // In a real implementation, this would return jobs ready for retry from delayed queue
        return emptyList()
    }

    override suspend fun requeueFailedJob(job: ScreenshotJob): Boolean {
        // For in-memory implementation, just enqueue the job
        InMemoryDatabase.enqueueJob(job)
        return true
    }

    override suspend fun cancelDelayedJob(jobId: String): Boolean {
        // For in-memory implementation, return false (no delayed queue tracking)
        return false
    }
}
