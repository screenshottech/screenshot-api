package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.User
import dev.screenshotapi.core.domain.entities.UserStatus
import dev.screenshotapi.core.domain.exceptions.UserAlreadyExistsException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.Clock

class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository
) : UseCase<RegisterUserRequest, RegisterUserResponse> {
    
    override suspend fun invoke(request: RegisterUserRequest): RegisterUserResponse {
        // Validate input
        if (request.email.isBlank()) {
            throw ValidationException("Email is required", "email")
        }
        if (request.password.isBlank()) {
            throw ValidationException("Password is required", "password")
        }
        
        // Check if user already exists
        val existingUser = userRepository.findByEmail(request.email)
        if (existingUser != null) {
            throw UserAlreadyExistsException(request.email)
        }
        
        // Get the free plan (default for new users)
        val freePlan = planRepository.findById("plan_free")
            ?: planRepository.findAll().firstOrNull()
            ?: throw ValidationException("No plans available", "plan")
        
        // Create user with hashed password
        val passwordHash = request.password.hashCode().toString()
        val user = User(
            id = "user_${System.currentTimeMillis()}",
            email = request.email,
            name = request.name,
            passwordHash = passwordHash,
            status = UserStatus.ACTIVE,
            planId = freePlan.id,
            planName = freePlan.name,
            creditsRemaining = freePlan.creditsPerMonth,
            authProvider = "local",
            externalId = null,
            stripeCustomerId = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // Save user
        val savedUser = userRepository.save(user)
        
        return RegisterUserResponse(
            success = true,
            userId = savedUser.id,
            email = savedUser.email,
            status = savedUser.status
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
