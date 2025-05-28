package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class GetUserUsageUseCase(
    private val userRepository: UserRepository,
    private val screenshotRepository: ScreenshotRepository
) : UseCase<GetUserUsageRequest, GetUserUsageResponse> {

    override suspend fun invoke(request: GetUserUsageRequest): GetUserUsageResponse {
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)

        val now = Clock.System.now()
        val thirtyDaysAgo = now.minus(30.days)

        val totalScreenshots = screenshotRepository.countByUserId(request.userId)
        val recentScreenshots = screenshotRepository.findByUserId(
            userId = request.userId,
            page = 1,
            limit = Int.MAX_VALUE
        ).count { it.createdAt >= thirtyDaysAgo }

        return GetUserUsageResponse(
            userId = user.id,
            creditsRemaining = user.creditsRemaining,
            totalScreenshots = totalScreenshots,
            screenshotsLast30Days = recentScreenshots.toLong(),
            planId = user.planId,
            currentPeriodStart = thirtyDaysAgo,
            currentPeriodEnd = now
        )
    }
}

data class GetUserUsageRequest(
    val userId: String
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
    }
}

data class GetUserUsageResponse(
    val userId: String,
    val creditsRemaining: Int,
    val totalScreenshots: Long,
    val screenshotsLast30Days: Long,
    val planId: String,
    val currentPeriodStart: Instant,
    val currentPeriodEnd: Instant
)
