package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

/**
 * Represents a user's subscription to a plan.
 * Designed to be compatible with future team functionality.
 */
data class Subscription(
    val id: String,
    val userId: String, // Owner of subscription (future: team owner)
    val planId: String,
    val billingCycle: BillingCycle,
    val status: SubscriptionStatus,
    val stripeSubscriptionId: String? = null,
    val stripeCustomerId: String? = null,
    val currentPeriodStart: Instant,
    val currentPeriodEnd: Instant,
    val cancelAtPeriodEnd: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    init {
        require(id.isNotBlank()) { "Subscription ID cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(planId.isNotBlank()) { "Plan ID cannot be blank" }
        // billingCycle is now type-safe enum, no validation needed
    }
    
    /**
     * Checks if the subscription is currently active.
     */
    fun isActive(): Boolean {
        return status == SubscriptionStatus.ACTIVE && 
               kotlinx.datetime.Clock.System.now() < currentPeriodEnd
    }
    
    /**
     * Checks if the subscription will cancel at period end.
     */
    fun willCancelAtPeriodEnd(): Boolean {
        return status == SubscriptionStatus.ACTIVE && cancelAtPeriodEnd
    }
    
    /**
     * Gets the appropriate price from a plan based on billing cycle.
     */
    fun getPriceFromPlan(plan: Plan): Int {
        return billingCycle.getPriceFromPlan(plan)
    }
    
    /**
     * Gets the total credits for the billing period.
     */
    fun getTotalCreditsFromPlan(plan: Plan): Int {
        return billingCycle.getCreditsFromPlan(plan)
    }
}