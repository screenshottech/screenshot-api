package dev.screenshotapi.infrastructure.adapters.output.queue.redis

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.infrastructure.adapters.output.queue.dto.ScreenshotJobQueueDto
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration

class RedisQueueAdapter(
    private val connection: StatefulRedisConnection<String, String>,
    private val queueKey: String = "screenshot_api:queue:screenshot_jobs",
    private val delayedQueueKey: String = "screenshot_api:queue:delayed_jobs"
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
    
    // Retry-related methods implementation
    override suspend fun enqueueDelayed(job: ScreenshotJob, delay: Duration) {
        try {
            val jobDto = ScreenshotJobQueueDto.fromDomain(job)
            val jobJson = json.encodeToString(ScreenshotJobQueueDto.serializer(), jobDto)
            val executeAt = Clock.System.now().plus(delay).toEpochMilliseconds()
            
            // Use Redis sorted set with score as execution timestamp
            connection.sync().zadd(delayedQueueKey, executeAt.toDouble(), jobJson)
            logger.info("Job ${job.id} enqueued for delayed execution in ${delay.inWholeSeconds}s")
        } catch (e: Exception) {
            logger.error("Failed to enqueue delayed job: ${job.id}", e)
            throw e
        }
    }
    
    override suspend fun dequeueReadyRetries(): List<ScreenshotJob> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            
            // Get all jobs that are ready to execute (score <= now)
            val readyJobs = connection.sync().zrangebyscore(delayedQueueKey, 0.0, now.toDouble())
            
            if (readyJobs.isEmpty()) {
                return emptyList()
            }
            
            // Remove the ready jobs from delayed queue and add to main queue
            val jobs = mutableListOf<ScreenshotJob>()
            for (jobJson in readyJobs) {
                try {
                    val jobDto = json.decodeFromString(ScreenshotJobQueueDto.serializer(), jobJson)
                    val job = jobDto.toDomain()
                    
                    // Remove from delayed queue and add to main queue
                    connection.sync().zrem(delayedQueueKey, jobJson)
                    connection.sync().lpush(queueKey, jobJson)
                    
                    jobs.add(job)
                    logger.debug("Moved delayed job ${job.id} to main queue")
                } catch (e: Exception) {
                    logger.error("Failed to process delayed job: $jobJson", e)
                    // Remove invalid job from delayed queue
                    connection.sync().zrem(delayedQueueKey, jobJson)
                }
            }
            
            logger.info("Moved ${jobs.size} delayed jobs to main queue")
            jobs
        } catch (e: Exception) {
            logger.error("Failed to dequeue ready retries", e)
            throw e
        }
    }
    
    override suspend fun requeueFailedJob(job: ScreenshotJob): Boolean {
        return try {
            val jobDto = ScreenshotJobQueueDto.fromDomain(job)
            val jobJson = json.encodeToString(ScreenshotJobQueueDto.serializer(), jobDto)
            
            // Add to the beginning of the queue for priority processing
            connection.sync().lpush(queueKey, jobJson)
            logger.info("Failed job ${job.id} requeued for retry")
            true
        } catch (e: Exception) {
            logger.error("Failed to requeue failed job: ${job.id}", e)
            false
        }
    }
    
    override suspend fun cancelDelayedJob(jobId: String): Boolean {
        return try {
            // Find the job in delayed queue by scanning for job ID
            val allDelayedJobs = connection.sync().zrange(delayedQueueKey, 0, -1)
            
            for (jobJson in allDelayedJobs) {
                try {
                    val jobDto = json.decodeFromString(ScreenshotJobQueueDto.serializer(), jobJson)
                    if (jobDto.id == jobId) {
                        val removed = connection.sync().zrem(delayedQueueKey, jobJson)
                        if (removed > 0) {
                            logger.info("Cancelled delayed job: $jobId")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse delayed job for cancellation: $jobJson", e)
                    // Continue scanning other jobs
                }
            }
            
            logger.debug("Delayed job not found for cancellation: $jobId")
            false
        } catch (e: Exception) {
            logger.error("Failed to cancel delayed job: $jobId", e)
            false
        }
    }
}
