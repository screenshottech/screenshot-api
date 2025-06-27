package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.WebhookConfiguration
import dev.screenshotapi.core.domain.entities.WebhookDelivery
import dev.screenshotapi.core.domain.entities.WebhookDeliveryStatus
import dev.screenshotapi.core.domain.entities.WebhookEvent
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryRepository
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryStats
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryWebhookConfigurationRepository : WebhookConfigurationRepository {
    private val webhooks = ConcurrentHashMap<String, WebhookConfiguration>()

    override suspend fun save(webhook: WebhookConfiguration): WebhookConfiguration {
        webhooks[webhook.id] = webhook
        return webhook
    }

    override suspend fun findById(id: String): WebhookConfiguration? {
        return webhooks[id]
    }

    override suspend fun findByUserId(userId: String): List<WebhookConfiguration> {
        return webhooks.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun findByUserIdAndEvent(userId: String, event: WebhookEvent): List<WebhookConfiguration> {
        return webhooks.values
            .filter { it.userId == userId && it.isActive && it.events.contains(event) }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun update(webhook: WebhookConfiguration): WebhookConfiguration {
        webhooks[webhook.id] = webhook
        return webhook
    }

    override suspend fun delete(id: String): Boolean {
        return webhooks.remove(id) != null
    }

    override suspend fun countByUserId(userId: String): Long {
        return webhooks.values.count { it.userId == userId }.toLong()
    }

    override suspend fun findActiveByEvent(event: WebhookEvent): List<WebhookConfiguration> {
        return webhooks.values
            .filter { it.isActive && it.events.contains(event) }
            .sortedByDescending { it.createdAt }
    }
}

class InMemoryWebhookDeliveryRepository : WebhookDeliveryRepository {
    private val deliveries = ConcurrentHashMap<String, WebhookDelivery>()

    override suspend fun save(delivery: WebhookDelivery): WebhookDelivery {
        deliveries[delivery.id] = delivery
        return delivery
    }

    override suspend fun findById(id: String): WebhookDelivery? {
        return deliveries[id]
    }

    override suspend fun findByWebhookConfigId(webhookConfigId: String, limit: Int): List<WebhookDelivery> {
        return deliveries.values
            .filter { it.webhookConfigId == webhookConfigId }
            .sortedByDescending { it.createdAt }
            .take(limit)
    }

    override suspend fun findByUserId(userId: String, limit: Int): List<WebhookDelivery> {
        return deliveries.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
            .take(limit)
    }

    override suspend fun findPendingDeliveries(limit: Int): List<WebhookDelivery> {
        return deliveries.values
            .filter { it.status == WebhookDeliveryStatus.PENDING }
            .sortedBy { it.createdAt }
            .take(limit)
    }

    override suspend fun findFailedDeliveriesForRetry(before: Instant, limit: Int): List<WebhookDelivery> {
        return deliveries.values
            .filter {
                it.status == WebhookDeliveryStatus.RETRYING &&
                it.nextRetryAt != null &&
                it.nextRetryAt <= before
            }
            .sortedBy { it.nextRetryAt }
            .take(limit)
    }

    override suspend fun update(delivery: WebhookDelivery): WebhookDelivery {
        deliveries[delivery.id] = delivery
        return delivery
    }

    override suspend fun countByStatus(status: WebhookDeliveryStatus): Long {
        return deliveries.values.count { it.status == status }.toLong()
    }

    override suspend fun countByWebhookConfigId(webhookConfigId: String): Long {
        return deliveries.values.count { it.webhookConfigId == webhookConfigId }.toLong()
    }

    override suspend fun deleteOlderThan(before: Instant): Int {
        val toDelete = deliveries.values.filter { it.createdAt < before }
        toDelete.forEach { deliveries.remove(it.id) }
        return toDelete.size
    }

    override suspend fun getDeliveryStats(webhookConfigId: String, since: Instant): WebhookDeliveryStats {
        val deliveriesForConfig = deliveries.values
            .filter { it.webhookConfigId == webhookConfigId && it.createdAt >= since }

        val total = deliveriesForConfig.size.toLong()
        val delivered = deliveriesForConfig.count { it.status == WebhookDeliveryStatus.DELIVERED }.toLong()
        val failed = deliveriesForConfig.count { it.status == WebhookDeliveryStatus.FAILED }.toLong()
        val pending = deliveriesForConfig.count {
            it.status in listOf(
                WebhookDeliveryStatus.PENDING,
                WebhookDeliveryStatus.RETRYING,
                WebhookDeliveryStatus.DELIVERING
            )
        }.toLong()

        val responseTimes = deliveriesForConfig
            .mapNotNull { it.responseTimeMs }
            .filter { it > 0 }

        val avgResponseTime = if (responseTimes.isNotEmpty()) {
            responseTimes.average().toLong()
        } else null

        val successRate = if (total > 0) {
            delivered.toDouble() / total.toDouble()
        } else 0.0

        return WebhookDeliveryStats(
            total = total,
            delivered = delivered,
            failed = failed,
            pending = pending,
            averageResponseTimeMs = avgResponseTime,
            successRate = successRate
        )
    }

    override suspend fun getSuccessRate(webhookConfigId: String, since: Instant): Double {
        val stats = getDeliveryStats(webhookConfigId, since)
        return stats.successRate
    }
}
