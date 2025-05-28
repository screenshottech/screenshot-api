package dev.screenshotapi.core.usecases.billing

class CheckCreditsUseCase {
    suspend operator fun invoke(request: CheckCreditsRequest): CheckCreditsResponse {
        return CheckCreditsResponse(
            hasCredits = true,
            creditsRemaining = 100
        )
    }
}

data class CheckCreditsRequest(
    val userId: String,
    val requiredAmount: Int = 1
)

data class CheckCreditsResponse(
    val hasCredits: Boolean,
    val creditsRemaining: Int
)
