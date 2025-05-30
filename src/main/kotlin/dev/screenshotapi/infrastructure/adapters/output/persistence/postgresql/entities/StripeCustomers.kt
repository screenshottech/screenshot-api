package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object StripeCustomers : IdTable<String>("stripe_customers") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)

    val userId = reference("user_id", Users)
    val stripeCustomerId = varchar("stripe_customer_id", 100).uniqueIndex()
    val subscriptionId = varchar("subscription_id", 100).nullable()
    val subscriptionStatus = varchar("subscription_status", 50).nullable()
    val currentPeriodStart = timestamp("current_period_start").nullable()
    val currentPeriodEnd = timestamp("current_period_end").nullable()
    val cancelAtPeriodEnd = bool("cancel_at_period_end").default(false)
    val trialEnd = timestamp("trial_end").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
