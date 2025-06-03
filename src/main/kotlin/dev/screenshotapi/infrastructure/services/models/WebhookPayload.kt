package dev.screenshotapi.infrastructure.services.models

import kotlinx.serialization.Serializable

@Serializable
data class WebhookPayload(
    val jobId: String,
    val status: String,
    val url: String? = null,
    val resultUrl: String? = null,
    val errorMessage: String? = null,
    val processingTimeMs: Long? = null,
    val completedAt: String? = null
)
