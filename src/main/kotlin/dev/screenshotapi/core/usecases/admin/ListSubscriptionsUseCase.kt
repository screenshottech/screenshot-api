package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.usecases.auth.GetUserUsageUseCase
import dev.screenshotapi.core.usecases.auth.GetUserUsageRequest

class ListSubscriptionsUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val getUserUsageUseCase: GetUserUsageUseCase
) {
    suspend operator fun invoke(request: ListSubscriptionsRequest): ListSubscriptionsResponse {
        // Get subscriptions with pagination from repository
        val subscriptions = subscriptionRepository.findAllWithPagination(
            page = request.page,
            limit = request.limit,
            searchQuery = request.searchQuery,
            statusFilter = request.statusFilter,
            planIdFilter = request.planIdFilter
        )

        // Get total count for pagination
        val total = subscriptionRepository.countAllWithFilters(
            searchQuery = request.searchQuery,
            statusFilter = request.statusFilter,
            planIdFilter = request.planIdFilter
        )

        // Convert to SubscriptionSummary domain objects
        val subscriptionSummaries = subscriptions.mapNotNull { subscription ->
            val user = userRepository.findById(subscription.userId)
            if (user == null) {
                // Skip if user not found - this shouldn't happen in a healthy system
                return@mapNotNull null
            }

            // Get plan information
            val plan = planRepository.findById(subscription.planId)
            val planName = plan?.name ?: "Unknown Plan"

            // Get user's current credits
            val currentCredits = user.creditsRemaining
            val planCredits = plan?.creditsPerMonth ?: 0

            SubscriptionSummary(
                id = subscription.id,
                userId = subscription.userId,
                userEmail = user.email,
                userName = user.name,
                planId = subscription.planId,
                planName = planName,
                status = subscription.status,
                stripeSubscriptionId = subscription.stripeSubscriptionId,
                stripeCustomerId = subscription.stripeCustomerId,
                currentPeriodStart = subscription.currentPeriodStart,
                currentPeriodEnd = subscription.currentPeriodEnd,
                // Simple representation: always show current/available
                creditsUsed = 0, // Don't show negative or confusing numbers
                creditsLimit = currentCredits, // Show actual credits available
                createdAt = subscription.createdAt,
                updatedAt = subscription.updatedAt
            )
        }

        return ListSubscriptionsResponse(
            subscriptions = subscriptionSummaries,
            page = request.page,
            limit = request.limit,
            total = total
        )
    }
}