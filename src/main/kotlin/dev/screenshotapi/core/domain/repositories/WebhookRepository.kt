package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.*
import kotlinx.datetime.Instant

/**
 * Repository for managing webhook configurations
 */
interface WebhookConfigurationRepository {
    suspend fun save(webhook: WebhookConfiguration): WebhookConfiguration
    suspend fun findById(id: String): WebhookConfiguration?
    suspend fun findByUserId(userId: String): List<WebhookConfiguration>
    suspend fun findByUserIdAndEvent(userId: String, event: WebhookEvent): List<WebhookConfiguration>
    suspend fun update(webhook: WebhookConfiguration): WebhookConfiguration
    suspend fun delete(id: String): Boolean
    suspend fun countByUserId(userId: String): Long
    suspend fun findActiveByEvent(event: WebhookEvent): List<WebhookConfiguration>
}

/**
 * Repository for tracking webhook deliveries
 */
interface WebhookDeliveryRepository {
    suspend fun save(delivery: WebhookDelivery): WebhookDelivery
    suspend fun findById(id: String): WebhookDelivery?
    suspend fun findByWebhookConfigId(webhookConfigId: String, limit: Int = 100): List<WebhookDelivery>
    suspend fun findByUserId(userId: String, limit: Int = 100): List<WebhookDelivery>
    suspend fun findPendingDeliveries(limit: Int = 100): List<WebhookDelivery>
    suspend fun findFailedDeliveriesForRetry(before: Instant, limit: Int = 100): List<WebhookDelivery>
    suspend fun update(delivery: WebhookDelivery): WebhookDelivery
    suspend fun countByStatus(status: WebhookDeliveryStatus): Long
    suspend fun countByWebhookConfigId(webhookConfigId: String): Long
    suspend fun deleteOlderThan(before: Instant): Int
    suspend fun deleteOldDeliveries(before: Instant, status: WebhookDeliveryStatus? = null, limit: Int = 1000): Int
    
    // Analytics queries
    suspend fun getDeliveryStats(webhookConfigId: String, since: Instant): WebhookDeliveryStats
    suspend fun getSuccessRate(webhookConfigId: String, since: Instant): Double
}

/**
 * Statistics for webhook deliveries
 */
data class WebhookDeliveryStats(
    val total: Long,
    val delivered: Long,
    val failed: Long,
    val pending: Long,
    val averageResponseTimeMs: Long?,
    val successRate: Double
)