package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Activities : IdTable<String>("activities") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)

    val userId = reference("user_id", Users).index("idx_activities_user_id")
    val type = varchar("type", 50) // SCREENSHOT_CREATED, LOGIN, etc.
    val description = text("description")
    val metadata = text("metadata").nullable() // JSON
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val timestamp = timestamp("timestamp")
}
