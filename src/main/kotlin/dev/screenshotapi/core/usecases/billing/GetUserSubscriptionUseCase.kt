package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.entities.Plan
import dev.screenshotapi.core.domain.entities.Subscription
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase

/**
 * Request model for getting user subscription
 */
data class GetUserSubscriptionRequest(
    val userId: String
)

/**
 * Response model for user subscription with plan details
 */
data class GetUserSubscriptionResponse(
    val subscription: Subscription?,
    val plan: Plan,
    val isActive: Boolean,
    val creditsForPeriod: Int,
    val priceForPeriod: Int
)

/**
 * Use case for retrieving user's subscription information with plan details.
 * Combines subscription and plan data for complete billing information.
 */
class GetUserSubscriptionUseCase(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val planRepository: PlanRepository
) : UseCase<GetUserSubscriptionRequest, GetUserSubscriptionResponse> {

    override suspend operator fun invoke(request: GetUserSubscriptionRequest): GetUserSubscriptionResponse {
        // Validate user exists
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User not found with id: ${request.userId}")
        
        // Get user's active subscription (if any)
        val subscription = subscriptionRepository.findActiveByUserId(request.userId)
        
        // Get user's current plan (from User entity or subscription)
        val planId = subscription?.planId ?: user.planId
        val plan = planRepository.findById(planId)
            ?: throw ResourceNotFoundException("Plan not found with id: $planId")
        
        // Calculate subscription details
        val isActive = subscription?.isActive() ?: false
        val creditsForPeriod = subscription?.getTotalCreditsFromPlan(plan) ?: plan.creditsPerMonth
        val priceForPeriod = subscription?.getPriceFromPlan(plan) ?: plan.priceCentsMonthly
        
        return GetUserSubscriptionResponse(
            subscription = subscription,
            plan = plan,
            isActive = isActive,
            creditsForPeriod = creditsForPeriod,
            priceForPeriod = priceForPeriod
        )
    }
}