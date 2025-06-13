package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.ports.output.PaymentGatewayPort
import dev.screenshotapi.core.usecases.common.UseCase
import org.slf4j.LoggerFactory

/**
 * Request model for creating billing portal session
 */
data class CreateBillingPortalSessionRequest(
    val userId: String,
    val returnUrl: String
)

/**
 * Response model for billing portal session
 */
data class CreateBillingPortalSessionResponse(
    val portalUrl: String
)

/**
 * Use case for creating Stripe billing portal session.
 * Allows users to manage their subscriptions, payment methods, and billing history.
 */
class CreateBillingPortalSessionUseCase(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentGatewayPort: PaymentGatewayPort
) : UseCase<CreateBillingPortalSessionRequest, CreateBillingPortalSessionResponse> {

    private val logger = LoggerFactory.getLogger(CreateBillingPortalSessionUseCase::class.java)

    override suspend operator fun invoke(request: CreateBillingPortalSessionRequest): CreateBillingPortalSessionResponse {
        logger.info("BILLING_PORTAL_USECASE_START: Creating billing portal session [userId=${request.userId}]")
        
        // Verify user exists
        val user = userRepository.findById(request.userId)
            ?: run {
                logger.error("BILLING_PORTAL_USER_NOT_FOUND: User not found [userId=${request.userId}]")
                throw ResourceNotFoundException("User not found with id: ${request.userId}")
            }
        
        logger.info("BILLING_PORTAL_USER_FOUND: User found [userId=${request.userId}, userEmail=${user.email}]")
        
        // Find user's active subscription to get Stripe customer ID
        val subscription = subscriptionRepository.findActiveByUserId(request.userId)
        val customerId = subscription?.stripeCustomerId
            ?: run {
                logger.error("BILLING_PORTAL_NO_SUBSCRIPTION: No active subscription found [userId=${request.userId}]")
                throw ResourceNotFoundException("No active subscription found for user: ${request.userId}")
            }
        
        logger.info("BILLING_PORTAL_SUBSCRIPTION_FOUND: Active subscription found [userId=${request.userId}, customerId=$customerId, subscriptionId=${subscription.id}]")
        
        // Create billing portal session via payment gateway
        val portalUrl = try {
            paymentGatewayPort.createBillingPortalSession(
                customerId = customerId,
                returnUrl = request.returnUrl
            )
        } catch (e: Exception) {
            logger.error("BILLING_PORTAL_GATEWAY_ERROR: Failed to create portal session [userId=${request.userId}, customerId=$customerId, error=${e.message}]", e)
            throw e
        }
        
        logger.info("BILLING_PORTAL_SUCCESS: Billing portal session created successfully [userId=${request.userId}, portalUrl=$portalUrl]")
        
        return CreateBillingPortalSessionResponse(
            portalUrl = portalUrl
        )
    }
}