package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.UserRepository

class CheckCreditsUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: CheckCreditsRequest): CheckCreditsResponse {
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)
        
        return CheckCreditsResponse(
            hasEnoughCredits = user.hasCredits(request.requiredCredits),
            availableCredits = user.creditsRemaining
        )
    }
}

data class CheckCreditsRequest(
    val userId: String,
    val requiredCredits: Int = 1
)

data class CheckCreditsResponse(
    val hasEnoughCredits: Boolean,
    val availableCredits: Int
)
