package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Represents a user's webhook configuration for receiving event notifications
 */
data class WebhookConfiguration(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val url: String,
    val secret: String, // Used for HMAC-SHA256 signing
    val events: Set<WebhookEvent>,
    val isActive: Boolean = true,
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(url.isNotBlank()) { "Webhook URL cannot be blank" }
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "Webhook URL must start with http:// or https://"
        }
        require(secret.length >= 32) { "Webhook secret must be at least 32 characters" }
        require(events.isNotEmpty()) { "At least one event must be subscribed" }
    }
}

/**
 * Events that can trigger webhook notifications
 */
enum class WebhookEvent {
    // Screenshot events
    SCREENSHOT_COMPLETED,
    SCREENSHOT_FAILED,

    // Credit events
    CREDITS_LOW,         // When credits drop below 20%
    CREDITS_EXHAUSTED,   // When credits reach 0

    // Subscription events
    SUBSCRIPTION_RENEWED,
    SUBSCRIPTION_CANCELLED,

    // Payment events
    PAYMENT_SUCCESSFUL,
    PAYMENT_FAILED,
    PAYMENT_PROCESSED,   // Alias for PAYMENT_SUCCESSFUL for backward compatibility

    // User events
    USER_REGISTERED,     // When a new user registers

    // Testing events
    WEBHOOK_TEST         // For testing webhook configurations
}

/**
 * Tracks webhook delivery attempts and their status
 */
data class WebhookDelivery(
    val id: String = UUID.randomUUID().toString(),
    val webhookConfigId: String,
    val userId: String,
    val event: WebhookEvent,
    val eventData: Map<String, Any>, // Event-specific data
    val payload: String, // JSON payload sent
    val signature: String, // HMAC signature
    val status: WebhookDeliveryStatus,
    val url: String, // URL at time of delivery (in case config changes)
    val attempts: Int = 1,
    val maxAttempts: Int = 5,
    val lastAttemptAt: Instant,
    val nextRetryAt: Instant? = null,
    val responseCode: Int? = null,
    val responseBody: String? = null,
    val responseTimeMs: Long? = null,
    val error: String? = null,
    val createdAt: Instant
) {
    init {
        require(webhookConfigId.isNotBlank()) { "Webhook config ID cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(attempts >= 0) { "Attempts must be non-negative" }
        require(attempts <= maxAttempts) { "Attempts cannot exceed max attempts" }
    }

    fun shouldRetry(): Boolean {
        return status == WebhookDeliveryStatus.FAILED &&
               attempts < maxAttempts &&
               responseCode?.let { it >= 500 || it == 429 } ?: true
    }

    fun isSuccessful(): Boolean {
        return status == WebhookDeliveryStatus.DELIVERED
    }
}

/**
 * Status of a webhook delivery attempt
 */
enum class WebhookDeliveryStatus {
    PENDING,    // Queued for delivery
    DELIVERING, // Currently being sent
    DELIVERED,  // Successfully delivered (2xx response)
    FAILED,     // Failed after all retries
    RETRYING    // Failed but will be retried
}
