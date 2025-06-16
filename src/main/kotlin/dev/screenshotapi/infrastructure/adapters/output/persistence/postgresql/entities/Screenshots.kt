package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Screenshots : IdTable<String>("screenshots") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)
    
    val userId = reference("user_id", Users)
    val apiKeyId = reference("api_key_id", ApiKeys)
    val url = text("url")
    val status = varchar("status", 20) // QUEUED, PROCESSING, COMPLETED, FAILED
    val resultUrl = text("result_url").nullable()
    val options = text("options") // JSON with screenshotapi parameters
    val processingTimeMs = long("processing_time_ms").nullable()
    val fileSizeBytes = long("file_size_bytes").nullable()
    val errorMessage = text("error_message").nullable()
    val webhookUrl = text("webhook_url").nullable()
    val webhookSent = bool("webhook_sent").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val completedAt = timestamp("completed_at").nullable()
}
