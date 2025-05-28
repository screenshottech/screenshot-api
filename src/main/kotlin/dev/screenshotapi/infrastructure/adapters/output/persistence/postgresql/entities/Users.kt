package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255).nullable()
    val passwordHash = varchar("password_hash", 255)
    val planId = reference("plan_id", Plans)
    val creditsRemaining = integer("credits_remaining").default(0)
    val status = varchar("status", 20).default("ACTIVE")
    val stripeCustomerId = varchar("stripe_customer_id", 100).nullable()
    val lastActivity = timestamp("last_activity").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
