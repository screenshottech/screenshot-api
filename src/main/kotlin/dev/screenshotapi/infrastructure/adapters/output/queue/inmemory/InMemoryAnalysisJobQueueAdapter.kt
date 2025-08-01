package dev.screenshotapi.infrastructure.adapters.output.queue.inmemory

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.repositories.AnalysisJobQueueRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedBlockingQueue

/**
 * In-Memory Analysis Job Queue Adapter
 * 
 * Simple in-memory implementation for development and testing.
 * Uses thread-safe LinkedBlockingQueue for FIFO processing.
 */
class InMemoryAnalysisJobQueueAdapter : AnalysisJobQueueRepository {
    
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val queue = LinkedBlockingQueue<AnalysisJob>()

    override suspend fun enqueue(job: AnalysisJob) {
        try {
            queue.offer(job)
            logger.debug("Enqueued analysis job to in-memory queue: ${job.id}")
        } catch (e: Exception) {
            logger.error("Failed to enqueue analysis job: ${job.id}", e)
            throw e
        }
    }

    override suspend fun dequeue(): AnalysisJob? {
        return try {
            val job = queue.poll()
            if (job != null) {
                logger.debug("Dequeued analysis job from in-memory queue: ${job.id}")
            }
            job
        } catch (e: Exception) {
            logger.error("Failed to dequeue analysis job", e)
            throw e
        }
    }

    override suspend fun size(): Long {
        return queue.size.toLong()
    }

    override suspend fun peek(): AnalysisJob? {
        return queue.peek()
    }

    override suspend fun clear() {
        queue.clear()
        logger.info("Cleared in-memory analysis job queue")
    }
}