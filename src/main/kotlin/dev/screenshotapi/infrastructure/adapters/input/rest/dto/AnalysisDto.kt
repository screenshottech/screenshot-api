package dev.screenshotapi.infrastructure.adapters.input.rest.dto

import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.usecases.analysis.CreateAnalysisUseCase
import dev.screenshotapi.core.usecases.analysis.GetAnalysisStatusUseCase
import dev.screenshotapi.core.usecases.analysis.GetScreenshotAnalysesUseCase
import dev.screenshotapi.core.usecases.analysis.ListAnalysesUseCase
import kotlinx.serialization.Serializable

// ==================== REQUEST DTOs ====================

/**
 * Request DTO for creating an analysis
 */
@Serializable
data class CreateAnalysisRequestDto(
    val analysisType: String, // AnalysisType enum name
    val language: String = "en",
    val webhookUrl: String? = null,
    val customUserPrompt: String? = null // For CUSTOM analysis type
)

// ==================== RESPONSE DTOs ====================

/**
 * Response DTO for analysis creation
 */
@Serializable
data class CreateAnalysisResponseDto(
    val analysisJobId: String,
    val status: String,
    val analysisType: String,
    val creditsDeducted: Int,
    val estimatedCompletion: String,
    val queuePosition: Int
)

/**
 * Response DTO for analysis status
 */
@Serializable
data class GetAnalysisStatusResponseDto(
    val analysisJobId: String,
    val status: String,
    val analysisType: String,
    val screenshotJobId: String,
    val screenshotUrl: String,
    val language: String,
    val webhookUrl: String?,

    // Results (null until completed)
    val resultData: String?,
    val confidence: Double?,

    // Processing info
    val processingTimeMs: Long?,
    val tokensUsed: Int?,
    val costUsd: Double?,

    // Error info (null unless failed)
    val errorMessage: String?,

    // Timestamps
    val createdAt: String,
    val startedAt: String?,
    val completedAt: String?,

    // Status helpers
    val isProcessing: Boolean,
    val isCompleted: Boolean,
    val isFailed: Boolean,
    val statusDescription: String,
    val estimatedCompletion: String?,

    // Metadata
    val metadata: Map<String, String>
)

/**
 * Response DTO for analysis list
 */
@Serializable
data class ListAnalysesResponseDto(
    val analyses: List<AnalysisSummaryDto>,
    val pagination: PaginationDto,
    val statistics: AnalysisStatsDto
)

/**
 * Analysis summary for list view
 */
@Serializable
data class AnalysisSummaryDto(
    val analysisJobId: String,
    val screenshotJobId: String,
    val analysisType: String,
    val status: String,
    val statusDescription: String,
    val language: String,
    val hasResults: Boolean,
    val confidence: Double?,
    val processingTimeMs: Long?,
    val costUsd: Double?,
    val errorMessage: String?,
    val createdAt: String,
    val completedAt: String?
)

/**
 * Analysis statistics summary
 */
@Serializable
data class AnalysisStatsDto(
    val totalAnalyses: Long,
    val completedAnalyses: Long,
    val failedAnalyses: Long,
    val successRate: Double,
    val totalCostUsd: Double,
    val averageProcessingTimeMs: Double?,
    val analysesByType: Map<String, Long>
)

// ==================== MAPPING FUNCTIONS ====================

/**
 * Convert DTO to domain request
 */
fun CreateAnalysisRequestDto.toDomainRequest(
    userId: String,
    screenshotJobId: String,
    apiKeyId: String? = null
): CreateAnalysisUseCase.Request {
    return CreateAnalysisUseCase.Request(
        userId = userId,
        screenshotJobId = screenshotJobId,
        analysisType = AnalysisType.valueOf(analysisType),
        language = language,
        webhookUrl = webhookUrl,
        apiKeyId = apiKeyId,
        customUserPrompt = customUserPrompt
    )
}

/**
 * Convert domain response to DTO
 */
fun CreateAnalysisUseCase.Response.toDto(): CreateAnalysisResponseDto {
    return CreateAnalysisResponseDto(
        analysisJobId = analysisJobId,
        status = status.name,
        analysisType = analysisType.displayName,
        creditsDeducted = creditsDeducted,
        estimatedCompletion = estimatedCompletion,
        queuePosition = queuePosition
    )
}

/**
 * Convert domain response to DTO
 */
fun GetAnalysisStatusUseCase.Response.toDto(): GetAnalysisStatusResponseDto {
    return GetAnalysisStatusResponseDto(
        analysisJobId = analysisJobId,
        status = status.name,
        analysisType = analysisType.displayName,
        screenshotJobId = screenshotJobId,
        screenshotUrl = screenshotUrl,
        language = language,
        webhookUrl = webhookUrl,
        resultData = resultData,
        confidence = confidence,
        processingTimeMs = processingTimeMs,
        tokensUsed = tokensUsed,
        costUsd = costUsd,
        errorMessage = errorMessage,
        createdAt = createdAt,
        startedAt = startedAt,
        completedAt = completedAt,
        isProcessing = isProcessing,
        isCompleted = isCompleted,
        isFailed = isFailed,
        statusDescription = statusDescription,
        estimatedCompletion = estimatedCompletion,
        metadata = metadata
    )
}

/**
 * Convert domain response to DTO
 */
fun ListAnalysesUseCase.Response.toDto(): ListAnalysesResponseDto {
    return ListAnalysesResponseDto(
        analyses = analyses.map { it.toDto() },
        pagination = pagination.toDto(),
        statistics = statistics.toDto()
    )
}

/**
 * Convert analysis summary to DTO
 */
fun ListAnalysesUseCase.AnalysisSummary.toDto(): AnalysisSummaryDto {
    return AnalysisSummaryDto(
        analysisJobId = analysisJobId,
        screenshotJobId = screenshotJobId,
        analysisType = analysisType.displayName,
        status = status.name,
        statusDescription = statusDescription,
        language = language,
        hasResults = hasResults,
        confidence = confidence,
        processingTimeMs = processingTimeMs,
        costUsd = costUsd,
        errorMessage = errorMessage,
        createdAt = createdAt,
        completedAt = completedAt
    )
}

/**
 * Convert pagination info to DTO
 */
fun ListAnalysesUseCase.PaginationInfo.toDto(): PaginationDto {
    return PaginationDto(
        page = page,
        limit = limit,
        total = total,
        totalPages = totalPages,
        hasNext = hasNext,
        hasPrevious = hasPrevious
    )
}

/**
 * Convert analysis stats to DTO
 */
fun ListAnalysesUseCase.AnalysisStatsSummary.toDto(): AnalysisStatsDto {
    return AnalysisStatsDto(
        totalAnalyses = totalAnalyses,
        completedAnalyses = completedAnalyses,
        failedAnalyses = failedAnalyses,
        successRate = successRate,
        totalCostUsd = totalCostUsd,
        averageProcessingTimeMs = averageProcessingTimeMs,
        analysesByType = analysesByType
    )
}

// ==================== SCREENSHOT ANALYSES DTOs ====================

/**
 * Response DTO for screenshot analyses list
 */
@Serializable
data class GetScreenshotAnalysesResponseDto(
    val screenshotJobId: String,
    val analyses: List<ScreenshotAnalysisSummaryDto>,
    val totalCount: Int,
    val metadata: ScreenshotAnalysesMetadataDto
)

/**
 * Analysis summary DTO for screenshot analyses
 */
@Serializable
data class ScreenshotAnalysisSummaryDto(
    val id: String,
    val analysisType: String,
    val status: String,
    val resultData: String?,
    val confidence: Double?,
    val processingTimeMs: Long?,
    val tokensUsed: Int?,
    val costUsd: Double?,
    val errorMessage: String?,
    val createdAt: String,
    val completedAt: String?,
    val isCompleted: Boolean,
    val hasResults: Boolean
)

/**
 * Metadata for screenshot analyses
 */
@Serializable
data class ScreenshotAnalysesMetadataDto(
    val screenshotUrl: String?,
    val screenshotStatus: String,
    val screenshotCreatedAt: String
)

/**
 * Convert screenshot analyses response to DTO
 */
fun GetScreenshotAnalysesUseCase.Response.toDto(): GetScreenshotAnalysesResponseDto {
    return GetScreenshotAnalysesResponseDto(
        screenshotJobId = screenshotJobId,
        analyses = analyses.map { it.toDto() },
        totalCount = totalCount,
        metadata = metadata.toDto()
    )
}

/**
 * Convert screenshot analysis summary to DTO
 */
fun GetScreenshotAnalysesUseCase.AnalysisSummary.toDto(): ScreenshotAnalysisSummaryDto {
    return ScreenshotAnalysisSummaryDto(
        id = id,
        analysisType = analysisType.displayName,
        status = status.name,
        resultData = resultData,
        confidence = confidence,
        processingTimeMs = processingTimeMs,
        tokensUsed = tokensUsed,
        costUsd = costUsd,
        errorMessage = errorMessage,
        createdAt = createdAt.toString(),
        completedAt = completedAt?.toString(),
        isCompleted = isCompleted,
        hasResults = hasResults
    )
}

/**
 * Convert screenshot analyses metadata to DTO
 */
fun GetScreenshotAnalysesUseCase.AnalysesMetadata.toDto(): ScreenshotAnalysesMetadataDto {
    return ScreenshotAnalysesMetadataDto(
        screenshotUrl = screenshotUrl,
        screenshotStatus = screenshotStatus,
        screenshotCreatedAt = screenshotCreatedAt.toString()
    )
}
