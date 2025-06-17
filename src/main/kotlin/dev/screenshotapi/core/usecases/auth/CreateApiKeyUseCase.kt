package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.ApiKey
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.Clock
import java.util.*
import dev.screenshotapi.core.domain.entities.Permission
import dev.screenshotapi.core.ports.output.HashingPort

class CreateApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository,
    private val hashingPort: HashingPort
) : UseCase<CreateApiKeyRequest, CreateApiKeyResponse> {
    
    override suspend fun invoke(request: CreateApiKeyRequest): CreateApiKeyResponse {
        // Validate input
        if (request.name.isBlank()) {
            throw ValidationException("API key name is required", "name")
        }
        
        // Check if user exists
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)
        
        // Generate secure API key
        val keyValue = "sk_${UUID.randomUUID().toString().replace("-", "")}"
        val keyHash = hashingPort.hashForLookup(keyValue)
        
        val apiKey = ApiKey(
            id = "key_${System.currentTimeMillis()}",
            userId = user.id,
            name = request.name,
            keyHash = keyHash,
            keyPrefix = "sk_${user.id.takeLast(8)}",
            permissions = setOf(Permission.SCREENSHOT_CREATE, Permission.SCREENSHOT_READ, Permission.SCREENSHOT_LIST),
            rateLimit = 1000, // Default rate limit
            usageCount = 0,
            isActive = true,
            lastUsed = null,
            expiresAt = null,
            createdAt = Clock.System.now()
        )
        
        // Save API key
        val savedApiKey = apiKeyRepository.save(apiKey)
        
        return CreateApiKeyResponse(
            id = savedApiKey.id,
            name = savedApiKey.name,
            keyValue = keyValue, // Only return the actual key value once, during creation
            isActive = savedApiKey.isActive,
            createdAt = savedApiKey.createdAt.toString()
        )
    }
}

data class CreateApiKeyRequest(
    val userId: String,
    val name: String
)

data class CreateApiKeyResponse(
    val id: String,
    val name: String,
    val keyValue: String,
    val isActive: Boolean,
    val createdAt: String
)
