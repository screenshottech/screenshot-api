package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.domain.entities.UsageLogAction

class DeductCreditsUseCase(
    private val userRepository: UserRepository,
    private val logUsageUseCase: LogUsageUseCase
) {
    suspend operator fun invoke(request: DeductCreditsRequest): DeductCreditsResponse {
        // Get user to check current credits
        val user = userRepository.findById(request.userId)
            ?: throw IllegalArgumentException("User not found: ${request.userId}")
        
        // Check if user has sufficient credits
        if (user.creditsRemaining < request.amount) {
            throw IllegalStateException("Insufficient credits. Required: ${request.amount}, Available: ${user.creditsRemaining}")
        }
        
        // Deduct credits from user
        val updatedUser = user.copy(
            creditsRemaining = user.creditsRemaining - request.amount,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
        userRepository.update(updatedUser)
        
        val deductedAt = kotlinx.datetime.Clock.System.now()
        
        // Log the credit deduction in usage logs
        logUsageUseCase.invoke(LogUsageUseCase.Request(
            userId = request.userId,
            action = UsageLogAction.CREDITS_DEDUCTED,
            creditsUsed = request.amount,
            metadata = mapOf(
                "previousCredits" to user.creditsRemaining.toString(),
                "newCredits" to updatedUser.creditsRemaining.toString(),
                "deductedAmount" to request.amount.toString()
            ),
            timestamp = deductedAt
        ))
        
        return DeductCreditsResponse(
            userId = request.userId,
            creditsDeducted = request.amount,
            creditsRemaining = updatedUser.creditsRemaining,
            deductedAt = deductedAt
        )
    }
}
