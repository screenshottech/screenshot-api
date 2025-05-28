package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.UserStatus
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase

class UpdateUserProfileUseCase(
    private val userRepository: UserRepository
) : UseCase<UpdateUserProfileRequest, UpdateUserProfileResponse> {

    override suspend fun invoke(request: UpdateUserProfileRequest): UpdateUserProfileResponse {
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)

        val updatedUser = user.copy(
            name = request.name ?: user.name,
            email = request.email ?: user.email
        )

        val savedUser = userRepository.update(updatedUser)

        return UpdateUserProfileResponse(
            userId = savedUser.id,
            email = savedUser.email,
            name = savedUser.name,
            status = savedUser.status,
            planId = savedUser.planId,
            creditsRemaining = savedUser.creditsRemaining
        )
    }
}

data class UpdateUserProfileRequest(
    val userId: String,
    val name: String? = null,
    val email: String? = null
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name?.isNotBlank() != false) { "Name cannot be blank if provided" }
        require(email?.contains("@") != false) { "Invalid email format if provided" }
    }
}

data class UpdateUserProfileResponse(
    val userId: String,
    val email: String,
    val name: String?,
    val status: UserStatus,
    val planId: String,
    val creditsRemaining: Int
)
