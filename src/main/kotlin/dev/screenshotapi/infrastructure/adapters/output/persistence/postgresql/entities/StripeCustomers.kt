package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object StripeCustomers : UUIDTable("stripe_customers") {
    val userId = reference("user_id", Users) // ← Usar reference
    val stripeCustomerId = varchar("stripe_customer_id", 100).uniqueIndex()
    val subscriptionId = varchar("subscription_id", 100).nullable()
    val subscriptionStatus = varchar("subscription_status", 50).nullable()
    val currentPeriodStart = datetime("current_period_start").nullable()
    val currentPeriodEnd = datetime("current_period_end").nullable()
    val cancelAtPeriodEnd = bool("cancel_at_period_end").default(false)
    val trialEnd = datetime("trial_end").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
