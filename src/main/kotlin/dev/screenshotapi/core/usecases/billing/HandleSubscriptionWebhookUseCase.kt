package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.entities.BillingCycle
import dev.screenshotapi.core.domain.entities.Subscription
import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.ports.output.PaymentGatewayPort
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

/**
 * Request model for handling webhook events
 */
data class HandleSubscriptionWebhookRequest(
    val payload: String,
    val signature: String
)

/**
 * Response model for webhook handling
 */
data class HandleSubscriptionWebhookResponse(
    val eventType: String,
    val processed: Boolean,
    val subscriptionId: String? = null,
    val message: String? = null
)

/**
 * Use case for handling subscription webhook events from payment gateways.
 * Uses standard event types (provider-agnostic) that are normalized by PaymentGatewayPort.
 */
class HandleSubscriptionWebhookUseCase(
    private val paymentGatewayPort: PaymentGatewayPort,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val provisionSubscriptionCreditsUseCase: ProvisionSubscriptionCreditsUseCase
) : UseCase<HandleSubscriptionWebhookRequest, HandleSubscriptionWebhookResponse> {

    private val logger = LoggerFactory.getLogger(HandleSubscriptionWebhookUseCase::class.java)

    override suspend operator fun invoke(request: HandleSubscriptionWebhookRequest): HandleSubscriptionWebhookResponse {
        val webhookStartTime = System.currentTimeMillis()
        logger.info("WEBHOOK_USECASE_START: Processing webhook event [payloadSize=${request.payload.length}, timestamp=$webhookStartTime]")
        
        // Parse webhook event via payment gateway (provider normalizes event types)
        val webhookEvent = try {
            paymentGatewayPort.handleWebhookEvent(request.payload, request.signature)
        } catch (e: Exception) {
            logger.error("WEBHOOK_SIGNATURE_ERROR: Invalid webhook signature or payload [error=${e.message}, payloadPreview=${request.payload.take(100)}]", e)
            throw ValidationException("Invalid webhook signature or payload", "webhook")
        }
        
        logger.info("WEBHOOK_EVENT_PARSED: Event parsed successfully [eventType=${webhookEvent.eventType}, objectId=${webhookEvent.objectId}, objectType=${webhookEvent.objectType}, dataKeys=${webhookEvent.data.keys.joinToString()}]")
        
        // Handle standard event types (normalized by PaymentGatewayPort)
        return try {
            when (webhookEvent.eventType) {
                "subscription.created" -> {
                    logger.info("WEBHOOK_HANDLING_SUBSCRIPTION_CREATED: Processing subscription creation [objectId=${webhookEvent.objectId}]")
                    handleSubscriptionCreated(webhookEvent)
                }
                "subscription.updated" -> {
                    logger.info("WEBHOOK_HANDLING_SUBSCRIPTION_UPDATED: Processing subscription update [objectId=${webhookEvent.objectId}]")
                    handleSubscriptionUpdated(webhookEvent)
                }
                "subscription.deleted" -> {
                    logger.info("WEBHOOK_HANDLING_SUBSCRIPTION_DELETED: Processing subscription deletion [objectId=${webhookEvent.objectId}]")
                    handleSubscriptionDeleted(webhookEvent)
                }
                "payment.succeeded" -> {
                    logger.info("WEBHOOK_HANDLING_PAYMENT_SUCCEEDED: Processing payment success [objectId=${webhookEvent.objectId}]")
                    handlePaymentSucceeded(webhookEvent)
                }
                "payment.failed" -> {
                    logger.info("WEBHOOK_HANDLING_PAYMENT_FAILED: Processing payment failure [objectId=${webhookEvent.objectId}]")
                    handlePaymentFailed(webhookEvent)
                }
                else -> {
                    logger.warn("WEBHOOK_UNHANDLED_EVENT: Event type not handled [eventType=${webhookEvent.eventType}]")
                    HandleSubscriptionWebhookResponse(
                        eventType = webhookEvent.eventType,
                        processed = false,
                        message = "Event type not handled"
                    )
                }
            }
        } catch (e: Exception) {
            val processingDuration = System.currentTimeMillis() - webhookStartTime
            logger.error("WEBHOOK_PROCESSING_ERROR: Failed to process webhook event [eventType=${webhookEvent.eventType}, objectId=${webhookEvent.objectId}, error=${e.javaClass.simpleName}, message=${e.message}, processingDuration=${processingDuration}ms]", e)
            throw e
        }
    }
    
    private suspend fun handleSubscriptionCreated(event: PaymentGatewayPort.WebhookEventResult): HandleSubscriptionWebhookResponse {
        val subscriptionData = event.data
        val providerSubscriptionId = event.objectId
        
        logger.info("SUBSCRIPTION_CREATE_START: Starting subscription creation [providerSubscriptionId=$providerSubscriptionId, dataKeys=${subscriptionData.keys}]")
        
        // For invoice.paid events, use subscriptionId from data instead of objectId
        val actualSubscriptionId = if (subscriptionData.containsKey("subscriptionId")) {
            subscriptionData["subscriptionId"] as? String ?: providerSubscriptionId
        } else {
            providerSubscriptionId
        }
        
        logger.info("SUBSCRIPTION_CREATE_ID_RESOLVED: Resolved subscription ID [actualSubscriptionId=$actualSubscriptionId, fromData=${subscriptionData.containsKey("subscriptionId")}]")
        
        val userId = subscriptionData["userId"] as? String
            ?: run {
                logger.error("SUBSCRIPTION_CREATE_MISSING_USER: Missing userId in subscription data [availableKeys=${subscriptionData.keys}]")
                throw ValidationException("Missing userId in subscription data. Available keys: ${subscriptionData.keys}", "userId")
            }
        
        logger.info("SUBSCRIPTION_CREATE_USER_CHECK: Checking if user exists [userId=$userId]")
        
        // Verify user exists
        val user = userRepository.findById(userId)
            ?: run {
                logger.error("SUBSCRIPTION_CREATE_USER_NOT_FOUND: User not found [userId=$userId]")
                throw ResourceNotFoundException("User not found with id: $userId")
            }
        
        logger.info("SUBSCRIPTION_CREATE_USER_FOUND: User found [userId=$userId, userEmail=${user.email}]")
        
        // Check if subscription already exists to avoid duplicates (idempotency check)
        val existingSubscription = subscriptionRepository.findByStripeSubscriptionId(actualSubscriptionId)
        if (existingSubscription != null) {
            logger.info("SUBSCRIPTION_CREATE_IDEMPOTENT: Subscription already exists, returning existing [actualSubscriptionId=$actualSubscriptionId, existingId=${existingSubscription.id}]")
            
            // Still try to provision credits in case this was missed previously
            try {
                logger.info("SUBSCRIPTION_PROVISION_CREDITS_RETRY: Attempting credit provisioning for existing subscription [subscriptionId=${existingSubscription.id}]")
                
                val provisionRequest = ProvisionSubscriptionCreditsRequest(
                    subscriptionId = existingSubscription.id,
                    reason = "subscription_created_retry"
                )
                
                val provisionResponse = provisionSubscriptionCreditsUseCase(provisionRequest)
                
                if (provisionResponse.processed) {
                    logger.info("SUBSCRIPTION_PROVISION_CREDITS_RETRY_SUCCESS: Credits provisioned for existing subscription [subscriptionId=${existingSubscription.id}]")
                } else {
                    logger.debug("SUBSCRIPTION_PROVISION_CREDITS_RETRY_SKIPPED: Credit provisioning not needed [subscriptionId=${existingSubscription.id}]")
                }
            } catch (e: Exception) {
                logger.warn("SUBSCRIPTION_PROVISION_CREDITS_RETRY_ERROR: Failed to provision credits for existing subscription [subscriptionId=${existingSubscription.id}, error=${e.message}]", e)
            }
            
            return HandleSubscriptionWebhookResponse(
                eventType = event.eventType,
                processed = true,
                subscriptionId = existingSubscription.id,
                message = "Subscription processed idempotently (already exists)"
            )
        }
        
        // Extract subscription details
        val planId = subscriptionData["planId"] as? String ?: user.planId
        val billingCycle = BillingCycle.fromString(subscriptionData["billingCycle"] as? String)
        val status = SubscriptionStatus.fromString(subscriptionData["status"] as? String ?: "active")
        val customerId = subscriptionData["customerId"] as? String
        
        logger.info("SUBSCRIPTION_CREATE_DETAILS: Extracted subscription details [planId=$planId, billingCycle=$billingCycle, status=$status, customerId=$customerId]")
        
        // Create subscription record
        val subscription = Subscription(
            id = "sub_${System.currentTimeMillis()}",
            userId = userId,
            planId = planId,
            billingCycle = billingCycle,
            status = status,
            stripeSubscriptionId = actualSubscriptionId,
            stripeCustomerId = customerId,
            currentPeriodStart = parseInstant(subscriptionData["currentPeriodStart"]),
            currentPeriodEnd = parseInstant(subscriptionData["currentPeriodEnd"]),
            createdAt = Clock.System.now()
        )
        
        logger.info("SUBSCRIPTION_CREATE_SAVING: Saving subscription to database [subscriptionId=${subscription.id}, stripeSubscriptionId=$actualSubscriptionId]")
        
        try {
            subscriptionRepository.save(subscription)
            logger.info("SUBSCRIPTION_CREATE_SUCCESS: Subscription saved successfully [subscriptionId=${subscription.id}, userId=$userId, planId=$planId]")
        } catch (e: Exception) {
            logger.error("SUBSCRIPTION_CREATE_DB_ERROR: Failed to save subscription [subscriptionId=${subscription.id}, error=${e.message}]", e)
            throw e
        }
        
        // Provision credits for the new subscription
        try {
            logger.info("SUBSCRIPTION_PROVISION_CREDITS_START: Starting credit provisioning [subscriptionId=${subscription.id}]")
            
            val provisionRequest = ProvisionSubscriptionCreditsRequest(
                subscriptionId = subscription.id,
                reason = "subscription_created"
            )
            
            val provisionResponse = provisionSubscriptionCreditsUseCase(provisionRequest)
            
            if (provisionResponse.processed) {
                logger.info("SUBSCRIPTION_PROVISION_CREDITS_SUCCESS: Credits provisioned successfully [subscriptionId=${subscription.id}, creditsProvisioned=${provisionResponse.creditsProvisioned}, newBalance=${provisionResponse.newCreditBalance}]")
            } else {
                logger.warn("SUBSCRIPTION_PROVISION_CREDITS_SKIPPED: Credit provisioning skipped [subscriptionId=${subscription.id}, reason=${provisionResponse.message}]")
            }
            
        } catch (e: Exception) {
            // Don't fail the webhook if credit provisioning fails - we can retry later
            logger.error("SUBSCRIPTION_PROVISION_CREDITS_ERROR: Failed to provision credits [subscriptionId=${subscription.id}, error=${e.message}]", e)
            // Consider adding the subscription to a retry queue here
        }
        
        return HandleSubscriptionWebhookResponse(
            eventType = event.eventType,
            processed = true,
            subscriptionId = subscription.id,
            message = "Subscription created successfully"
        )
    }
    
    private suspend fun handleSubscriptionUpdated(event: PaymentGatewayPort.WebhookEventResult): HandleSubscriptionWebhookResponse {
        val providerSubscriptionId = event.objectId
        val subscriptionData = event.data
        
        logger.debug("Processing subscription update for provider ID: $providerSubscriptionId")
        
        // Find existing subscription
        val existingSubscription = subscriptionRepository.findByStripeSubscriptionId(providerSubscriptionId)
            ?: throw ResourceNotFoundException("Subscription not found with provider ID: $providerSubscriptionId")
        
        // Update subscription with new data
        val updatedSubscription = existingSubscription.copy(
            status = SubscriptionStatus.fromString(subscriptionData["status"] as? String ?: "active"),
            currentPeriodStart = parseInstant(subscriptionData["currentPeriodStart"]),
            currentPeriodEnd = parseInstant(subscriptionData["currentPeriodEnd"]),
            cancelAtPeriodEnd = subscriptionData["cancelAtPeriodEnd"] as? Boolean ?: false,
            updatedAt = Clock.System.now()
        )
        
        subscriptionRepository.update(updatedSubscription)
        
        return HandleSubscriptionWebhookResponse(
            eventType = event.eventType,
            processed = true,
            subscriptionId = updatedSubscription.id,
            message = "Subscription updated successfully"
        )
    }
    
    private suspend fun handleSubscriptionDeleted(event: PaymentGatewayPort.WebhookEventResult): HandleSubscriptionWebhookResponse {
        val providerSubscriptionId = event.objectId
        
        // Find and update subscription to canceled status
        val existingSubscription = subscriptionRepository.findByStripeSubscriptionId(providerSubscriptionId)
            ?: throw ResourceNotFoundException("Subscription not found with provider ID: $providerSubscriptionId")
        
        val canceledSubscription = existingSubscription.copy(
            status = SubscriptionStatus.CANCELED,
            updatedAt = Clock.System.now()
        )
        
        subscriptionRepository.update(canceledSubscription)
        
        return HandleSubscriptionWebhookResponse(
            eventType = event.eventType,
            processed = true,
            subscriptionId = canceledSubscription.id,
            message = "Subscription canceled successfully"
        )
    }
    
    private suspend fun handlePaymentSucceeded(event: PaymentGatewayPort.WebhookEventResult): HandleSubscriptionWebhookResponse {
        val subscriptionData = event.data
        val providerSubscriptionId = subscriptionData["subscriptionId"] as? String
        
        if (providerSubscriptionId != null) {
            val subscription = subscriptionRepository.findByStripeSubscriptionId(providerSubscriptionId)
            if (subscription != null && subscription.status != SubscriptionStatus.ACTIVE) {
                val updatedSubscription = subscription.copy(
                    status = SubscriptionStatus.ACTIVE,
                    updatedAt = Clock.System.now()
                )
                subscriptionRepository.update(updatedSubscription)
            }
        }
        
        return HandleSubscriptionWebhookResponse(
            eventType = event.eventType,
            processed = true,
            message = "Payment succeeded, subscription activated"
        )
    }
    
    private suspend fun handlePaymentFailed(event: PaymentGatewayPort.WebhookEventResult): HandleSubscriptionWebhookResponse {
        val subscriptionData = event.data
        val providerSubscriptionId = subscriptionData["subscriptionId"] as? String
        
        if (providerSubscriptionId != null) {
            val subscription = subscriptionRepository.findByStripeSubscriptionId(providerSubscriptionId)
            if (subscription != null) {
                val updatedSubscription = subscription.copy(
                    status = SubscriptionStatus.PAST_DUE,
                    updatedAt = Clock.System.now()
                )
                subscriptionRepository.update(updatedSubscription)
            }
        }
        
        return HandleSubscriptionWebhookResponse(
            eventType = event.eventType,
            processed = true,
            message = "Payment failed, subscription marked as past due"
        )
    }
    
    private fun parseInstant(value: Any?): Instant {
        return when (value) {
            is Long -> Instant.fromEpochSeconds(value)
            is String -> Instant.parse(value)
            else -> Clock.System.now()
        }
    }
}