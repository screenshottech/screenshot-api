package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.entities.UserStatus

class GetUserDetailsUseCase {
    suspend operator fun invoke(request: GetUserDetailsRequest): GetUserDetailsResponse {
        return GetUserDetailsResponse(
            user = UserDetail(
                id = request.userId,
                email = "user@example.com",
                name = "Test User",
                status = UserStatus.ACTIVE,
                plan = PlanInfo(
                    id = "plan_1",
                    name = "Basic",
                    creditsPerMonth = 1000,
                    priceCents = 999
                ),
                creditsRemaining = 100,
                totalScreenshots = 0,
                successfulScreenshots = 0,
                failedScreenshots = 0,
                totalCreditsUsed = 0,
                lastActivity = null,
                createdAt = kotlinx.datetime.Clock.System.now(),
                updatedAt = kotlinx.datetime.Clock.System.now(),
                stripeCustomerId = null
            ),
            activity = if (request.includeActivity) emptyList() else null,
            apiKeys = if (request.includeApiKeys) emptyList() else null
        )
    }
}
