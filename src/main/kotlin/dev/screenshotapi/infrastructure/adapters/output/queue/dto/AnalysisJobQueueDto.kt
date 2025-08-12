package dev.screenshotapi.infrastructure.adapters.output.queue.dto

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.AnalysisStatus
import dev.screenshotapi.core.domain.entities.AnalysisType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisJobQueueDto(
    val id: String,
    val userId: String,
    val screenshotJobId: String,
    val screenshotUrl: String,
    val analysisType: String,
    val status: String,
    val language: String,
    val webhookUrl: String? = null,
    val customUserPrompt: String? = null,
    val resultData: String? = null,
    val confidence: Double? = null,
    val metadata: Map<String, String> = emptyMap(),
    val processingTimeMs: Long? = null,
    val tokensUsed: Int? = null,
    val costUsd: Double? = null,
    val errorMessage: String? = null,
    val createdAt: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val updatedAt: String
) {
    companion object {
        fun fromDomain(job: AnalysisJob): AnalysisJobQueueDto {
            return AnalysisJobQueueDto(
                id = job.id,
                userId = job.userId,
                screenshotJobId = job.screenshotJobId,
                screenshotUrl = job.screenshotUrl,
                analysisType = job.analysisType.name,
                status = job.status.name,
                language = job.language,
                webhookUrl = job.webhookUrl,
                customUserPrompt = job.customUserPrompt,
                resultData = job.resultData,
                confidence = job.confidence,
                metadata = job.metadata,
                processingTimeMs = job.processingTimeMs,
                tokensUsed = job.tokensUsed,
                costUsd = job.costUsd,
                errorMessage = job.errorMessage,
                createdAt = job.createdAt.toString(),
                startedAt = job.startedAt?.toString(),
                completedAt = job.completedAt?.toString(),
                updatedAt = job.updatedAt.toString()
            )
        }
    }

    fun toDomain(): AnalysisJob {
        return AnalysisJob(
            id = id,
            userId = userId,
            screenshotJobId = screenshotJobId,
            screenshotUrl = screenshotUrl,
            analysisType = AnalysisType.valueOf(analysisType),
            status = AnalysisStatus.valueOf(status),
            language = language,
            webhookUrl = webhookUrl,
            customUserPrompt = customUserPrompt,
            resultData = resultData,
            confidence = confidence,
            metadata = metadata,
            processingTimeMs = processingTimeMs,
            tokensUsed = tokensUsed,
            costUsd = costUsd,
            errorMessage = errorMessage,
            createdAt = Instant.parse(createdAt),
            startedAt = startedAt?.let { Instant.parse(it) },
            completedAt = completedAt?.let { Instant.parse(it) },
            updatedAt = Instant.parse(updatedAt)
        )
    }
}