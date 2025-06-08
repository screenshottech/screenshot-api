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

        // Update only the fields that are provided
        val updatedApiKey = apiKey.copy(
            isActive = request.isActive ?: apiKey.isActive,
            name = request.name ?: apiKey.name
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
    val name: String? = null
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(apiKeyId.isNotBlank()) { "API Key ID cannot be blank" }
        require(isActive != null || name != null) { "At least one field must be provided for update" }
    }
}

data class UpdateApiKeyResponse(
    val apiKey: ApiKey,
    val message: String
)