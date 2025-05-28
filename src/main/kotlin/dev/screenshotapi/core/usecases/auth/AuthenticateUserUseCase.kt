package dev.screenshotapi.core.usecases.auth

class AuthenticateUserUseCase {
    suspend operator fun invoke(request: AuthenticateUserRequest): AuthenticateUserResponse {
        return AuthenticateUserResponse(
            success = true,
            userId = "user_123",
            email = request.email,
            token = "jwt_token_456"
        )
    }
}

data class AuthenticateUserRequest(
    val email: String,
    val password: String
)

data class AuthenticateUserResponse(
    val success: Boolean,
    val userId: String? = null,
    val email: String? = null,
    val token: String? = null
)
