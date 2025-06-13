package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.UserStatus
import dev.screenshotapi.core.domain.entities.UserRole
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException

class GetUserProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: GetUserProfileRequest): GetUserProfileResponse {
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User not found")
        
        return GetUserProfileResponse(
            userId = user.id,
            email = user.email,
            name = user.name,
            status = user.status,
            roles = user.roles,
            creditsRemaining = user.creditsRemaining
        )
    }
}

data class GetUserProfileRequest(
    val userId: String
)

data class GetUserProfileResponse(
    val userId: String,
    val email: String,
    val name: String?,
    val status: UserStatus,
    val roles: Set<UserRole>,
    val creditsRemaining: Int
)
