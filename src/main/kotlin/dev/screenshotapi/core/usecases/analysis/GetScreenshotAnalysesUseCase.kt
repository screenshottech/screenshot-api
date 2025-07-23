package dev.screenshotapi.core.usecases.analysis

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.AnalysisStatus
import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import org.slf4j.LoggerFactory

/**
 * Get Screenshot Analyses Use Case - Retrieves all analysis jobs for a specific screenshot
 * 
 * This use case handles:
 * - Fetching all analyses associated with a screenshot job
 * - Access control (user can only see analyses for their own screenshots)
 * - Ordering analyses by creation date (newest first)
 * - Validation that the screenshot exists and belongs to the user
 */
class GetScreenshotAnalysesUseCase(
    private val analysisJobRepository: AnalysisJobRepository,
    private val screenshotRepository: ScreenshotRepository
) {
    private val logger = LoggerFactory.getLogger(GetScreenshotAnalysesUseCase::class.java)

    suspend operator fun invoke(request: Request): Response {
        logger.debug(
            "Getting analyses for screenshot ${request.screenshotJobId}, " +
            "user ${request.userId}"
        )
        
        // First verify that the screenshot exists and belongs to the user
        val screenshot = screenshotRepository.findByIdAndUserId(request.screenshotJobId, request.userId)
            ?: throw ResourceNotFoundException("Screenshot", request.screenshotJobId)
        
        // Get all analyses for this screenshot (to get total count)
        val allAnalyses = analysisJobRepository.findByScreenshotJobId(request.screenshotJobId)
        
        // Apply page-based pagination (following backend standard)
        val offset = (request.page - 1) * request.limit
        val paginatedAnalyses = allAnalyses.drop(offset).take(request.limit)
        
        logger.debug(
            "Found ${allAnalyses.size} total analyses for screenshot ${request.screenshotJobId}, " +
            "returning ${paginatedAnalyses.size} for page ${request.page} with limit ${request.limit}"
        )
        
        return Response(
            screenshotJobId = request.screenshotJobId,
            analyses = paginatedAnalyses.map { it.toSummary() },
            totalCount = allAnalyses.size,
            metadata = AnalysesMetadata(
                screenshotUrl = screenshot.resultUrl,
                screenshotStatus = screenshot.status.name,
                screenshotCreatedAt = screenshot.createdAt
            )
        )
    }

    data class Request(
        val screenshotJobId: String,
        val userId: String,
        val page: Int = 1,
        val limit: Int = 20
    )

    data class Response(
        val screenshotJobId: String,
        val analyses: List<AnalysisSummary>,
        val totalCount: Int,
        val metadata: AnalysesMetadata
    )

    data class AnalysisSummary(
        val id: String,
        val analysisType: AnalysisType,
        val status: AnalysisStatus,
        val resultData: String?,
        val confidence: Double?,
        val processingTimeMs: Long?,
        val tokensUsed: Int?,
        val costUsd: Double?,
        val errorMessage: String?,
        val createdAt: kotlinx.datetime.Instant,
        val completedAt: kotlinx.datetime.Instant?
    ) {
        val isCompleted: Boolean get() = status == AnalysisStatus.COMPLETED
        val hasResults: Boolean get() = !resultData.isNullOrBlank()
    }

    data class AnalysesMetadata(
        val screenshotUrl: String?,
        val screenshotStatus: String,
        val screenshotCreatedAt: kotlinx.datetime.Instant
    )

    private fun AnalysisJob.toSummary() = AnalysisSummary(
        id = id,
        analysisType = analysisType,
        status = status,
        resultData = resultData,
        confidence = confidence,
        processingTimeMs = processingTimeMs,
        tokensUsed = tokensUsed,
        costUsd = costUsd,
        errorMessage = errorMessage,
        createdAt = createdAt,
        completedAt = completedAt
    )
}