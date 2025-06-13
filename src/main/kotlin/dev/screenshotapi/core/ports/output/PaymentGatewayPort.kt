package dev.screenshotapi.core.ports.output

import dev.screenshotapi.core.domain.entities.BillingCycle
import kotlinx.datetime.Instant

/**
 * Port interface for payment gateway operations.
 * Following hexagonal architecture - this is implemented by infrastructure adapters.
 */
interface PaymentGatewayPort {
    /**
     * The name of this payment gateway provider.
     */
    val providerName: String
    
    /**
     * Creates a checkout session for subscription.
     */
    suspend fun createCheckoutSession(
        userId: String,
        customerEmail: String?,
        planId: String,
        billingCycle: BillingCycle,
        successUrl: String,
        cancelUrl: String
    ): CheckoutSessionResult
    
    /**
     * Retrieves subscription information from the payment gateway.
     */
    suspend fun getSubscription(
        subscriptionId: String
    ): SubscriptionResult?
    
    /**
     * Cancels a subscription at period end.
     */
    suspend fun cancelSubscription(
        subscriptionId: String,
        cancelAtPeriodEnd: Boolean = true
    ): SubscriptionResult?
    
    /**
     * Creates a portal session for customer to manage billing.
     */
    suspend fun createBillingPortalSession(
        customerId: String,
        returnUrl: String
    ): String
    
    /**
     * Handles a webhook event from the payment gateway.
     */
    suspend fun handleWebhookEvent(
        payload: String,
        signature: String
    ): WebhookEventResult

    /**
     * Data class to represent checkout session result.
     */
    data class CheckoutSessionResult(
        val sessionId: String,
        val url: String,
        val customerId: String?
    )
    
    /**
     * Data class to represent subscription data from gateway.
     */
    data class SubscriptionResult(
        val subscriptionId: String,
        val status: String,
        val currentPeriodStart: Instant,
        val currentPeriodEnd: Instant,
        val customerId: String,
        val cancelAtPeriodEnd: Boolean = false
    )
    
    /**
     * Data class to represent webhook event data from gateway.
     */
    data class WebhookEventResult(
        val eventType: String,
        val objectId: String,
        val objectType: String,
        val data: Map<String, Any>
    )
}