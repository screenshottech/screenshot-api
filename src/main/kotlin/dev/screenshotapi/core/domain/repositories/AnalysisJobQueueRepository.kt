package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.AnalysisJob

/**
 * Analysis Job Queue Repository - Port for analysis job queue operations
 * 
 * This interface defines queue operations for analysis jobs, separate from
 * persistence operations to maintain clean architecture boundaries.
 * 
 * Using a dedicated queue prevents race conditions that occur when multiple
 * workers poll the database directly.
 */
interface AnalysisJobQueueRepository {
    
    /**
     * Enqueue an analysis job for processing
     */
    suspend fun enqueue(job: AnalysisJob)
    
    /**
     * Dequeue the next analysis job for processing
     * This operation is atomic - only one worker can get each job
     */
    suspend fun dequeue(): AnalysisJob?
    
    /**
     * Get the current queue size
     */
    suspend fun size(): Long
    
    /**
     * Peek at the next job without removing it from the queue
     */
    suspend fun peek(): AnalysisJob?
    
    /**
     * Clear all jobs from the queue (for testing/maintenance)
     */
    suspend fun clear()
}