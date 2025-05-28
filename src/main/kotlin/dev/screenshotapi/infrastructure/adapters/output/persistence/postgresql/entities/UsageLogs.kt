package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object UsageLogs : UUIDTable("usage_logs") {
    val userId = reference("user_id", Users)
    val apiKeyId = reference("api_key_id", ApiKeys).nullable()
    val screenshotId = reference("screenshot_id", Screenshots).nullable()
    val action = varchar("action", 50) // SCREENSHOT_CREATED, CREDITS_DEDUCTED, etc.
    val creditsUsed = integer("credits_used").default(0)
    val metadata = text("metadata").nullable() // JSON with additional info
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val timestamp = datetime("timestamp")
}
