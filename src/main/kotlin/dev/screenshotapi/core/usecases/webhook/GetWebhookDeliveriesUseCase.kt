package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.WebhookDelivery
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryRepository
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryStats
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class GetWebhookDeliveriesUseCase(
    private val webhookConfigRepository: WebhookConfigurationRepository,
    private val webhookDeliveryRepository: WebhookDeliveryRepository
) {
    suspend fun getDeliveries(
        webhookId: String,
        userId: String,
        limit: Int = 100
    ): List<WebhookDelivery> {
        val webhook = webhookConfigRepository.findById(webhookId)
            ?: throw ResourceNotFoundException("Webhook", webhookId)
        
        if (webhook.userId != userId) {
            throw ValidationException.UnauthorizedAccess("webhook", webhookId)
        }
        
        return webhookDeliveryRepository.findByWebhookConfigId(webhookId, limit)
    }
    
    suspend fun getUserDeliveries(
        userId: String,
        limit: Int = 100
    ): List<WebhookDelivery> {
        return webhookDeliveryRepository.findByUserId(userId, limit)
    }
    
    suspend fun getDeliveryStats(
        webhookId: String,
        userId: String,
        since: Instant? = null
    ): WebhookDeliveryStats {
        val webhook = webhookConfigRepository.findById(webhookId)
            ?: throw ResourceNotFoundException("Webhook", webhookId)
        
        if (webhook.userId != userId) {
            throw ValidationException.UnauthorizedAccess("webhook", webhookId)
        }
        
        val sinceTime = since ?: Clock.System.now().minus(30.days)
        return webhookDeliveryRepository.getDeliveryStats(webhookId, sinceTime)
    }
    
    suspend fun getSuccessRate(
        webhookId: String,
        userId: String,
        since: Instant? = null
    ): Double {
        val webhook = webhookConfigRepository.findById(webhookId)
            ?: throw ResourceNotFoundException("Webhook", webhookId)
        
        if (webhook.userId != userId) {
            throw ValidationException.UnauthorizedAccess("webhook", webhookId)
        }
        
        val sinceTime = since ?: Clock.System.now().minus(30.days)
        return webhookDeliveryRepository.getSuccessRate(webhookId, sinceTime)
    }
    
    suspend fun getDelivery(
        deliveryId: String,
        userId: String
    ): WebhookDelivery {
        val delivery = webhookDeliveryRepository.findById(deliveryId)
            ?: throw ResourceNotFoundException("WebhookDelivery", deliveryId)
        
        if (delivery.userId != userId) {
            throw ValidationException.UnauthorizedAccess("webhook delivery", deliveryId)
        }
        
        return delivery
    }
}