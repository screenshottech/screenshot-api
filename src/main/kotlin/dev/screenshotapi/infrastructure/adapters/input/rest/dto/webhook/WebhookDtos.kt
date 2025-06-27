package dev.screenshotapi.infrastructure.adapters.input.rest.dto.webhook

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryStats
import kotlinx.serialization.Serializable

/**
 * Request DTOs
 */
@Serializable
data class CreateWebhookRequestDto(
    val url: String,
    val events: List<String>,
    val description: String? = null
)

@Serializable
data class UpdateWebhookRequestDto(
    val url: String? = null,
    val events: List<String>? = null,
    val description: String? = null,
    val isActive: Boolean? = null
)

/**
 * Response DTOs
 */
@Serializable
data class WebhookConfigurationDto(
    val id: String,
    val userId: String,
    val url: String,
    val events: List<String>,
    val isActive: Boolean,
    val description: String?,
    val createdAt: String,
    val updatedAt: String,
    val secret: String? = null // Only included when explicitly requested
)

@Serializable
data class WebhookListResponseDto(
    val webhooks: List<WebhookConfigurationDto>,
    val total: Int
)

@Serializable
data class WebhookDeliveryDto(
    val id: String,
    val webhookConfigId: String,
    val userId: String,
    val event: String,
    val eventData: Map<String, String>, // Simplified for JSON serialization
    val status: String,
    val url: String,
    val attempts: Int,
    val maxAttempts: Int,
    val lastAttemptAt: String,
    val nextRetryAt: String?,
    val responseCode: Int?,
    val responseBody: String?,
    val responseTimeMs: Long?,
    val error: String?,
    val createdAt: String
)

@Serializable
data class WebhookDeliveryListResponseDto(
    val deliveries: List<WebhookDeliveryDto>,
    val total: Int
)

@Serializable
data class WebhookDeliveryStatsDto(
    val total: Long,
    val delivered: Long,
    val failed: Long,
    val pending: Long,
    val averageResponseTimeMs: Long?,
    val successRate: Double
)

@Serializable
data class WebhookDebugStatsDto(
    val userId: String,
    val totalWebhooks: Int,
    val activeWebhooks: Int,
    val totalDeliveries: Int,
    val deliveryStats: WebhookDeliveryBreakdownDto,
    val availableEvents: List<String>,
    val webhookEndpoints: List<String>
)

@Serializable
data class WebhookDeliveryBreakdownDto(
    val delivered: Int,
    val failed: Int,
    val pending: Int,
    val retrying: Int
)

/**
 * Extension functions for mapping between domain entities and DTOs
 */
fun WebhookConfiguration.toDto(includeSecret: Boolean = false): WebhookConfigurationDto = WebhookConfigurationDto(
    id = id,
    userId = userId,
    url = url,
    events = events.map { it.name },
    isActive = isActive,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    secret = if (includeSecret) secret else null
)

/**
 * Safe mapping without secret for listing operations
 */
fun WebhookConfiguration.toDtoSafe(): WebhookConfigurationDto = toDto(includeSecret = false)

/**
 * Full mapping with secret for creation response
 */
fun WebhookConfiguration.toDtoWithSecret(): WebhookConfigurationDto = toDto(includeSecret = true)

fun WebhookDelivery.toDto(): WebhookDeliveryDto = WebhookDeliveryDto(
    id = id,
    webhookConfigId = webhookConfigId,
    userId = userId,
    event = event.name,
    eventData = eventData.mapValues { it.value.toString() }, // Convert to string for JSON
    status = status.name,
    url = url,
    attempts = attempts,
    maxAttempts = maxAttempts,
    lastAttemptAt = lastAttemptAt.toString(),
    nextRetryAt = nextRetryAt?.toString(),
    responseCode = responseCode,
    responseBody = responseBody,
    responseTimeMs = responseTimeMs,
    error = error,
    createdAt = createdAt.toString()
)

fun WebhookDeliveryStats.toDto(): WebhookDeliveryStatsDto = WebhookDeliveryStatsDto(
    total = total,
    delivered = delivered,
    failed = failed,
    pending = pending,
    averageResponseTimeMs = averageResponseTimeMs,
    successRate = successRate
)