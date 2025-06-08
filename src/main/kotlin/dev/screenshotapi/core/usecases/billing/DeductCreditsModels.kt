package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.entities.CreditDeductionReason
import kotlinx.datetime.Instant

data class DeductCreditsRequest(
    val userId: String,
    val amount: Int,
    val jobId: String? = null, // Optional job context for traceability
    val reason: CreditDeductionReason? = null // Optional reason for business traceability
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
