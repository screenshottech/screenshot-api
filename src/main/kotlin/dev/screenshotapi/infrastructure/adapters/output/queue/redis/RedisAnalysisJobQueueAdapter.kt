package dev.screenshotapi.infrastructure.adapters.output.queue.redis

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.repositories.AnalysisJobQueueRepository
import dev.screenshotapi.infrastructure.adapters.output.queue.dto.AnalysisJobQueueDto
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Redis Queue Adapter for Analysis Jobs
 * 
 * This adapter handles queuing and dequeuing of analysis jobs using Redis,
 * preventing race conditions that occur when multiple workers poll the database directly.
 */
class RedisAnalysisJobQueueAdapter(
    private val connection: StatefulRedisConnection<String, String>,
    private val queueKey: String = "screenshot_api:queue:analysis_jobs"
) : AnalysisJobQueueRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Enqueues an analysis job to Redis queue
     */
    override suspend fun enqueue(job: AnalysisJob) {
        try {
            val jobDto = AnalysisJobQueueDto.fromDomain(job)
            val jobJson = json.encodeToString(AnalysisJobQueueDto.serializer(), jobDto)
            
            // Use LPUSH to add to the left side (head) of the list
            connection.sync().lpush(queueKey, jobJson)
            
            logger.debug("Enqueued analysis job to Redis: ${job.id}")
        } catch (e: Exception) {
            logger.error("Failed to enqueue analysis job: ${job.id}", e)
            throw e
        }
    }

    /**
     * Dequeues an analysis job from Redis queue
     * This is atomic - only one worker can get each job
     */
    override suspend fun dequeue(): AnalysisJob? {
        return try {
            // Use RPOP to remove from the right side (tail) of the list
            // This ensures FIFO (First In, First Out) behavior
            val jobJson = connection.sync().rpop(queueKey) ?: return null
            
            val jobDto = json.decodeFromString(AnalysisJobQueueDto.serializer(), jobJson)
            val job = jobDto.toDomain()
            
            logger.debug("Dequeued analysis job from Redis: ${job.id}")
            job
        } catch (e: Exception) {
            logger.error("Failed to dequeue analysis job", e)
            throw e
        }
    }

    /**
     * Gets the current queue size without removing items
     */
    override suspend fun size(): Long {
        return try {
            connection.sync().llen(queueKey)
        } catch (e: Exception) {
            logger.error("Failed to get analysis queue size", e)
            throw e
        }
    }

    /**
     * Peeks at the next job without removing it from the queue
     */
    override suspend fun peek(): AnalysisJob? {
        return try {
            val jobJson = connection.sync().lindex(queueKey, -1) ?: return null
            val jobDto = json.decodeFromString(AnalysisJobQueueDto.serializer(), jobJson)
            jobDto.toDomain()
        } catch (e: Exception) {
            logger.error("Failed to peek analysis job", e)
            throw e
        }
    }

    /**
     * Clears all jobs from the queue (for testing/maintenance)
     */
    override suspend fun clear() {
        try {
            connection.sync().del(queueKey)
            logger.info("Cleared analysis job queue")
        } catch (e: Exception) {
            logger.error("Failed to clear analysis job queue", e)
            throw e
        }
    }
}