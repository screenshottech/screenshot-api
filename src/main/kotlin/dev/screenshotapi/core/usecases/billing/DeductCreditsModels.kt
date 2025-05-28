package dev.screenshotapi.core.usecases.billing

import kotlinx.datetime.Instant

data class DeductCreditsRequest(
    val userId: String,
    val amount: Int
) {
    init {
        require(amount > 0) { "Amount must be positive" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
    }
}

data class DeductCreditsResponse(
    val userId: String,
    val creditsDeducted: Int,
    val creditsRemaining: Int,
    val deductedAt: Instant
)
