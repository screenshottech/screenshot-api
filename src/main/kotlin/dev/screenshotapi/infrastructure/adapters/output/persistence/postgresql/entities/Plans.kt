package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Plans : UUIDTable("plans") {
    val name = varchar("name", 100)
    val stripePriceId = varchar("stripe_price_id", 100).nullable()
    val creditsPerMonth = integer("credits_per_month")
    val priceInCents = integer("price_in_cents").default(0)
    val features = text("features") // JSON array
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
