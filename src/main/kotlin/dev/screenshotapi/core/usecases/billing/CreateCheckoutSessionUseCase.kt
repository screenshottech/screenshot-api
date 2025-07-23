package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.entities.BillingCycle
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.ports.output.PaymentGatewayPort
import dev.screenshotapi.core.usecases.common.UseCase

/**
 * Request model for creating checkout session
 */
data class CreateCheckoutSessionRequest(
    val userId: String,
    val planId: String,
    val billingCycle: BillingCycle,
    val successUrl: String,
    val cancelUrl: String
)

/**
 * Response model for checkout session
 */
data class CreateCheckoutSessionResponse(
    val sessionId: String,
    val checkoutUrl: String,
    val customerId: String?
)

/**
 * Use case for creating a payment checkout session.
 * Orchestrates validation and payment gateway interaction.
 */
class CreateCheckoutSessionUseCase(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val paymentGatewayPort: PaymentGatewayPort
) : UseCase<CreateCheckoutSessionRequest, CreateCheckoutSessionResponse> {

    override suspend operator fun invoke(request: CreateCheckoutSessionRequest): CreateCheckoutSessionResponse {
        // Validate user exists
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User not found with id: ${request.userId}")
        
        // Validate plan exists and is active
        val plan = planRepository.findById(request.planId)
            ?: throw ResourceNotFoundException("Plan not found with id: ${request.planId}")
        
        if (!plan.isActive) {
            throw ValidationException.InvalidState("Plan", "inactive", "active")
        }
        
        // Validate URLs
        if (request.successUrl.isBlank()) {
            throw ValidationException.Required("successUrl")
        }
        if (request.cancelUrl.isBlank()) {
            throw ValidationException.Required("cancelUrl")
        }
        
        // Validate pricing exists for billing cycle
        val price = request.billingCycle.getPriceFromPlan(plan)
        if (price <= 0 && plan.name.lowercase() != "free") {
            throw ValidationException.Custom("Plan does not support ${request.billingCycle.name.lowercase()} billing", "billingCycle")
        }
        
        // Create checkout session via payment gateway
        val result = paymentGatewayPort.createCheckoutSession(
            userId = user.id,
            customerEmail = user.email,
            planId = plan.id,
            billingCycle = request.billingCycle,
            successUrl = request.successUrl,
            cancelUrl = request.cancelUrl
        )
        
        return CreateCheckoutSessionResponse(
            sessionId = result.sessionId,
            checkoutUrl = result.url,
            customerId = result.customerId
        )
    }
}