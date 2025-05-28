package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Activities : UUIDTable("activities") {
    val userId = reference("user_id", Users) // ← Usar reference
    val type = varchar("type", 50) // SCREENSHOT_CREATED, LOGIN, etc.
    val description = text("description")
    val metadata = text("metadata").nullable() // JSON
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val timestamp = datetime("timestamp")
}
