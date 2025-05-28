package dev.screenshotapi.infrastructure.adapters.output.queue.redis

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.repositories.QueueRepository
import io.lettuce.core.api.StatefulRedisConnection

class RedisQueueAdapter(private val connection: StatefulRedisConnection<String, String>) : QueueRepository {
    override suspend fun enqueue(job: ScreenshotJob) {
        TODO("Redis implementation not yet completed")
    }

    override suspend fun dequeue(): ScreenshotJob? {
        TODO("Redis implementation not yet completed")
    }

    override suspend fun peek(): ScreenshotJob? {
        TODO("Redis implementation not yet completed")
    }

    override suspend fun size(): Long {
        TODO("Redis implementation not yet completed")
    }

}
