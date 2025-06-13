package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException

class GetUserDetailsUseCase(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val getUserActivityUseCase: GetUserActivityUseCase
) {
    suspend operator fun invoke(request: GetUserDetailsRequest): GetUserDetailsResponse {
        // Get user details
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)
        
        // Get plan information
        val plan = planRepository.findById(user.planId)
            ?: throw ResourceNotFoundException("Plan", user.planId)
        
        // Get screenshot statistics
        val userScreenshots = screenshotRepository.findByUserId(user.id)
        val totalScreenshots = userScreenshots.size.toLong()
        val successfulScreenshots = userScreenshots.count { it.status.name == "COMPLETED" }.toLong()
        val failedScreenshots = userScreenshots.count { it.status.name == "FAILED" }.toLong()
        
        // Calculate total credits used (assuming 1 credit per screenshot for now)
        val totalCreditsUsed = totalScreenshots
        
        val userDetail = UserDetail(
            id = user.id,
            email = user.email,
            name = user.name,
            status = user.status,
            roles = user.roles.map { it.name },
            plan = PlanInfo(
                id = plan.id,
                name = plan.name,
                creditsPerMonth = plan.creditsPerMonth,
                priceCents = plan.priceCentsMonthly
            ),
            creditsRemaining = user.creditsRemaining,
            totalScreenshots = totalScreenshots,
            successfulScreenshots = successfulScreenshots,
            failedScreenshots = failedScreenshots,
            totalCreditsUsed = totalCreditsUsed,
            lastActivity = user.lastActivity,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            stripeCustomerId = user.stripeCustomerId
        )
        
        // Get activity if requested
        val activity = if (request.includeActivity) {
            val activityRequest = GetUserActivityRequest(
                userId = user.id,
                days = 30, // Last 30 days
                limit = 10 // Recent 10 activities
            )
            val activityResponse = getUserActivityUseCase(activityRequest)
            activityResponse.activities
        } else null
        
        // Get API keys if requested
        val apiKeys = if (request.includeApiKeys) {
            val userApiKeys = apiKeyRepository.findByUserId(user.id)
            userApiKeys.map { apiKey ->
                ApiKeyDetail(
                    id = apiKey.id,
                    name = apiKey.name,
                    permissions = apiKey.permissions,
                    isActive = apiKey.isActive,
                    lastUsed = apiKey.lastUsed,
                    createdAt = apiKey.createdAt
                )
            }
        } else null
        
        return GetUserDetailsResponse(
            user = userDetail,
            activity = activity,
            apiKeys = apiKeys
        )
    }
}
