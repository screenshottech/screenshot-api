package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Database table for monthly usage tracking
 */
object UsageTracking : Table("usage_tracking") {
    val userId = reference("user_id", Users)
    val month = varchar("month", 7) // Format: "2025-01"
    val totalRequests = integer("total_requests").default(0)
    val planCreditsLimit = integer("plan_credits_limit")
    val remainingCredits = integer("remaining_credits")
    val lastRequestAt = timestamp("last_request_at")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId, month)
}
