package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant
import java.util.*

/**
 * Analysis Job Entity - Represents an AI analysis request for a screenshot
 * 
 * This entity handles the lifecycle of AI-powered analysis requests,
 * separate from screenshot generation for better scalability and cost control.
 */
data class AnalysisJob(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val screenshotJobId: String,
    val screenshotUrl: String,
    val analysisType: AnalysisType,
    val status: AnalysisStatus,
    val language: String = "en",
    val webhookUrl: String? = null,
    
    // Custom prompts support
    val customSystemPrompt: String? = null,
    val customUserPrompt: String? = null,
    val promptValidationScore: Double? = null,
    val securityFlags: Map<String, String> = emptyMap(),
    
    // Results
    val resultData: String? = null,
    val confidence: Double? = null,
    val metadata: Map<String, String> = emptyMap(),
    
    // Processing info
    val processingTimeMs: Long? = null,
    val tokensUsed: Int? = null,
    val costUsd: Double? = null,
    val errorMessage: String? = null,
    
    // Timestamps
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val updatedAt: Instant = createdAt
) {
    /**
     * Check if analysis is in a terminal state
     */
    fun isTerminal(): Boolean = status in listOf(
        AnalysisStatus.COMPLETED, 
        AnalysisStatus.FAILED, 
        AnalysisStatus.CANCELLED
    )
    
    /**
     * Check if analysis can be retried
     */
    fun canRetry(): Boolean = status == AnalysisStatus.FAILED
    
    /**
     * Get processing duration in milliseconds
     */
    fun getProcessingDuration(): Long? {
        return if (startedAt != null && completedAt != null) {
            completedAt.toEpochMilliseconds() - startedAt.toEpochMilliseconds()
        } else null
    }
    
    /**
     * Create a retry copy of this job
     */
    fun retry(newId: String = UUID.randomUUID().toString()): AnalysisJob {
        return copy(
            id = newId,
            status = AnalysisStatus.QUEUED,
            resultData = null,
            confidence = null,
            processingTimeMs = null,
            tokensUsed = null,
            costUsd = null,
            errorMessage = null,
            startedAt = null,
            completedAt = null,
            updatedAt = createdAt
        )
    }
    
    /**
     * Check if this job uses custom prompts
     */
    fun usesCustomPrompts(): Boolean {
        return analysisType == AnalysisType.CUSTOM && 
               (!customUserPrompt.isNullOrBlank() || !customSystemPrompt.isNullOrBlank())
    }
    
    /**
     * Get effective system prompt (custom or default)
     */
    fun getEffectiveSystemPrompt(): String {
        return if (analysisType == AnalysisType.CUSTOM && !customSystemPrompt.isNullOrBlank()) {
            customSystemPrompt!!
        } else {
            analysisType.systemPrompt
        }
    }
    
    /**
     * Get effective user prompt (custom or default)
     */
    fun getEffectiveUserPrompt(): String {
        return if (analysisType == AnalysisType.CUSTOM && !customUserPrompt.isNullOrBlank()) {
            customUserPrompt!!
        } else {
            analysisType.userPrompt
        }
    }
    
    /**
     * Check if custom prompts have security risks
     */
    fun hasSecurityFlags(): Boolean {
        return securityFlags.isNotEmpty()
    }
}

/**
 * Analysis Status - Lifecycle states for analysis jobs
 */
enum class AnalysisStatus(
    val displayName: String,
    val description: String
) {
    QUEUED("Queued", "Analysis request queued for processing"),
    PROCESSING("Processing", "AI analysis in progress"),
    COMPLETED("Completed", "Analysis completed successfully"),
    FAILED("Failed", "Analysis failed with error"),
    CANCELLED("Cancelled", "Analysis cancelled by user");
    
    companion object {
        fun fromString(value: String): AnalysisStatus {
            return values().find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid analysis status: $value")
        }
    }
}