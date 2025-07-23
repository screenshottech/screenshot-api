package dev.screenshotapi.infrastructure.services.models

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.ScreenshotJob
import kotlinx.serialization.Serializable

@Serializable
data class WebhookPayload(
    val jobId: String,
    val status: String,
    val url: String = "",
    val resultUrl: String = "",
    val errorMessage: String = "",
    val processingTimeMs: Long = 0,
    val completedAt: String = "",
    val createdAt: String,
    val userId: String,
    val format: String,
    val width: Int,
    val height: Int
)

@Serializable
data class AnalysisWebhookPayload(
    val analysisJobId: String,
    val screenshotJobId: String,
    val analysisType: String,
    val status: String,
    val language: String,
    val confidence: Double = 0.0,
    val tokensUsed: Int = 0,
    val costUsd: Double = 0.0,
    val processingTimeMs: Long = 0,
    val resultData: String = "",
    val errorMessage: String = "",
    val completedAt: String = "",
    val createdAt: String,
    val userId: String,
    val webhookUrl: String = ""
)

/**
 * Extension function to convert AnalysisJob to AnalysisWebhookPayload
 * Following Clean Architecture - Domain to Infrastructure DTO conversion
 */
fun AnalysisJob.toWebhookPayload(userId: String): AnalysisWebhookPayload = AnalysisWebhookPayload(
    analysisJobId = this.id,
    screenshotJobId = this.screenshotJobId,
    analysisType = this.analysisType.name,
    status = this.status.name.lowercase(),
    language = this.language,
    confidence = this.confidence ?: 0.0,
    tokensUsed = this.tokensUsed ?: 0,
    costUsd = this.costUsd ?: 0.0,
    processingTimeMs = this.processingTimeMs ?: 0,
    resultData = this.resultData ?: "",
    errorMessage = this.errorMessage ?: "",
    completedAt = this.completedAt?.toString() ?: "",
    createdAt = this.createdAt.toString(),
    userId = userId,
    webhookUrl = this.webhookUrl ?: ""
)

/**
 * Extension function to convert AnalysisWebhookPayload to Map<String, Any>
 * For webhook use case compatibility
 */
fun AnalysisWebhookPayload.toMap(): Map<String, Any> = mapOf(
    "analysisJobId" to analysisJobId,
    "screenshotJobId" to screenshotJobId,
    "analysisType" to analysisType,
    "status" to status,
    "language" to language,
    "confidence" to confidence,
    "tokensUsed" to tokensUsed,
    "costUsd" to costUsd,
    "processingTimeMs" to processingTimeMs,
    "resultData" to resultData,
    "errorMessage" to errorMessage,
    "completedAt" to completedAt,
    "createdAt" to createdAt,
    "userId" to userId,
    "webhookUrl" to webhookUrl
)

/**
 * Extension function to convert ScreenshotJob to WebhookPayload
 * Following Clean Architecture - Domain to Infrastructure DTO conversion
 */
fun ScreenshotJob.toWebhookPayload(): WebhookPayload = WebhookPayload(
    jobId = this.id,
    status = this.status.name.lowercase(),
    url = this.request.url,
    resultUrl = this.resultUrl ?: "",
    errorMessage = this.errorMessage ?: "",
    processingTimeMs = this.processingTimeMs ?: 0,
    completedAt = this.completedAt?.toString() ?: "",
    createdAt = this.createdAt.toString(),
    userId = this.userId,
    format = this.request.format.name,
    width = this.request.width,
    height = this.request.height
)

/**
 * Extension function to convert WebhookPayload to Map<String, Any>
 * For webhook use case compatibility
 */
fun WebhookPayload.toMap(): Map<String, Any> = mapOf(
    "jobId" to jobId,
    "status" to status,
    "url" to url,
    "resultUrl" to resultUrl,
    "errorMessage" to errorMessage,
    "processingTimeMs" to processingTimeMs,
    "completedAt" to completedAt,
    "createdAt" to createdAt,
    "userId" to userId,
    "format" to format,
    "width" to width,
    "height" to height
)
