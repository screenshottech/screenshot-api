package dev.screenshotapi.core.usecases.analysis

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.AnalysisStatus
import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import org.slf4j.LoggerFactory

/**
 * List Analyses Use Case - Retrieves paginated list of user's analysis jobs
 * 
 * This use case handles:
 * - Paginated analysis job listing for a user
 * - Optional filtering by status or analysis type
 * - Summary statistics and metadata
 * - Proper access control (user can only see their own analyses)
 */
class ListAnalysesUseCase(
    private val analysisJobRepository: AnalysisJobRepository
) {
    private val logger = LoggerFactory.getLogger(ListAnalysesUseCase::class.java)

    suspend operator fun invoke(request: Request): Response {
        logger.debug(
            "Listing analyses for user ${request.userId}, " +
            "page ${request.page}, limit ${request.limit}, " +
            "status filter: ${request.status}"
        )
        
        // Get paginated analyses for user
        val analyses = analysisJobRepository.findByUserId(
            userId = request.userId,
            page = request.page,
            limit = request.limit,
            status = request.status
        )
        
        // Get total count for pagination
        val totalCount = analysisJobRepository.countByUserId(
            userId = request.userId,
            status = request.status
        )
        
        // Get user's analysis statistics
        val stats = analysisJobRepository.getAnalysisStats(request.userId)
        
        logger.debug(
            "Found ${analyses.size} analyses for user ${request.userId}, " +
            "total: $totalCount"
        )
        
        return Response(
            analyses = analyses.map { it.toSummaryDto() },
            pagination = PaginationInfo(
                page = request.page,
                limit = request.limit,
                total = totalCount,
                totalPages = ((totalCount + request.limit - 1) / request.limit).toInt(),
                hasNext = (request.page * request.limit) < totalCount,
                hasPrevious = request.page > 1
            ),
            statistics = stats.toDto()
        )
    }

    data class Request(
        val userId: String,
        val page: Int = 1,
        val limit: Int = 20,
        val status: AnalysisStatus? = null
    ) {
        init {
            require(page > 0) { "Page must be greater than 0" }
            require(limit in 1..100) { "Limit must be between 1 and 100" }
        }
    }

    data class Response(
        val analyses: List<AnalysisSummary>,
        val pagination: PaginationInfo,
        val statistics: AnalysisStatsSummary
    )

    /**
     * Analysis summary for list view
     */
    data class AnalysisSummary(
        val analysisJobId: String,
        val screenshotJobId: String,
        val analysisType: AnalysisType,
        val status: AnalysisStatus,
        val language: String,
        val hasResults: Boolean,
        val confidence: Double?,
        val processingTimeMs: Long?,
        val costUsd: Double?,
        val errorMessage: String?,
        val createdAt: String,
        val completedAt: String?
    ) {
        val statusDescription: String
            get() = when (status) {
                AnalysisStatus.QUEUED -> "Queued"
                AnalysisStatus.PROCESSING -> "Processing"
                AnalysisStatus.COMPLETED -> "Completed"
                AnalysisStatus.FAILED -> "Failed"
                AnalysisStatus.CANCELLED -> "Cancelled"
            }
    }

    /**
     * Pagination information
     */
    data class PaginationInfo(
        val page: Int,
        val limit: Int,
        val total: Long,
        val totalPages: Int,
        val hasNext: Boolean,
        val hasPrevious: Boolean
    )

    /**
     * Analysis statistics summary
     */
    data class AnalysisStatsSummary(
        val totalAnalyses: Long,
        val completedAnalyses: Long,
        val failedAnalyses: Long,
        val successRate: Double,
        val totalCostUsd: Double,
        val averageProcessingTimeMs: Double?,
        val analysesByType: Map<String, Long>
    )

    /**
     * Extension function to convert AnalysisJob to summary
     */
    private fun AnalysisJob.toSummaryDto(): AnalysisSummary {
        return AnalysisSummary(
            analysisJobId = id,
            screenshotJobId = screenshotJobId,
            analysisType = analysisType,
            status = status,
            language = language,
            hasResults = resultData != null,
            confidence = confidence,
            processingTimeMs = processingTimeMs,
            costUsd = costUsd,
            errorMessage = errorMessage,
            createdAt = createdAt.toString(),
            completedAt = completedAt?.toString()
        )
    }

    /**
     * Extension function to convert AnalysisStats to DTO
     */
    private fun dev.screenshotapi.core.domain.repositories.AnalysisStats.toDto(): AnalysisStatsSummary {
        return AnalysisStatsSummary(
            totalAnalyses = totalAnalyses,
            completedAnalyses = completedAnalyses,
            failedAnalyses = failedAnalyses,
            successRate = successRate,
            totalCostUsd = totalCostUsd,
            averageProcessingTimeMs = averageProcessingTimeMs,
            analysesByType = analysesByType
        )
    }
}