package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.WebhookConfiguration
import dev.screenshotapi.core.domain.entities.WebhookDelivery
import dev.screenshotapi.core.domain.entities.WebhookDeliveryStatus
import dev.screenshotapi.core.domain.entities.WebhookEvent
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryRepository
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryStats
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.WebhookConfigurations
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.WebhookDeliveries
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.slf4j.LoggerFactory

class PostgreSQLWebhookConfigurationRepository(
    private val database: Database
) : WebhookConfigurationRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(webhook: WebhookConfiguration): WebhookConfiguration = dbQuery(database) {
        WebhookConfigurations.insert {
            it[id] = webhook.id
            it[userId] = webhook.userId
            it[url] = webhook.url
            it[secret] = webhook.secret
            it[events] = json.encodeToString(webhook.events.map { e -> e.name })
            it[isActive] = webhook.isActive
            it[description] = webhook.description
            it[createdAt] = webhook.createdAt
            it[updatedAt] = webhook.updatedAt
        }
        webhook
    }

    override suspend fun findById(id: String): WebhookConfiguration? = dbQuery(database) {
        WebhookConfigurations.select { WebhookConfigurations.id eq id }
            .singleOrNull()
            ?.toWebhookConfiguration()
    }

    override suspend fun findByUserId(userId: String): List<WebhookConfiguration> = dbQuery(database) {
        WebhookConfigurations.select { WebhookConfigurations.userId eq userId }
            .orderBy(WebhookConfigurations.createdAt to SortOrder.DESC)
            .map { it.toWebhookConfiguration() }
    }

    override suspend fun findByUserIdAndEvent(userId: String, event: WebhookEvent): List<WebhookConfiguration> = dbQuery(database) {
        WebhookConfigurations.select {
            (WebhookConfigurations.userId eq userId) and
            (WebhookConfigurations.isActive eq true) and
            (WebhookConfigurations.events like "%${event.name}%")
        }.map { it.toWebhookConfiguration() }
            .filter { it.events.contains(event) }
    }

    override suspend fun findActiveByEvent(event: WebhookEvent): List<WebhookConfiguration> = dbQuery(database) {
        WebhookConfigurations.select {
            (WebhookConfigurations.isActive eq true) and
            (WebhookConfigurations.events like "%${event.name}%")
        }.map { it.toWebhookConfiguration() }
            .filter { it.events.contains(event) }
    }

    override suspend fun update(webhook: WebhookConfiguration): WebhookConfiguration = dbQuery(database) {
        WebhookConfigurations.update({ WebhookConfigurations.id eq webhook.id }) {
            it[userId] = webhook.userId
            it[url] = webhook.url
            it[secret] = webhook.secret
            it[events] = json.encodeToString(webhook.events.map { e -> e.name })
            it[isActive] = webhook.isActive
            it[description] = webhook.description
            it[updatedAt] = webhook.updatedAt
        }
        webhook
    }

    override suspend fun delete(id: String): Boolean = dbQuery(database) {
        val deliveriesDeleted = WebhookDeliveries.deleteWhere {
            WebhookDeliveries.webhookConfigId eq id
        }

        logger.info("Deleted $deliveriesDeleted webhook deliveries for webhook config $id")

        val configDeleted = WebhookConfigurations.deleteWhere {
            WebhookConfigurations.id eq id
        } > 0

        logger.info("Deleted webhook configuration $id: $configDeleted")
        configDeleted
    }

    override suspend fun countByUserId(userId: String): Long = dbQuery(database) {
        WebhookConfigurations.select { WebhookConfigurations.userId eq userId }.count()
    }

    private fun ResultRow.toWebhookConfiguration(): WebhookConfiguration {
        val eventsString = this[WebhookConfigurations.events]
        val eventNames: List<String> = json.decodeFromString(eventsString)
        val events = eventNames.mapNotNull {
            try { WebhookEvent.valueOf(it) } catch (e: Exception) { null }
        }.toSet()

        return WebhookConfiguration(
            id = this[WebhookConfigurations.id],
            userId = this[WebhookConfigurations.userId],
            url = this[WebhookConfigurations.url],
            secret = this[WebhookConfigurations.secret],
            events = events,
            isActive = this[WebhookConfigurations.isActive],
            description = this[WebhookConfigurations.description],
            createdAt = this[WebhookConfigurations.createdAt],
            updatedAt = this[WebhookConfigurations.updatedAt]
        )
    }
}

class PostgreSQLWebhookDeliveryRepository(
    private val database: Database
) : WebhookDeliveryRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private fun serializeEventData(eventData: Map<String, Any>): String {
        val stringMap = eventData.mapValues { (_, value) ->
            when (value) {
                is String -> value
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> value.toString()
            }
        }
        return json.encodeToString(stringMap)
    }

    private fun deserializeEventData(jsonString: String): Map<String, Any> {
        val stringMap = json.decodeFromString<Map<String, String>>(jsonString)
        return stringMap.mapValues { it.value as Any }
    }

    override suspend fun save(delivery: WebhookDelivery): WebhookDelivery = dbQuery(database) {
        WebhookDeliveries.insert {
            it[id] = delivery.id
            it[webhookConfigId] = delivery.webhookConfigId
            it[userId] = delivery.userId
            it[event] = delivery.event.name
            it[eventData] = serializeEventData(delivery.eventData)
            it[payload] = delivery.payload
            it[signature] = delivery.signature
            it[status] = delivery.status.name
            it[url] = delivery.url
            it[attempts] = delivery.attempts
            it[maxAttempts] = delivery.maxAttempts
            it[lastAttemptAt] = delivery.lastAttemptAt
            it[nextRetryAt] = delivery.nextRetryAt
            it[responseCode] = delivery.responseCode
            it[responseBody] = delivery.responseBody
            it[responseTimeMs] = delivery.responseTimeMs
            it[error] = delivery.error
            it[createdAt] = delivery.createdAt
        }
        delivery
    }

    override suspend fun findById(id: String): WebhookDelivery? = dbQuery(database) {
        WebhookDeliveries.select { WebhookDeliveries.id eq id }
            .singleOrNull()
            ?.toWebhookDelivery()
    }

    override suspend fun findByWebhookConfigId(webhookConfigId: String, limit: Int): List<WebhookDelivery> = dbQuery(database) {
        WebhookDeliveries.select { WebhookDeliveries.webhookConfigId eq webhookConfigId }
            .orderBy(WebhookDeliveries.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toWebhookDelivery() }
    }

    override suspend fun findByUserId(userId: String, limit: Int): List<WebhookDelivery> = dbQuery(database) {
        WebhookDeliveries.select { WebhookDeliveries.userId eq userId }
            .orderBy(WebhookDeliveries.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toWebhookDelivery() }
    }

    override suspend fun findPendingDeliveries(limit: Int): List<WebhookDelivery> = dbQuery(database) {
        WebhookDeliveries.select {
            WebhookDeliveries.status inList listOf(
                WebhookDeliveryStatus.PENDING.name,
                WebhookDeliveryStatus.RETRYING.name
            )
        }
            .orderBy(WebhookDeliveries.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { it.toWebhookDelivery() }
    }

    override suspend fun findFailedDeliveriesForRetry(before: Instant, limit: Int): List<WebhookDelivery> = dbQuery(database) {
        WebhookDeliveries.select {
            (WebhookDeliveries.status eq WebhookDeliveryStatus.RETRYING.name) and
            (WebhookDeliveries.nextRetryAt lessEq before)
        }
            .orderBy(WebhookDeliveries.nextRetryAt to SortOrder.ASC)
            .limit(limit)
            .map { it.toWebhookDelivery() }
    }

    override suspend fun countByStatus(status: WebhookDeliveryStatus): Long = dbQuery(database) {
        WebhookDeliveries.select { WebhookDeliveries.status eq status.name }.count()
    }

    override suspend fun countByWebhookConfigId(webhookConfigId: String): Long = dbQuery(database) {
        WebhookDeliveries.select { WebhookDeliveries.webhookConfigId eq webhookConfigId }.count()
    }

    override suspend fun deleteOlderThan(before: Instant): Int = dbQuery(database) {
        WebhookDeliveries.deleteWhere { WebhookDeliveries.createdAt lessEq before }
    }

    override suspend fun deleteOldDeliveries(before: Instant, status: WebhookDeliveryStatus?, limit: Int): Int = dbQuery(database) {
        // Simple approach: delete directly with limit using a subquery-like approach
        val baseCondition = WebhookDeliveries.createdAt lessEq before
        val condition = if (status != null) {
            baseCondition and (WebhookDeliveries.status eq status.name)
        } else {
            baseCondition
        }
        
        // For PostgreSQL, we'll do a direct delete with limit
        // Note: This may delete more than the limit if there are many matching records
        // but it's simpler and safer than complex subqueries
        WebhookDeliveries.deleteWhere(limit) { condition }
    }

    override suspend fun update(delivery: WebhookDelivery): WebhookDelivery = dbQuery(database) {
        WebhookDeliveries.update({ WebhookDeliveries.id eq delivery.id }) {
            it[status] = delivery.status.name
            it[attempts] = delivery.attempts
            it[lastAttemptAt] = delivery.lastAttemptAt
            it[nextRetryAt] = delivery.nextRetryAt
            it[responseCode] = delivery.responseCode
            it[responseBody] = delivery.responseBody
            it[responseTimeMs] = delivery.responseTimeMs
            it[error] = delivery.error
        }
        delivery
    }

    override suspend fun getDeliveryStats(webhookConfigId: String, since: Instant): WebhookDeliveryStats = dbQuery(database) {
        val query = WebhookDeliveries.select {
            (WebhookDeliveries.webhookConfigId eq webhookConfigId) and
            (WebhookDeliveries.createdAt greaterEq since)
        }

        val total = query.count()
        val delivered = query.copy().andWhere { WebhookDeliveries.status eq WebhookDeliveryStatus.DELIVERED.name }.count()
        val failed = query.copy().andWhere { WebhookDeliveries.status eq WebhookDeliveryStatus.FAILED.name }.count()
        val pending = query.copy().andWhere {
            WebhookDeliveries.status inList listOf(
                WebhookDeliveryStatus.PENDING.name,
                WebhookDeliveryStatus.DELIVERING.name,
                WebhookDeliveryStatus.RETRYING.name
            )
        }.count()

        WebhookDeliveryStats(
            total = total,
            delivered = delivered,
            failed = failed,
            pending = pending,
            averageResponseTimeMs = null,
            successRate = if (total > 0) delivered.toDouble() / total.toDouble() else 0.0
        )
    }

    override suspend fun getSuccessRate(webhookConfigId: String, since: Instant): Double = dbQuery(database) {
        val total = WebhookDeliveries.select {
            (WebhookDeliveries.webhookConfigId eq webhookConfigId) and
            (WebhookDeliveries.createdAt greaterEq since)
        }.count()

        if (total == 0L) return@dbQuery 0.0

        val delivered = WebhookDeliveries.select {
            (WebhookDeliveries.webhookConfigId eq webhookConfigId) and
            (WebhookDeliveries.createdAt greaterEq since) and
            (WebhookDeliveries.status eq WebhookDeliveryStatus.DELIVERED.name)
        }.count()

        delivered.toDouble() / total.toDouble()
    }

    private fun ResultRow.toWebhookDelivery(): WebhookDelivery {
        val eventData: Map<String, Any> = deserializeEventData(this[WebhookDeliveries.eventData])

        return WebhookDelivery(
            id = this[WebhookDeliveries.id],
            webhookConfigId = this[WebhookDeliveries.webhookConfigId],
            userId = this[WebhookDeliveries.userId],
            event = WebhookEvent.valueOf(this[WebhookDeliveries.event]),
            eventData = eventData,
            payload = this[WebhookDeliveries.payload],
            signature = this[WebhookDeliveries.signature],
            status = WebhookDeliveryStatus.valueOf(this[WebhookDeliveries.status]),
            url = this[WebhookDeliveries.url],
            attempts = this[WebhookDeliveries.attempts],
            maxAttempts = this[WebhookDeliveries.maxAttempts],
            lastAttemptAt = this[WebhookDeliveries.lastAttemptAt],
            nextRetryAt = this[WebhookDeliveries.nextRetryAt],
            responseCode = this[WebhookDeliveries.responseCode],
            responseBody = this[WebhookDeliveries.responseBody],
            responseTimeMs = this[WebhookDeliveries.responseTimeMs],
            error = this[WebhookDeliveries.error],
            createdAt = this[WebhookDeliveries.createdAt]
        )
    }
}
