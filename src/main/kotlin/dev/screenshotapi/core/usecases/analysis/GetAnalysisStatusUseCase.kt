package dev.screenshotapi.core.usecases.analysis

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.AnalysisStatus
import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import org.slf4j.LoggerFactory

/**
 * Get Analysis Status Use Case - Retrieves status and results of an analysis job
 * 
 * This use case handles:
 * - Analysis job lookup with user access control
 * - Status information including progress indicators
 * - Result data when analysis is completed
 * - Error information when analysis fails
 */
class GetAnalysisStatusUseCase(
    private val analysisJobRepository: AnalysisJobRepository
) {
    private val logger = LoggerFactory.getLogger(GetAnalysisStatusUseCase::class.java)

    suspend operator fun invoke(request: Request): Response {
        logger.debug("Getting analysis status for job ${request.analysisJobId}")
        
        // Find analysis job with user access control
        val analysisJob = analysisJobRepository.findByIdAndUserId(request.analysisJobId, request.userId)
            ?: throw ResourceNotFoundException("AnalysisJob", request.analysisJobId)
        
        logger.debug(
            "Analysis job found: ${analysisJob.id}, " +
            "Status: ${analysisJob.status}, " +
            "Type: ${analysisJob.analysisType.displayName}"
        )
        
        return Response(
            analysisJobId = analysisJob.id,
            status = analysisJob.status,
            analysisType = analysisJob.analysisType,
            screenshotJobId = analysisJob.screenshotJobId,
            screenshotUrl = analysisJob.screenshotUrl,
            language = analysisJob.language,
            webhookUrl = analysisJob.webhookUrl,
            
            // Custom prompts (for CUSTOM analysis type)
            customSystemPrompt = analysisJob.customSystemPrompt,
            customUserPrompt = analysisJob.customUserPrompt,
            promptValidationScore = analysisJob.promptValidationScore,
            securityFlags = analysisJob.securityFlags,
            usesCustomPrompts = analysisJob.usesCustomPrompts(),
            
            // Results (only available when completed)
            resultData = if (analysisJob.status == AnalysisStatus.COMPLETED) {
                analysisJob.resultData
            } else null,
            confidence = if (analysisJob.status == AnalysisStatus.COMPLETED) {
                analysisJob.confidence
            } else null,
            
            // Processing information
            processingTimeMs = analysisJob.processingTimeMs,
            tokensUsed = analysisJob.tokensUsed,
            costUsd = analysisJob.costUsd,
            
            // Error information
            errorMessage = if (analysisJob.status == AnalysisStatus.FAILED) {
                analysisJob.errorMessage
            } else null,
            
            // Timestamps
            createdAt = analysisJob.createdAt.toString(),
            startedAt = analysisJob.startedAt?.toString(),
            completedAt = analysisJob.completedAt?.toString(),
            
            // Additional metadata
            metadata = analysisJob.metadata
        )
    }

    data class Request(
        val analysisJobId: String,
        val userId: String
    )

    data class Response(
        val analysisJobId: String,
        val status: AnalysisStatus,
        val analysisType: AnalysisType,
        val screenshotJobId: String,
        val screenshotUrl: String,
        val language: String,
        val webhookUrl: String?,
        
        // Custom prompts (for CUSTOM analysis type)
        val customSystemPrompt: String?,
        val customUserPrompt: String?,
        val promptValidationScore: Double?,
        val securityFlags: Map<String, String>,
        val usesCustomPrompts: Boolean,
        
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
        
        // Metadata
        val metadata: Map<String, String>
    ) {
        /**
         * Check if analysis is still processing
         */
        val isProcessing: Boolean
            get() = status in listOf(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING)
        
        /**
         * Check if analysis is completed successfully
         */
        val isCompleted: Boolean
            get() = status == AnalysisStatus.COMPLETED
        
        /**
         * Check if analysis has failed
         */
        val isFailed: Boolean
            get() = status == AnalysisStatus.FAILED
        
        /**
         * Get human-readable status description
         */
        val statusDescription: String
            get() = when (status) {
                AnalysisStatus.QUEUED -> "Analysis request is queued for processing"
                AnalysisStatus.PROCESSING -> "AI analysis is currently in progress"
                AnalysisStatus.COMPLETED -> "Analysis completed successfully"
                AnalysisStatus.FAILED -> "Analysis failed: ${errorMessage ?: "Unknown error"}"
                AnalysisStatus.CANCELLED -> "Analysis was cancelled"
            }
        
        /**
         * Get estimated completion time (for queued/processing jobs)
         */
        val estimatedCompletion: String?
            get() = when (status) {
                AnalysisStatus.QUEUED -> "Analysis will begin shortly"
                AnalysisStatus.PROCESSING -> "Analysis should complete within 1-2 minutes"
                else -> null
            }
    }
}