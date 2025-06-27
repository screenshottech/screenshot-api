package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Table for storing webhook configurations
 */
object WebhookConfigurations : Table("webhook_configurations") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50)
    val url = text("url")
    val secret = varchar("secret", 255)
    val events = text("events") // JSON array of event names
    val isActive = bool("is_active").default(true)
    val description = text("description").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        index(false, userId)
        index(false, isActive)
        index(false, userId, isActive)
    }
}

/**
 * Table for tracking webhook delivery attempts
 */
object WebhookDeliveries : Table("webhook_deliveries") {
    val id = varchar("id", 50)
    val webhookConfigId = varchar("webhook_config_id", 50)
    val userId = varchar("user_id", 50)
    val event = varchar("event", 50)
    val eventData = text("event_data") // JSON
    val payload = text("payload") // JSON payload sent
    val signature = varchar("signature", 255)
    val status = varchar("status", 20)
    val url = text("url")
    val attempts = integer("attempts").default(1)
    val maxAttempts = integer("max_attempts").default(5)
    val lastAttemptAt = timestamp("last_attempt_at")
    val nextRetryAt = timestamp("next_retry_at").nullable()
    val responseCode = integer("response_code").nullable()
    val responseBody = text("response_body").nullable()
    val responseTimeMs = long("response_time_ms").nullable()
    val error = text("error").nullable()
    val createdAt = timestamp("created_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        index(false, webhookConfigId)
        index(false, userId)
        index(false, status)
        index(false, status, nextRetryAt) // For finding deliveries to retry
        index(false, createdAt) // For cleanup queries
        index(false, webhookConfigId, createdAt) // For historical queries
    }
}