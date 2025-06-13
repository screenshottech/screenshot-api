package dev.screenshotapi.core.domain.entities

import dev.screenshotapi.core.domain.exceptions.ValidationException

/**
 * Represents the status of a subscription.
 * Maps to Stripe subscription statuses but remains provider-agnostic.
 */
enum class SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    CANCELED,
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    UNPAID,
    TRIALING;
    
    companion object {
        /**
         * Maps a string status to our domain subscription status.
         * Provider-agnostic mapping.
         */
        fun fromString(status: String): SubscriptionStatus {
            return when (status.lowercase()) {
                "active" -> ACTIVE
                "past_due" -> PAST_DUE
                "canceled" -> CANCELED
                "incomplete" -> INCOMPLETE
                "incomplete_expired" -> INCOMPLETE_EXPIRED
                "unpaid" -> UNPAID
                "trialing" -> TRIALING
                else -> throw ValidationException("Unknown subscription status: $status", "status")
            }
        }
        
        /**
         * Converts to string representation for external systems.
         */
        fun SubscriptionStatus.toExternalString(): String {
            return this.name.lowercase()
        }
    }
}