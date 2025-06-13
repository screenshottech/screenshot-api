package dev.screenshotapi.infrastructure.adapters.input.rest.dto

import dev.screenshotapi.core.domain.entities.BillingCycle
import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import kotlinx.serialization.Serializable

// Request DTOs
@Serializable
data class CreateCheckoutSessionRequestDto(
    val planId: String,
    val billingCycle: String, // "monthly" or "annual"
    val successUrl: String,
    val cancelUrl: String
)

@Serializable
data class HandleWebhookRequestDto(
    val payload: String,
    val signature: String
)

// Response DTOs
@Serializable
data class AvailablePlansResponseDto(
    val plans: List<PlanDto>
)

@Serializable
data class PlanDto(
    val id: String,
    val name: String,
    val description: String?,
    val creditsPerMonth: Int,
    val priceCentsMonthly: Int,
    val priceCentsAnnual: Int?,
    val features: List<String>
)

@Serializable
data class CreateCheckoutSessionResponseDto(
    val sessionId: String,
    val url: String,
    val customerId: String?
)

@Serializable
data class UserSubscriptionResponseDto(
    val subscriptionId: String?,
    val status: String,
    val planId: String?,
    val billingCycle: String?,
    val currentPeriodStart: String?,
    val currentPeriodEnd: String?,
    val cancelAtPeriodEnd: Boolean
)

@Serializable
data class WebhookResponseDto(
    val received: Boolean,
    val processed: Boolean,
    val eventType: String?
)

@Serializable
data class BillingPortalResponseDto(
    val url: String,
    val created: Boolean
)

// Extension functions to convert from domain to DTO
fun dev.screenshotapi.core.domain.entities.Plan.toDto(): PlanDto {
    return PlanDto(
        id = this.id,
        name = this.name,
        description = this.description,
        creditsPerMonth = this.creditsPerMonth,
        priceCentsMonthly = this.priceCentsMonthly,
        priceCentsAnnual = this.priceCentsAnnual,
        features = this.features
    )
}

fun String.toBillingCycle(): BillingCycle {
    return BillingCycle.fromString(this)
}

fun SubscriptionStatus.toExternalString(): String {
    return when (this) {
        SubscriptionStatus.ACTIVE -> "active"
        SubscriptionStatus.PAST_DUE -> "past_due"
        SubscriptionStatus.CANCELED -> "canceled"
        SubscriptionStatus.INCOMPLETE -> "incomplete"
        SubscriptionStatus.INCOMPLETE_EXPIRED -> "incomplete_expired"
        SubscriptionStatus.TRIALING -> "trialing"
        SubscriptionStatus.UNPAID -> "unpaid"
        // SubscriptionStatus.PAUSED -> "paused" // Not defined in current enum
    }
}