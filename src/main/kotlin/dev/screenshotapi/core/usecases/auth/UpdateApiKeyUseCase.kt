package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.ApiKey
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.core.usecases.common.UseCase

class UpdateApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository
) : UseCase<UpdateApiKeyRequest, UpdateApiKeyResponse> {

    override suspend fun invoke(request: UpdateApiKeyRequest): UpdateApiKeyResponse {
        val apiKey = apiKeyRepository.findById(request.apiKeyId)
            ?: throw ResourceNotFoundException("API Key", request.apiKeyId)

        // Ensure the user owns this API key
        if (apiKey.userId != request.userId) {
            throw AuthorizationException.InsufficientPermissions("API_KEY_UPDATE")
        }

        // Handle default key logic before updating
        if (request.setAsDefault == true) {
            // Set this key as the default for the user
            apiKeyRepository.setAsDefault(request.userId, request.apiKeyId)
        }

        // Update only the fields that are provided
        val updatedApiKey = apiKey.copy(
            isActive = request.isActive ?: apiKey.isActive,
            name = request.name ?: apiKey.name,
            isDefault = if (request.setAsDefault == true) true else apiKey.isDefault
        )

        val result = apiKeyRepository.update(updatedApiKey)

        return UpdateApiKeyResponse(
            apiKey = result,
            message = "API key updated successfully"
        )
    }
}

data class UpdateApiKeyRequest(
    val userId: String,
    val apiKeyId: String,
    val isActive: Boolean? = null,
    val name: String? = null,
    val setAsDefault: Boolean? = null
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(apiKeyId.isNotBlank()) { "API Key ID cannot be blank" }
        require(isActive != null || name != null || setAsDefault != null) { "At least one field must be provided for update" }
    }
}

data class UpdateApiKeyResponse(
    val apiKey: ApiKey,
    val message: String
)