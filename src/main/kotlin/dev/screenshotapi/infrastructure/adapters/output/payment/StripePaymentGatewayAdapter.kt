package dev.screenshotapi.infrastructure.adapters.output.payment

import com.stripe.StripeClient
import com.stripe.exception.StripeException
import com.stripe.model.Event
import com.stripe.model.Subscription
import com.stripe.model.Invoice
import com.stripe.model.StripeObject
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams
import com.stripe.param.SubscriptionUpdateParams
import dev.screenshotapi.core.domain.entities.BillingCycle
import dev.screenshotapi.core.domain.exceptions.PaymentException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.ports.output.PaymentGatewayPort
import dev.screenshotapi.infrastructure.config.StripeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import com.stripe.net.Webhook
import kotlinx.datetime.toKotlinInstant

/**
 * Stripe implementation of PaymentGatewayPort.
 *
 * Key Design Decisions:
 * 1. Uses modern StripeClient (v23+) instead of legacy pattern
 * 2. Wraps blocking Stripe calls with coroutines (withContext)
 * 3. Normalizes Stripe events to standard domain events
 * 4. Robust error handling with domain exceptions
 * 5. Comprehensive configuration with timeouts and retries
 */
class StripePaymentGatewayAdapter(
    private val config: StripeConfig,
    private val planRepository: dev.screenshotapi.core.domain.repositories.PlanRepository
) : PaymentGatewayPort {

    private val logger = LoggerFactory.getLogger(StripePaymentGatewayAdapter::class.java)
    
    // Check if Stripe credentials are placeholder/invalid
    private val isValidConfig = !config.secretKey.startsWith("sk_test_placeholder") && 
                                !config.publishableKey.startsWith("pk_test_placeholder") &&
                                !config.webhookSecret.startsWith("whsec_placeholder")

    // Modern StripeClient with configuration (only if valid)
    private val stripeClient = if (isValidConfig) {
        StripeClient.builder()
            .setApiKey(config.secretKey)
            .setConnectTimeout(config.connectTimeout)
            .setReadTimeout(config.readTimeout)
            .setMaxNetworkRetries(config.maxNetworkRetries)
            .build()
    } else {
        logger.warn("Stripe credentials are not configured. Using placeholder values.")
        null
    }

    override val providerName: String = "stripe"

    override suspend fun createCheckoutSession(
        userId: String,
        customerEmail: String?,
        planId: String,
        billingCycle: BillingCycle,
        successUrl: String,
        cancelUrl: String
    ): PaymentGatewayPort.CheckoutSessionResult = withContext(Dispatchers.IO) {
        
        // Check if Stripe is properly configured
        if (!isValidConfig || stripeClient == null) {
            throw PaymentException.ConfigurationError("Stripe is not properly configured. Please set valid STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, and STRIPE_WEBHOOK_SECRET environment variables.")
        }
        
        try {
            logger.info("Creating Stripe checkout session for user: $userId, plan: $planId, cycle: $billingCycle")

            // Get Stripe price ID from database
            val stripePriceId = getPriceIdForPlan(planId, billingCycle)

            // Build checkout session parameters
            val sessionParams = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .apply {
                    // Add customer email if provided
                    customerEmail?.let { setCustomerEmail(it) }

                    // Add metadata for tracking
                    putMetadata("userId", userId)
                    putMetadata("planId", planId)
                    putMetadata("billingCycle", billingCycle.toExternalString())

                    // Configure line item based on plan and billing cycle
                    addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setPrice(stripePriceId)
                            .setQuantity(1L)
                            .build()
                    )

                    // Configure subscription data
                    setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                            .putMetadata("userId", userId)
                            .putMetadata("planId", planId)
                            .putMetadata("billingCycle", billingCycle.toExternalString())
                            .build()
                    )
                }
                .build()

            // Create session via Stripe
            val session = stripeClient.checkout().sessions().create(sessionParams)

            logger.info("Stripe checkout session created: ${session.id}")

            PaymentGatewayPort.CheckoutSessionResult(
                sessionId = session.id,
                url = session.url,
                customerId = session.customer
            )

        } catch (e: StripeException) {
            logger.error("Stripe error creating checkout session: ${e.message}", e)
            
            // Sanitize error message to avoid exposing sensitive information
            val sanitizedMessage = when {
                e.message?.contains("Invalid API Key", ignoreCase = true) == true -> 
                    "Payment service configuration error"
                e.message?.contains("No such price", ignoreCase = true) == true -> 
                    "Selected plan is not available"
                e.message?.contains("No such customer", ignoreCase = true) == true -> 
                    "Customer account error"
                else -> "Payment service temporarily unavailable"
            }
            
            throw PaymentException.PaymentFailed("Failed to create checkout session: $sanitizedMessage")
        } catch (e: Exception) {
            logger.error("Unexpected error creating checkout session", e)
            throw PaymentException.PaymentFailed("Unexpected error during checkout creation")
        }
    }

    override suspend fun getSubscription(
        subscriptionId: String
    ): PaymentGatewayPort.SubscriptionResult? = withContext(Dispatchers.IO) {
        
        // Check if Stripe is properly configured
        if (!isValidConfig || stripeClient == null) {
            throw PaymentException.ConfigurationError("Stripe is not properly configured. Please set valid STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, and STRIPE_WEBHOOK_SECRET environment variables.")
        }
        
        try {
            logger.debug("Retrieving Stripe subscription: $subscriptionId")

            val subscription = stripeClient.subscriptions().retrieve(subscriptionId)

            PaymentGatewayPort.SubscriptionResult(
                subscriptionId = subscription.id,
                status = subscription.status,
                currentPeriodStart = java.time.Instant.ofEpochSecond(subscription.currentPeriodStart).toKotlinInstant(),
                currentPeriodEnd = java.time.Instant.ofEpochSecond(subscription.currentPeriodEnd).toKotlinInstant(),
                customerId = subscription.customer,
                cancelAtPeriodEnd = subscription.cancelAtPeriodEnd ?: false
            )

        } catch (e: StripeException) {
            logger.error("Stripe error retrieving subscription: $subscriptionId", e)
            if (e.statusCode == 404) {
                null // Subscription not found
            } else {
                throw PaymentException.SubscriptionNotFound(subscriptionId)
            }
        }
    }

    override suspend fun cancelSubscription(
        subscriptionId: String,
        cancelAtPeriodEnd: Boolean
    ): PaymentGatewayPort.SubscriptionResult? = withContext(Dispatchers.IO) {
        
        // Check if Stripe is properly configured
        if (!isValidConfig || stripeClient == null) {
            throw PaymentException.ConfigurationError("Stripe is not properly configured. Please set valid STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, and STRIPE_WEBHOOK_SECRET environment variables.")
        }
        
        try {
            logger.info("Canceling Stripe subscription: $subscriptionId, at period end: $cancelAtPeriodEnd")

            val subscription: Subscription = if (cancelAtPeriodEnd) {
                // Cancel at period end
                stripeClient.subscriptions().update(
                    subscriptionId,
                    SubscriptionUpdateParams.builder()
                        .setCancelAtPeriodEnd(true)
                        .build()
                )
            } else {
                // Cancel immediately
                stripeClient.subscriptions().cancel(subscriptionId)
            }

            PaymentGatewayPort.SubscriptionResult(
                subscriptionId = subscription.id,
                status = subscription.status,
                currentPeriodStart = java.time.Instant.ofEpochSecond(subscription.currentPeriodStart).toKotlinInstant(),
                currentPeriodEnd = java.time.Instant.ofEpochSecond(subscription.currentPeriodEnd).toKotlinInstant(),
                customerId = subscription.customer,
                cancelAtPeriodEnd = subscription.cancelAtPeriodEnd ?: false
            )

        } catch (e: StripeException) {
            logger.error("Stripe error canceling subscription: $subscriptionId - ${e.message}", e)
            throw PaymentException.PaymentFailed("Failed to cancel subscription: Payment service error")
        }
    }

    override suspend fun createBillingPortalSession(
        customerId: String,
        returnUrl: String
    ): String = withContext(Dispatchers.IO) {
        
        // Check if Stripe is properly configured
        if (!isValidConfig || stripeClient == null) {
            throw PaymentException.ConfigurationError("Stripe is not properly configured. Please set valid STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, and STRIPE_WEBHOOK_SECRET environment variables.")
        }
        
        try {
            logger.info("Creating Stripe billing portal session for customer: $customerId")

            val session = stripeClient.billingPortal().sessions().create(
                PortalSessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(returnUrl)
                    .build()
            )

            session.url

        } catch (e: StripeException) {
            logger.error("Stripe error creating billing portal session: ${e.message}", e)
            
            // Handle specific configuration error
            if (e.message?.contains("No configuration provided") == true || 
                e.message?.contains("default configuration has not been created") == true) {
                throw PaymentException.ConfigurationError(
                    "Stripe Customer Portal is not configured. Please configure the customer portal settings " +
                    "in your Stripe dashboard at: https://dashboard.stripe.com/test/settings/billing/portal"
                )
            }
            
            throw PaymentException.PaymentFailed("Failed to create billing portal session: ${e.message}")
        }
    }

    override suspend fun handleWebhookEvent(
        payload: String,
        signature: String
    ): PaymentGatewayPort.WebhookEventResult = withContext(Dispatchers.IO) {
        
        // Check if Stripe is properly configured
        if (!isValidConfig) {
            throw PaymentException.ConfigurationError("Stripe is not properly configured. Please set valid STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, and STRIPE_WEBHOOK_SECRET environment variables.")
        }
        
        try {
            logger.debug("Processing Stripe webhook event")

            // Validate webhook signature
            val event = Webhook.constructEvent(payload, signature, config.webhookSecret)

            // Normalize Stripe events to standard domain events
            val normalizedEventType = normalizeEventType(event.type)
            val eventData = extractEventData(event)

            logger.info("Processed Stripe webhook: ${event.type} -> $normalizedEventType")

            // Extract objectId from eventData since deserializer is unreliable
            val objectId = when (normalizedEventType) {
                "subscription.created", "subscription.updated", "subscription.deleted" -> {
                    // For subscription events, use subscriptionId from eventData or try to get from event object
                    eventData["subscriptionId"] as? String ?: ""
                }
                else -> ""
            }
            
            val objectType = when (normalizedEventType) {
                "subscription.created", "subscription.updated", "subscription.deleted" -> "subscription"
                "payment.succeeded", "payment.failed" -> "payment"
                else -> "event"
            }
            
            PaymentGatewayPort.WebhookEventResult(
                eventType = normalizedEventType,
                objectId = objectId,
                objectType = objectType,
                data = eventData
            )

        } catch (e: Exception) {
            logger.error("Error processing Stripe webhook", e)
            throw ValidationException.InvalidFormat("webhook", "invalid signature or payload")
        }
    }

    /**
     * Normalizes Stripe event types to standard domain events.
     * This ensures Use Cases remain provider-agnostic.
     */
    private fun normalizeEventType(stripeEventType: String): String {
        return when (stripeEventType) {
            "checkout.session.completed" -> "subscription.created"  // Primary event for subscription creation
            "customer.subscription.created" -> "subscription.created"
            "customer.subscription.updated" -> "subscription.updated"
            "customer.subscription.deleted" -> "subscription.deleted"
            "invoice.paid" -> "subscription.created"  // Fallback for subscription creation
            "invoice.payment_succeeded" -> "payment.succeeded"
            "invoice.payment_failed" -> "payment.failed"
            else -> stripeEventType // Pass through unknown events
        }
    }

    /**
     * Extracts relevant data from Stripe events for domain processing.
     * Uses safe property access to handle different Stripe API versions.
     */
    private fun extractEventData(event: Event): Map<String, Any> {
        val eventData = mutableMapOf<String, Any>()

        // Structured logging
        logger.debug("STRIPE_EVENT_EXTRACT_START: Extracting data for event type [eventType=${event.type}]")
        
        // Use direct event data access instead of deserializer
        val eventDataObj = event.data?.`object`
        if (eventDataObj == null) {
            logger.warn("STRIPE_EVENT_NO_DATA: No event data object found [eventType=${event.type}]")
            return eventData
        }
        
        logger.debug("STRIPE_EVENT_DATA_TYPE: Event data object found [eventType=${event.type}, dataType=${eventDataObj.javaClass.simpleName}]")
        
        // Cast to specific Stripe object types
        val stripeObject = eventDataObj as? StripeObject
        if (stripeObject == null) {
            logger.warn("STRIPE_EVENT_NOT_STRIPE_OBJECT: Event data object is not a StripeObject [eventType=${event.type}, actualType=${eventDataObj.javaClass.simpleName}]")
            return eventData
        }
        
        logger.debug("STRIPE_OBJECT_TYPE: Stripe object validated [eventType=${event.type}, stripeObjectType=${stripeObject.javaClass.simpleName}]")

        when (event.type) {
            "checkout.session.completed" -> {
                val session = stripeObject as Session
                
                logger.info("STRIPE_CHECKOUT_SESSION: Processing checkout session [sessionId=${session.id}, subscriptionId=${session.subscription}, customerId=${session.customer}]")
                
                // Extract metadata directly from checkout session
                val metadata = session.metadata ?: emptyMap()
                logger.info("STRIPE_CHECKOUT_METADATA: Extracted metadata [userId=${metadata["userId"]}, planId=${metadata["planId"]}, billingCycle=${metadata["billingCycle"]}]")
                
                eventData["userId"] = metadata["userId"] ?: ""
                eventData["planId"] = metadata["planId"] ?: ""
                eventData["billingCycle"] = metadata["billingCycle"] ?: "monthly"
                eventData["status"] = "active"  // Checkout completed means subscription is active
                eventData["customerId"] = session.customer ?: ""
                eventData["subscriptionId"] = session.subscription ?: ""
                
                // Use current timestamp as fallback for period info
                eventData["currentPeriodStart"] = System.currentTimeMillis() / 1000
                eventData["currentPeriodEnd"] = (System.currentTimeMillis() / 1000) + (30 * 24 * 60 * 60) // +30 days
                eventData["cancelAtPeriodEnd"] = false
                
                logger.info("STRIPE_CHECKOUT_DATA_EXTRACTED: Checkout session data extracted successfully [userId=${eventData["userId"]}, subscriptionId=${eventData["subscriptionId"]}]")
            }
            "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" -> {
                val subscription = stripeObject as Subscription
                
                // Debug logging
                println("DEBUG: Stripe subscription metadata: ${subscription.metadata}")
                println("DEBUG: Stripe subscription ID: ${subscription.id}")
                println("DEBUG: Stripe subscription status: ${subscription.status}")
                
                eventData["userId"] = subscription.metadata["userId"] ?: ""
                eventData["planId"] = subscription.metadata["planId"] ?: ""
                eventData["billingCycle"] = subscription.metadata["billingCycle"] ?: "monthly"
                eventData["status"] = subscription.status
                eventData["customerId"] = subscription.customer
                eventData["subscriptionId"] = subscription.id  // Add subscription ID for consistent objectId extraction
                eventData["currentPeriodStart"] = subscription.currentPeriodStart
                eventData["currentPeriodEnd"] = subscription.currentPeriodEnd
                eventData["cancelAtPeriodEnd"] = subscription.cancelAtPeriodEnd ?: false
                
                // Debug logging the extracted data
                println("DEBUG: Extracted eventData: $eventData")
            }
            "invoice.paid" -> {
                val invoice = stripeObject as Invoice
                
                // Debug logging
                println("DEBUG: Invoice paid - subscription ID: ${invoice.subscription}")
                println("DEBUG: Invoice subscription_details: ${invoice.subscriptionDetails}")
                
                // Extract metadata from invoice subscription_details
                val subscriptionDetails = invoice.subscriptionDetails
                val metadata = subscriptionDetails?.metadata ?: emptyMap()
                
                println("DEBUG: Invoice subscription metadata: $metadata")
                
                eventData["userId"] = metadata["userId"] ?: ""
                eventData["planId"] = metadata["planId"] ?: ""
                eventData["billingCycle"] = metadata["billingCycle"] ?: "monthly"
                eventData["status"] = "active"  // Invoice paid means subscription is active
                eventData["customerId"] = invoice.customer ?: ""
                eventData["subscriptionId"] = invoice.subscription ?: ""
                
                // For invoice.paid, we need to fetch the subscription to get period info
                // We'll use current timestamp as fallback
                eventData["currentPeriodStart"] = System.currentTimeMillis() / 1000
                eventData["currentPeriodEnd"] = (System.currentTimeMillis() / 1000) + (30 * 24 * 60 * 60) // +30 days
                eventData["cancelAtPeriodEnd"] = false
                
                println("DEBUG: Extracted invoice eventData: $eventData")
            }
            "invoice.payment_succeeded", "invoice.payment_failed" -> {
                val invoice = stripeObject as Invoice
                eventData["subscriptionId"] = invoice.subscription ?: ""
                eventData["customerId"] = invoice.customer ?: ""
                eventData["amountPaid"] = invoice.amountPaid ?: 0
                eventData["currency"] = invoice.currency ?: "usd"
            }
        }

        return eventData
    }

    /**
     * Maps plan and billing cycle to Stripe price ID using database lookup.
     * This ensures synchronization between database plans and Stripe products.
     */
    private suspend fun getPriceIdForPlan(planId: String, billingCycle: BillingCycle): String {
        logger.debug("Looking up Stripe price ID for plan: $planId, billing cycle: $billingCycle")
        
        // Get plan from database
        val plan = planRepository.findById(planId)
            ?: throw ValidationException.Custom("Plan not found: $planId", "planId")
        
        // Determine which Stripe price ID to use based on billing cycle
        val stripePriceId = when (billingCycle) {
            BillingCycle.MONTHLY -> plan.stripePriceIdMonthly
            BillingCycle.ANNUAL -> plan.stripePriceIdAnnual
        }
        
        // Validate that Stripe price ID exists
        if (stripePriceId.isNullOrBlank()) {
            throw ValidationException.Custom(
                "No Stripe price ID configured for plan: ${plan.name} (${billingCycle.name.lowercase()})", 
                "planId"
            )
        }
        
        logger.debug("Found Stripe price ID: $stripePriceId for plan: $planId")
        return stripePriceId
    }
}
