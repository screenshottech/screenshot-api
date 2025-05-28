package dev.screenshotapi.core.usecases.billing

class DeductCreditsUseCase {
    suspend operator fun invoke(request: DeductCreditsRequest): DeductCreditsResponse {
        return DeductCreditsResponse(
            userId = request.userId,
            creditsDeducted = request.amount,
            creditsRemaining = 99,
            deductedAt = kotlinx.datetime.Clock.System.now()
        )
    }
}
