package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.WebhookConfiguration
import dev.screenshotapi.core.domain.entities.WebhookDelivery
import dev.screenshotapi.core.domain.entities.WebhookDeliveryStatus
import dev.screenshotapi.core.domain.entities.WebhookEvent
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.minutes

class SendWebhookUseCase(
    private val webhookConfigRepository: WebhookConfigurationRepository,
    private val webhookDeliveryRepository: WebhookDeliveryRepository,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val WEBHOOK_TIMEOUT_SECONDS = 30L
        const val MAX_RETRY_ATTEMPTS = 5
        val RETRY_DELAYS = listOf(1.minutes, 5.minutes, 15.minutes, 30.minutes, 60.minutes)
    }

    suspend fun sendForEvent(
        event: WebhookEvent,
        eventData: Map<String, Any>,
        userId: String? = null
    ): List<WebhookDelivery> {
        val webhooks = if (userId != null) {
            webhookConfigRepository.findByUserIdAndEvent(userId, event)
        } else {
            webhookConfigRepository.findActiveByEvent(event)
        }

        return webhooks.map { webhook ->
            sendWebhook(webhook, event, eventData)
        }
    }

    suspend fun sendWebhook(
        webhook: WebhookConfiguration,
        event: WebhookEvent,
        eventData: Map<String, Any>
    ): WebhookDelivery {
        val payload = createPayload(event, eventData)
        val signature = generateSignature(payload, webhook.secret)

        val delivery = WebhookDelivery(
            id = UUID.randomUUID().toString(),
            webhookConfigId = webhook.id,
            userId = webhook.userId,
            event = event,
            eventData = eventData,
            payload = payload,
            signature = signature,
            status = WebhookDeliveryStatus.PENDING,
            url = webhook.url,
            attempts = 0,
            maxAttempts = MAX_RETRY_ATTEMPTS,
            lastAttemptAt = Clock.System.now(),
            nextRetryAt = null,
            createdAt = Clock.System.now()
        )

        val savedDelivery = webhookDeliveryRepository.save(delivery)
        return attemptDelivery(savedDelivery)
    }

    suspend fun retryFailedDeliveries(limit: Int = 100): List<WebhookDelivery> {
        val now = Clock.System.now()
        val failedDeliveries = webhookDeliveryRepository.findFailedDeliveriesForRetry(now, limit)

        return failedDeliveries.map { delivery ->
            attemptDelivery(delivery)
        }
    }

    /**
     * Attempt to deliver a webhook
     */
    private suspend fun attemptDelivery(delivery: WebhookDelivery): WebhookDelivery {
        val startTime = System.currentTimeMillis()
        val updatedDelivery = delivery.copy(
            status = WebhookDeliveryStatus.DELIVERING,
            attempts = delivery.attempts + 1,
            lastAttemptAt = Clock.System.now()
        )

        webhookDeliveryRepository.update(updatedDelivery)

        try {
            val response = httpClient.post(delivery.url) {
                header("Content-Type", "application/json")
                header("X-Webhook-Event", delivery.event.name)
                header("X-Webhook-Signature-256", "sha256=${delivery.signature}")
                header("X-Webhook-Delivery", delivery.id)
                header("User-Agent", "ScreenshotAPI-Webhook/1.0")

                setBody(delivery.payload)
            }

            val responseTime = System.currentTimeMillis() - startTime
            val responseBody = response.bodyAsText().take(1000)

            val finalDelivery = if (response.status.isSuccess()) {
                logger.info("Webhook delivered successfully: ${delivery.id}")
                updatedDelivery.copy(
                    status = WebhookDeliveryStatus.DELIVERED,
                    responseCode = response.status.value,
                    responseBody = responseBody,
                    responseTimeMs = responseTime,
                    nextRetryAt = null
                )
            } else {
                logger.warn("Webhook delivery failed with status ${response.status}: ${delivery.id}")
                handleFailedDelivery(updatedDelivery, response.status.value, responseBody, responseTime)
            }

            return webhookDeliveryRepository.update(finalDelivery)

        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            logger.error("Webhook delivery exception for ${delivery.id}: ${e.message}", e)

            val failedDelivery = handleFailedDelivery(
                updatedDelivery,
                null,
                null,
                responseTime,
                e.message
            )

            return webhookDeliveryRepository.update(failedDelivery)
        }
    }

    private fun handleFailedDelivery(
        delivery: WebhookDelivery,
        responseCode: Int?,
        responseBody: String?,
        responseTime: Long,
        error: String? = null
    ): WebhookDelivery {
        val shouldRetry = delivery.attempts < delivery.maxAttempts

        return if (shouldRetry) {
            val retryDelay = RETRY_DELAYS.getOrNull(delivery.attempts - 1) ?: RETRY_DELAYS.last()
            val nextRetryAt = Clock.System.now().plus(retryDelay)

            delivery.copy(
                status = WebhookDeliveryStatus.RETRYING,
                responseCode = responseCode,
                responseBody = responseBody,
                responseTimeMs = responseTime,
                error = error,
                nextRetryAt = nextRetryAt
            )
        } else {
            logger.error("Webhook delivery permanently failed after ${delivery.attempts} attempts: ${delivery.id}")

            delivery.copy(
                status = WebhookDeliveryStatus.FAILED,
                responseCode = responseCode,
                responseBody = responseBody,
                responseTimeMs = responseTime,
                error = error,
                nextRetryAt = null
            )
        }
    }

    private fun createPayload(event: WebhookEvent, eventData: Map<String, Any>): String {
        val payload = WebhookPayloadDto(
            event = event.name,
            timestamp = Clock.System.now().toString(),
            data = convertEventDataToJson(eventData)
        )

        return json.encodeToString(payload)
    }

    private fun convertEventDataToJson(eventData: Map<String, Any>): Map<String, String> {
        return eventData.mapValues { (_, value) ->
            when (value) {
                is String -> value
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> value.toString()
            }
        }
    }

    private fun generateSignature(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)

        val signature = mac.doFinal(payload.toByteArray())
        return signature.joinToString("") { "%02x".format(it) }
    }
}

@Serializable
private data class WebhookPayloadDto(
    val event: String,
    val timestamp: String,
    val data: Map<String, String>
)
