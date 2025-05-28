package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities


import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ApiKeys : UUIDTable("api_keys") {
    val userId = reference("user_id", Users)
    val keyHash = varchar("key_hash", 64).uniqueIndex()
    val name = varchar("name", 100)
    val permissions = text("permissions") // JSON array
    val rateLimit = integer("rate_limit").default(1000) // per hour
    val isActive = bool("is_active").default(true)
    val lastUsed = timestamp("last_used").nullable()
    val createdAt = timestamp("created_at")
}
