package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for subscriptions.
 * Following existing table patterns in the codebase.
 */
object Subscriptions : Table("subscriptions") {
    val id = varchar("id", 255)
    val userId = varchar("user_id", 255).references(Users.id)
    val planId = varchar("plan_id", 255).references(Plans.id)
    val billingCycle = varchar("billing_cycle", 20) // "monthly" or "annual"
    val status = varchar("status", 50)
    val stripeSubscriptionId = varchar("stripe_subscription_id", 255).nullable()
    val stripeCustomerId = varchar("stripe_customer_id", 255).nullable()
    val currentPeriodStart = timestamp("current_period_start")
    val currentPeriodEnd = timestamp("current_period_end")
    val cancelAtPeriodEnd = bool("cancel_at_period_end").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        // Add indexes for common queries
        index(false, userId)
        index(false, stripeSubscriptionId)
        index(false, stripeCustomerId)
        index(false, status)
    }
}