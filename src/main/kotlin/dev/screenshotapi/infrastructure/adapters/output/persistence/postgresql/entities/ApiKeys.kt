package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ApiKeys : IdTable<String>("api_keys") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)

    val userId = reference("user_id", Users).index("idx_api_keys_user_id")
    val name = varchar("name", 255)
    val keyHash = varchar("key_hash", 255).index(customIndexName = "idx_api_keys_key_hash", isUnique = true)
    val keyPrefix = varchar("key_prefix", 50)
    val permissions = text("permissions") // JSON array stored as text
    val rateLimit = integer("rate_limit").default(1000)
    val usageCount = long("usage_count").default(0)
    val isActive = bool("is_active").default(true)
    val isDefault = bool("is_default").default(false)
    val lastUsed = timestamp("last_used").nullable()
    val expiresAt = timestamp("expires_at").nullable()
    val createdAt = timestamp("created_at")
}
