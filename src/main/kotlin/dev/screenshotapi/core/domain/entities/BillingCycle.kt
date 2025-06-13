package dev.screenshotapi.core.domain.entities

/**
 * Represents the billing cycle for a subscription.
 * Encapsulates billing period logic and price calculations.
 */
enum class BillingCycle {
    MONTHLY,
    ANNUAL;
    
    /**
     * Gets the appropriate price from a plan based on this billing cycle.
     */
    fun getPriceFromPlan(plan: Plan): Int {
        return when (this) {
            MONTHLY -> plan.priceCentsMonthly
            ANNUAL -> plan.priceCentsAnnual ?: (plan.priceCentsMonthly * 12 * 0.8).toInt() // 20% discount
        }
    }
    
    /**
     * Gets the total credits for this billing cycle.
     * Annual plans get bonus credits.
     */
    fun getCreditsFromPlan(plan: Plan): Int {
        return when (this) {
            MONTHLY -> plan.creditsPerMonth
            ANNUAL -> (plan.creditsPerMonth * 12 * 1.2).toInt() // 20% bonus for annual
        }
    }
    
    /**
     * Gets the number of months in this billing cycle.
     */
    fun getMonthCount(): Int {
        return when (this) {
            MONTHLY -> 1
            ANNUAL -> 12
        }
    }
    
    /**
     * Converts to string for external systems and database storage.
     */
    fun toExternalString(): String {
        return when (this) {
            MONTHLY -> "monthly"
            ANNUAL -> "annual"
        }
    }
    
    companion object {
        /**
         * Creates BillingCycle from string representation.
         * Used for parsing external data and API inputs.
         */
        fun fromString(value: String?): BillingCycle {
            return when (value?.lowercase()) {
                "annual", "yearly" -> ANNUAL
                "monthly", null -> MONTHLY
                else -> MONTHLY // Default fallback
            }
        }
    }
}