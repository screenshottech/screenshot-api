package dev.screenshotapi.infrastructure.adapters.output.queue.redis

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.repositories.QueueRepository
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.json.Json

class RedisQueueAdapter(private val connection: StatefulRedisConnection<String, String>) : QueueRepository {
    private val queueKey = "screenshot:jobs"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun enqueue(job: ScreenshotJob) {
        val jobJson = json.encodeToString(job)
        connection.sync().lpush(queueKey, jobJson)
    }

    override suspend fun dequeue(): ScreenshotJob? {
        val jobJson = connection.sync().rpop(queueKey) ?: return null
        return json.decodeFromString<ScreenshotJob>(jobJson)
    }

    override suspend fun peek(): ScreenshotJob? {
        val jobJson = connection.sync().lindex(queueKey, -1) ?: return null
        return json.decodeFromString<ScreenshotJob>(jobJson)
    }

    override suspend fun size(): Long {
        return connection.sync().llen(queueKey)
    }

}
