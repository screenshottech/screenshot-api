package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.AnalysisStatus

/**
 * Analysis Job Repository - Port for analysis job persistence
 * 
 * Manages the lifecycle and querying of AI analysis jobs.
 * Follows the repository pattern with clean architecture boundaries.
 */
interface AnalysisJobRepository {
    
    /**
     * Save or update an analysis job
     */
    suspend fun save(job: AnalysisJob): AnalysisJob
    
    /**
     * Find analysis job by ID
     */
    suspend fun findById(id: String): AnalysisJob?
    
    /**
     * Find analysis job by ID and user ID (security constraint)
     */
    suspend fun findByIdAndUserId(id: String, userId: String): AnalysisJob?
    
    /**
     * Find analysis jobs by screenshot job ID
     */
    suspend fun findByScreenshotJobId(screenshotJobId: String): List<AnalysisJob>
    
    /**
     * Find analysis jobs by user ID with pagination
     */
    suspend fun findByUserId(
        userId: String, 
        page: Int = 1, 
        limit: Int = 20,
        status: AnalysisStatus? = null
    ): List<AnalysisJob>
    
    /**
     * Count analysis jobs by user ID with optional status filter
     */
    suspend fun countByUserId(userId: String, status: AnalysisStatus? = null): Long
    
    /**
     * Find next queued job for processing
     */
    suspend fun findNextQueuedJob(): AnalysisJob?
    
    /**
     * Find jobs by status (for monitoring and cleanup)
     */
    suspend fun findByStatus(status: AnalysisStatus, limit: Int = 100): List<AnalysisJob>
    
    /**
     * Update job status with optional error message
     */
    suspend fun updateStatus(
        id: String, 
        status: AnalysisStatus, 
        errorMessage: String? = null
    ): Boolean
    
    /**
     * Delete analysis job by ID
     */
    suspend fun deleteById(id: String): Boolean
    
    /**
     * Get analysis statistics for a user
     */
    suspend fun getAnalysisStats(userId: String): AnalysisStats
}

/**
 * Analysis Statistics - Summary metrics for user analysis usage
 */
data class AnalysisStats(
    val totalAnalyses: Long,
    val completedAnalyses: Long,
    val failedAnalyses: Long,
    val totalCostUsd: Double,
    val averageProcessingTimeMs: Double?,
    val analysesByType: Map<String, Long>
) {
    val successRate: Double
        get() = if (totalAnalyses > 0) (completedAnalyses.toDouble() / totalAnalyses.toDouble()) * 100 else 0.0
}