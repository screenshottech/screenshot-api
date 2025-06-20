package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.usecases.billing.*
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import dev.screenshotapi.infrastructure.auth.requireUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * REST endpoints for billing and subscription management.
 * Following the same pattern as AuthController.
 */
class BillingController : KoinComponent {
    private val logger = LoggerFactory.getLogger(BillingController::class.java)
    private val getAvailablePlansUseCase: GetAvailablePlansUseCase by inject()
    private val createCheckoutSessionUseCase: CreateCheckoutSessionUseCase by inject()
    private val getUserSubscriptionUseCase: GetUserSubscriptionUseCase by inject()
    private val handleSubscriptionWebhookUseCase: HandleSubscriptionWebhookUseCase by inject()
    private val createBillingPortalSessionUseCase: CreateBillingPortalSessionUseCase by inject()

    /**
     * GET /billing/plans
     * Public endpoint to get available subscription plans
     */
    suspend fun getPlans(call: ApplicationCall) {
        val response = getAvailablePlansUseCase(GetAvailablePlansRequest(includeInactive = false))
        
        val plansDto = AvailablePlansResponseDto(
            plans = response.plans.map { it.toDto() }
        )
        
        call.respond(HttpStatusCode.OK, plansDto)
    }

    /**
     * POST /billing/checkout
     * Authenticated endpoint to create a checkout session
     */
    suspend fun createCheckout(call: ApplicationCall) {
        val request = call.receive<CreateCheckoutSessionRequestDto>()
        
        // Get user ID from JWT authentication (billing endpoints use jwt-auth)
        val userId = call.requireUserId()
        
        // Convert DTO to domain model
        val billingCycle = request.billingCycle.toBillingCycle()
        
        // Execute use case
        val useCaseRequest = CreateCheckoutSessionRequest(
            userId = userId,
            planId = request.planId,
            billingCycle = billingCycle,
            successUrl = request.successUrl,
            cancelUrl = request.cancelUrl
        )
        
        val response = createCheckoutSessionUseCase(useCaseRequest)
        
        // Convert response to DTO
        val responseDto = CreateCheckoutSessionResponseDto(
            sessionId = response.sessionId,
            url = response.checkoutUrl,
            customerId = response.customerId
        )
        
        call.respond(HttpStatusCode.Created, responseDto)
    }

    /**
     * GET /billing/subscription
     * Authenticated endpoint to get user's current subscription
     */
    suspend fun getSubscription(call: ApplicationCall) {
        // Get user ID from JWT authentication (billing endpoints use jwt-auth)
        val userId = call.requireUserId()
        
        // Execute use case
        val request = GetUserSubscriptionRequest(userId)
        val response = getUserSubscriptionUseCase(request)
        
        // Convert response to DTO
        val responseDto = UserSubscriptionResponseDto(
            subscriptionId = response.subscription?.id,
            status = response.subscription?.status?.toExternalString() ?: "none",
            planId = response.subscription?.planId,
            billingCycle = response.subscription?.billingCycle?.toExternalString(),
            currentPeriodStart = response.subscription?.currentPeriodStart?.toString(),
            currentPeriodEnd = response.subscription?.currentPeriodEnd?.toString(),
            cancelAtPeriodEnd = response.subscription?.cancelAtPeriodEnd ?: false
        )
        
        call.respond(HttpStatusCode.OK, responseDto)
    }

    /**
     * POST /billing/portal
     * Authenticated endpoint to create Stripe billing portal session
     */
    suspend fun createBillingPortal(call: ApplicationCall) {
        // Get user ID from JWT authentication (billing endpoints use jwt-auth)
        val userId = call.requireUserId()
        
        logger.info("BILLING_PORTAL_REQUEST: Creating billing portal session [userId=$userId]")
        
        try {
            // Execute use case to create billing portal session
            val request = CreateBillingPortalSessionRequest(
                userId = userId,
                returnUrl = "http://localhost:3000/billing" // TODO: Make this configurable
            )
            
            val response = createBillingPortalSessionUseCase(request)
            
            logger.info("BILLING_PORTAL_SUCCESS: Billing portal session created [userId=$userId, portalUrl=${response.portalUrl}]")
            
            // Convert response to DTO
            val responseDto = BillingPortalResponseDto(
                url = response.portalUrl,
                created = true
            )
            
            call.respond(HttpStatusCode.OK, responseDto)
            
        } catch (e: dev.screenshotapi.core.domain.exceptions.PaymentException.ConfigurationError) {
            logger.error("BILLING_PORTAL_CONFIG_ERROR: Stripe portal not configured [userId=$userId, error=${e.message}]", e)
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponseDto(
                code = "BILLING_PORTAL_NOT_CONFIGURED",
                message = "Stripe Customer Portal not configured: ${e.message}",
                details = mapOf("action" to "Please configure the customer portal in Stripe dashboard")
            ))
        } catch (e: dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException) {
            logger.error("BILLING_PORTAL_NOT_FOUND: Resource not found [userId=$userId, error=${e.message}]", e)
            call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("subscription", userId))
        } catch (e: Exception) {
            logger.error("BILLING_PORTAL_ERROR: Failed to create billing portal session [userId=$userId, error=${e.message}]", e)
            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(
                code = "BILLING_PORTAL_ERROR",
                message = "Failed to create billing portal session: ${e.message}"
            ))
        }
    }

    /**
     * POST /billing/webhook
     * Public endpoint for Stripe webhooks
     */
    suspend fun handleWebhook(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        val requestId = call.request.headers["X-Request-ID"] ?: "webhook-${System.currentTimeMillis()}"
        
        try {
            // Get raw body and signature header
            val payload = call.receiveText()
            val signature = call.request.headers["stripe-signature"]
                ?: throw dev.screenshotapi.core.domain.exceptions.ValidationException(
                    "Missing Stripe signature header", 
                    "stripe-signature"
                )
            
            logger.info("WEBHOOK_START: Processing webhook request [requestId=$requestId, payloadSize=${payload.length}, hasSignature=${signature.isNotEmpty()}]")
            
            // Execute use case
            val request = HandleSubscriptionWebhookRequest(
                payload = payload,
                signature = signature
            )
            
            val response = handleSubscriptionWebhookUseCase(request)
            val duration = System.currentTimeMillis() - startTime
            
            // Convert response to DTO
            val responseDto = WebhookResponseDto(
                received = true,
                processed = response.processed,
                eventType = response.eventType
            )
            
            logger.info("WEBHOOK_SUCCESS: Webhook processed successfully [requestId=$requestId, eventType=${response.eventType}, processed=${response.processed}, duration=${duration}ms, subscriptionId=${response.subscriptionId}]")
            
            call.respond(HttpStatusCode.OK, responseDto)
            
        } catch (e: dev.screenshotapi.core.domain.exceptions.ValidationException) {
            val duration = System.currentTimeMillis() - startTime
            logger.warn("WEBHOOK_VALIDATION_ERROR: Webhook validation failed [requestId=$requestId, duration=${duration}ms, error=${e.message}, field=${e.field}]")
            call.respond(HttpStatusCode.BadRequest, mapOf("received" to false, "error" to e.message))
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("WEBHOOK_ERROR: Webhook processing failed [requestId=$requestId, duration=${duration}ms, error=${e.javaClass.simpleName}, message=${e.message}]", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("received" to false, "error" to "Processing failed"))
        }
    }
}