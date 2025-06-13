package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Plans : IdTable<String>("plans") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)
    
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val creditsPerMonth = integer("credits_per_month")
    val priceCentsMonthly = integer("price_cents_monthly")
    val priceCentsAnnual = integer("price_cents_annual").nullable()
    val billingCycle = varchar("billing_cycle", 20).default("monthly") // 'monthly' or 'annual'
    val currency = varchar("currency", 10).default("USD")
    val features = text("features").nullable() // JSONB stored as text
    val isActive = bool("is_active").default(true)
    val stripeProductId = varchar("stripe_product_id", 100).nullable()
    val stripePriceIdMonthly = varchar("stripe_price_id_monthly", 100).nullable()
    val stripePriceIdAnnual = varchar("stripe_price_id_annual", 100).nullable()
    val stripeMetadata = text("stripe_metadata").nullable()
    val sortOrder = integer("sort_order").default(0)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
