package dev.screenshotapi.infrastructure.adapters.output.queue.redis

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.infrastructure.adapters.output.queue.dto.ScreenshotJobQueueDto
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class RedisQueueAdapter(
    private val connection: StatefulRedisConnection<String, String>,
    private val queueKey: String = "screenshot_api:queue:screenshot_jobs"
) : QueueRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun enqueue(job: ScreenshotJob) {
        try {
            val jobDto = ScreenshotJobQueueDto.fromDomain(job)
            val jobJson = json.encodeToString(ScreenshotJobQueueDto.serializer(), jobDto)
            connection.sync().lpush(queueKey, jobJson)
        } catch (e: Exception) {
            logger.error("Failed to enqueue job: ${job.id}", e)
            throw e
        }
    }

    override suspend fun dequeue(): ScreenshotJob? {
        try {
            val jobJson = connection.sync().rpop(queueKey) ?: return null
            val jobDto = json.decodeFromString(ScreenshotJobQueueDto.serializer(), jobJson)
            return jobDto.toDomain()
        } catch (e: Exception) {
            logger.error("Failed to dequeue job", e)
            throw e
        }
    }

    override suspend fun peek(): ScreenshotJob? {
        try {
            val jobJson = connection.sync().lindex(queueKey, -1) ?: return null
            val jobDto = json.decodeFromString(ScreenshotJobQueueDto.serializer(), jobJson)
            return jobDto.toDomain()
        } catch (e: Exception) {
            logger.error("Failed to peek job", e)
            throw e
        }
    }

    override suspend fun size(): Long {
        return try {
            connection.sync().llen(queueKey)
        } catch (e: Exception) {
            logger.error("Failed to get queue size", e)
            throw e
        }
    }
}
