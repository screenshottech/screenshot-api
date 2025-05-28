package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.exceptions.PaymentException
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class HandlePaymentUseCase(
    private val userRepository: UserRepository,
    private val addCreditsUseCase: AddCreditsUseCase
) : UseCase<HandlePaymentRequest, HandlePaymentResponse> {

    override suspend fun invoke(request: HandlePaymentRequest): HandlePaymentResponse {
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)

        // Validate payment
        if (!isValidPayment(request)) {
            throw PaymentException.PaymentFailed("Invalid payment information")
        }

        // Calculate credits based on payment amount
        val creditsToAdd = calculateCredits(request.amountCents, request.planType)

        // Add credits to user account
        val addCreditsResponse = addCreditsUseCase(
            AddCreditsRequest(
                userId = request.userId,
                amount = creditsToAdd,
                transactionId = request.paymentId
            )
        )

        return HandlePaymentResponse(
            userId = request.userId,
            paymentId = request.paymentId,
            amountCents = request.amountCents,
            creditsAdded = creditsToAdd,
            newCreditBalance = addCreditsResponse.newCreditBalance,
            success = true,
            processedAt = Clock.System.now()
        )
    }

    private fun isValidPayment(request: HandlePaymentRequest): Boolean {
        // Basic validation - in real implementation, verify with payment provider
        return request.amountCents > 0 &&
                request.paymentId.isNotBlank() &&
                request.planType.isNotBlank()
    }

    private fun calculateCredits(amountCents: Long, planType: String): Int {
        // Credit calculation based on plan type
        return when (planType.lowercase()) {
            "starter" -> (amountCents / 100).toInt() * 50  // 50 credits per dollar
            "pro" -> (amountCents / 100).toInt() * 100     // 100 credits per dollar
            "business" -> (amountCents / 100).toInt() * 200 // 200 credits per dollar
            else -> (amountCents / 100).toInt() * 10       // Default: 10 credits per dollar
        }
    }
}

data class HandlePaymentRequest(
    val userId: String,
    val paymentId: String,
    val amountCents: Long,
    val planType: String,
    val stripeCustomerId: String? = null
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(paymentId.isNotBlank()) { "Payment ID cannot be blank" }
        require(amountCents > 0) { "Payment amount must be positive" }
        require(planType.isNotBlank()) { "Plan type cannot be blank" }
    }
}

data class HandlePaymentResponse(
    val userId: String,
    val paymentId: String,
    val amountCents: Long,
    val creditsAdded: Int,
    val newCreditBalance: Int,
    val success: Boolean,
    val processedAt: Instant
)
