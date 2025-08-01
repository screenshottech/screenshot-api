package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.AnalysisStatus
import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import dev.screenshotapi.core.domain.repositories.AnalysisStats
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory Analysis Job Repository
 * 
 * Development and testing implementation with realistic mock data.
 * Maintains thread-safety with ConcurrentHashMap.
 */
class InMemoryAnalysisJobRepository : AnalysisJobRepository {
    
    private val logger = LoggerFactory.getLogger(InMemoryAnalysisJobRepository::class.java)
    private val jobs = ConcurrentHashMap<String, AnalysisJob>()

    init {
        // Initialize with realistic mock data for development
        initializeMockData()
    }

    override suspend fun save(job: AnalysisJob): AnalysisJob {
        jobs[job.id] = job
        logger.debug("Saved analysis job: ${job.id}")
        return job
    }

    override suspend fun findById(id: String): AnalysisJob? {
        return jobs[id]
    }

    override suspend fun findByIdAndUserId(id: String, userId: String): AnalysisJob? {
        return jobs[id]?.takeIf { it.userId == userId }
    }

    override suspend fun findByScreenshotJobId(screenshotJobId: String): List<AnalysisJob> {
        return jobs.values
            .filter { it.screenshotJobId == screenshotJobId }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun findByUserId(
        userId: String, 
        page: Int, 
        limit: Int,
        status: AnalysisStatus?
    ): List<AnalysisJob> {
        return jobs.values
            .filter { it.userId == userId }
            .filter { status == null || it.status == status }
            .sortedByDescending { it.createdAt }
            .drop((page - 1) * limit)
            .take(limit)
    }

    override suspend fun countByUserId(userId: String, status: AnalysisStatus?): Long {
        return jobs.values
            .filter { it.userId == userId }
            .filter { status == null || it.status == status }
            .size
            .toLong()
    }

    override suspend fun findNextQueuedJob(): AnalysisJob? {
        return jobs.values
            .filter { it.status == AnalysisStatus.QUEUED }
            .minByOrNull { it.createdAt }
    }

    override suspend fun findByStatus(status: AnalysisStatus, limit: Int): List<AnalysisJob> {
        return jobs.values
            .filter { it.status == status }
            .sortedBy { it.createdAt }
            .take(limit)
    }

    override suspend fun updateStatus(
        id: String, 
        status: AnalysisStatus, 
        errorMessage: String?
    ): Boolean {
        val job = jobs[id] ?: return false
        
        val updatedJob = job.copy(
            status = status,
            errorMessage = errorMessage,
            updatedAt = Clock.System.now(),
            startedAt = if (status == AnalysisStatus.PROCESSING) Clock.System.now() else job.startedAt,
            completedAt = if (status.isTerminal()) Clock.System.now() else job.completedAt
        )
        
        jobs[id] = updatedJob
        logger.debug("Updated analysis job status: $id -> $status")
        return true
    }

    override suspend fun deleteById(id: String): Boolean {
        return jobs.remove(id) != null
    }

    override suspend fun getAnalysisStats(userId: String): AnalysisStats {
        val userJobs = jobs.values.filter { it.userId == userId }
        
        val totalAnalyses = userJobs.size.toLong()
        val completedAnalyses = userJobs.count { it.status == AnalysisStatus.COMPLETED }.toLong()
        val failedAnalyses = userJobs.count { it.status == AnalysisStatus.FAILED }.toLong()
        val totalCost = userJobs.mapNotNull { it.costUsd }.sum()
        val avgProcessingTime = userJobs.mapNotNull { it.processingTimeMs }.average().takeIf { !it.isNaN() }
        
        val analysesByType = userJobs
            .groupBy { it.analysisType.displayName }
            .mapValues { it.value.size.toLong() }
        
        return AnalysisStats(
            totalAnalyses = totalAnalyses,
            completedAnalyses = completedAnalyses,
            failedAnalyses = failedAnalyses,
            totalCostUsd = totalCost,
            averageProcessingTimeMs = avgProcessingTime,
            analysesByType = analysesByType
        )
    }

    /**
     * Extension to check if status is terminal
     */
    private fun AnalysisStatus.isTerminal(): Boolean = this in listOf(
        AnalysisStatus.COMPLETED, 
        AnalysisStatus.FAILED, 
        AnalysisStatus.CANCELLED
    )

    /**
     * Initialize with realistic mock data for development
     */
    private fun initializeMockData() {
        val now = Clock.System.now()
        
        // Mock completed analysis
        val completedAnalysis = AnalysisJob(
            id = "analysis-001",
            userId = "test-user-123",
            screenshotJobId = "screenshot-001", 
            screenshotUrl = "https://test-bucket.s3.amazonaws.com/screenshots/screenshot-001.png",
            analysisType = AnalysisType.BASIC_OCR,
            status = AnalysisStatus.COMPLETED,
            language = "en",
            resultData = """
                {
                    "extractedText": "Welcome to Our Service\nGet started with your free trial today!\nContact us: support@example.com\nPhone: (555) 123-4567",
                    "confidence": 0.94,
                    "wordCount": 16,
                    "analysis": {
                        "sentiment": "positive",
                        "entities": [
                            {"type": "email", "value": "support@example.com", "confidence": 0.99},
                            {"type": "phone", "value": "(555) 123-4567", "confidence": 0.97}
                        ]
                    }
                }
            """.trimIndent(),
            confidence = 0.94,
            processingTimeMs = 2340L,
            tokensUsed = 850,
            costUsd = 0.0042,
            createdAt = now.minus(kotlin.time.Duration.parse("2h")),
            startedAt = now.minus(kotlin.time.Duration.parse("2h")).plus(kotlin.time.Duration.parse("5s")),
            completedAt = now.minus(kotlin.time.Duration.parse("2h")).plus(kotlin.time.Duration.parse("7s")),
            metadata = mapOf(
                "model" to "claude-3-haiku",
                "region" to "us-east-2",
                "imageSize" to "1920x1080"
            )
        )
        
        // Mock processing analysis
        val processingAnalysis = AnalysisJob(
            id = "analysis-002",
            userId = "test-user-123",
            screenshotJobId = "screenshot-002",
            screenshotUrl = "https://test-bucket.s3.amazonaws.com/screenshots/screenshot-002.png",
            analysisType = AnalysisType.UX_ANALYSIS,
            status = AnalysisStatus.PROCESSING,
            language = "en",
            createdAt = now.minus(kotlin.time.Duration.parse("30s")),
            startedAt = now.minus(kotlin.time.Duration.parse("25s")),
            metadata = mapOf(
                "model" to "claude-3-haiku",
                "region" to "us-east-2"
            )
        )
        
        // Mock queued analysis
        val queuedAnalysis = AnalysisJob(
            id = "analysis-003",
            userId = "test-user-456",
            screenshotJobId = "screenshot-003",
            screenshotUrl = "https://test-bucket.s3.amazonaws.com/screenshots/screenshot-003.png",
            analysisType = AnalysisType.CONTENT_SUMMARY,
            status = AnalysisStatus.QUEUED,
            language = "es",
            webhookUrl = "https://webhook.example.com/analysis-complete",
            createdAt = now.minus(kotlin.time.Duration.parse("10s"))
        )
        
        // Mock failed analysis
        val failedAnalysis = AnalysisJob(
            id = "analysis-004",
            userId = "test-user-123",
            screenshotJobId = "screenshot-004",
            screenshotUrl = "https://test-bucket.s3.amazonaws.com/screenshots/screenshot-004.png",
            analysisType = AnalysisType.GENERAL,
            status = AnalysisStatus.FAILED,
            language = "en",
            errorMessage = "Image processing failed: Unable to decode image format",
            createdAt = now.minus(kotlin.time.Duration.parse("1h")),
            startedAt = now.minus(kotlin.time.Duration.parse("1h")).plus(kotlin.time.Duration.parse("2s")),
            completedAt = now.minus(kotlin.time.Duration.parse("1h")).plus(kotlin.time.Duration.parse("8s")),
            processingTimeMs = 6000L
        )
        
        // Add mock data to repository
        jobs[completedAnalysis.id] = completedAnalysis
        jobs[processingAnalysis.id] = processingAnalysis
        jobs[queuedAnalysis.id] = queuedAnalysis
        jobs[failedAnalysis.id] = failedAnalysis
        
        logger.info("Initialized InMemoryAnalysisJobRepository with ${jobs.size} mock analysis jobs")
    }
}