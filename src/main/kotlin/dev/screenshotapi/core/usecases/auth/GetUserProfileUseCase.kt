package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.UserStatus

class GetUserProfileUseCase {
    suspend operator fun invoke(request: GetUserProfileRequest): GetUserProfileResponse {
        return GetUserProfileResponse(
            userId = request.userId,
            email = "user@example.com",
            name = "Test User",
            status = UserStatus.ACTIVE,
            creditsRemaining = 100
        )
    }
}

data class GetUserProfileRequest(
    val userId: String
)

data class GetUserProfileResponse(
    val userId: String,
    val email: String,
    val name: String,
    val status: UserStatus,
    val creditsRemaining: Int
)
