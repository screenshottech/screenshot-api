package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.UserStatus

class RegisterUserUseCase {
    suspend operator fun invoke(request: RegisterUserRequest): RegisterUserResponse {
        return RegisterUserResponse(
            success = true,
            userId = "user_${System.currentTimeMillis()}",
            email = request.email,
            status = UserStatus.ACTIVE
        )
    }
}

data class RegisterUserRequest(
    val email: String,
    val password: String,
    val name: String
)

data class RegisterUserResponse(
    val success: Boolean,
    val userId: String? = null,
    val email: String? = null,
    val status: UserStatus? = null
)
