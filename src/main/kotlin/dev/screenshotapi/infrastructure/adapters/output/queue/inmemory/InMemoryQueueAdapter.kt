package dev.screenshotapi.infrastructure.adapters.output.queue.inmemory

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory.InMemoryDatabase

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
}
