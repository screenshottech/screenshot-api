package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.billing.GetUserSubscriptionUseCase
import dev.screenshotapi.core.usecases.billing.GetUserSubscriptionRequest
import dev.screenshotapi.core.usecases.auth.GetUserUsageUseCase
import dev.screenshotapi.core.usecases.auth.GetUserUsageRequest

/**
 * Use case for retrieving detailed subscription information by subscription ID.
 * 
 * This use case follows clean architecture principles using composition:
 * - Reuses existing GetUserSubscriptionUseCase (no logic duplication)
 * - Minimal orchestration: findById + delegate to existing use case
 * - Single responsibility: converts subscriptionId lookup to userId lookup
 * 
 * Architecture pattern: Composition over inheritance
 * - Primary use case: GetUserSubscriptionUseCase (handles business logic)
 * - This use case: Simple adapter/orchestrator
 */
class GetSubscriptionDetailsUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val getUserSubscriptionUseCase: GetUserSubscriptionUseCase,
    private val getUserUsageUseCase: GetUserUsageUseCase
) {
    suspend operator fun invoke(request: GetSubscriptionDetailsRequest): GetSubscriptionDetailsResponse {
        // 1. Find subscription by ID (simple repository call)
        val subscription = subscriptionRepository.findById(request.subscriptionId)
            ?: throw ResourceNotFoundException("Subscription not found with id: ${request.subscriptionId}")

        // 2. Get user to ensure it exists
        val user = userRepository.findById(subscription.userId)
            ?: throw ResourceNotFoundException("User not found with id: ${subscription.userId}")

        // 3. Delegate to existing use cases (pure composition - no logic duplication)
        val userSubscriptionResponse = getUserSubscriptionUseCase(
            GetUserSubscriptionRequest(userId = subscription.userId)
        )
        
        // 4. Get real usage data for accurate statistics
        val userUsageResponse = getUserUsageUseCase(
            GetUserUsageRequest(userId = subscription.userId)
        )

        // 5. Return composed response using existing structures with real data
        return GetSubscriptionDetailsResponse(
            subscriptionId = subscription.id,
            userId = subscription.userId,
            userEmail = user.email,
            userName = user.name,
            subscription = userSubscriptionResponse.subscription,
            plan = userSubscriptionResponse.plan,
            isActive = userSubscriptionResponse.isActive,
            creditsForPeriod = userSubscriptionResponse.creditsForPeriod,
            priceForPeriod = userSubscriptionResponse.priceForPeriod,
            // Real usage data from dashboard
            creditsRemaining = userUsageResponse.creditsRemaining,
            totalScreenshots = userUsageResponse.totalScreenshots,
            screenshotsLast30Days = userUsageResponse.screenshotsLast30Days
        )
    }
}

/**
 * Request model for subscription details lookup
 */
data class GetSubscriptionDetailsRequest(
    val subscriptionId: String
) {
    init {
        require(subscriptionId.isNotBlank()) { "Subscription ID cannot be blank" }
    }
}

/**
 * Response model that extends GetUserSubscriptionResponse with subscription context
 * Reuses existing domain entities (Subscription, Plan) for maximum consistency
 * Includes real usage data for accurate admin dashboard display
 */
data class GetSubscriptionDetailsResponse(
    val subscriptionId: String,
    val userId: String,
    val userEmail: String,
    val userName: String?,
    // Reused from GetUserSubscriptionResponse (no duplication)
    val subscription: dev.screenshotapi.core.domain.entities.Subscription?,
    val plan: dev.screenshotapi.core.domain.entities.Plan,
    val isActive: Boolean,
    val creditsForPeriod: Int,
    val priceForPeriod: Int,
    // Real usage data from GetUserUsageUseCase
    val creditsRemaining: Int,
    val totalScreenshots: Long,
    val screenshotsLast30Days: Long
)