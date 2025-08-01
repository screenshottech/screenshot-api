package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase

class AddCreditsUseCase(
    private val userRepository: UserRepository
) : UseCase<AddCreditsRequest, AddCreditsResponse> {

    override suspend fun invoke(request: AddCreditsRequest): AddCreditsResponse {
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)

        if (request.amount <= 0) {
            throw ValidationException.Positive("amount")
        }

        val updatedUser = user.copy(
            creditsRemaining = user.creditsRemaining + request.amount
        )

        val savedUser = userRepository.update(updatedUser)

        return AddCreditsResponse(
            userId = savedUser.id,
            creditsAdded = request.amount,
            newCreditBalance = savedUser.creditsRemaining,
            transactionId = request.transactionId
        )
    }
}

data class AddCreditsRequest(
    val userId: String,
    val amount: Int,
    val transactionId: String
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(amount > 0) { "Credits amount must be positive" }
        require(transactionId.isNotBlank()) { "Transaction ID cannot be blank" }
    }
}

data class AddCreditsResponse(
    val userId: String,
    val creditsAdded: Int,
    val newCreditBalance: Int,
    val transactionId: String
)
