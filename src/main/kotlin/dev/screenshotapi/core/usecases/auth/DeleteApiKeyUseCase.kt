package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.core.usecases.common.UseCase

class DeleteApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository
) : UseCase<DeleteApiKeyRequest, DeleteApiKeyResponse> {

    override suspend fun invoke(request: DeleteApiKeyRequest): DeleteApiKeyResponse {
        val apiKey = apiKeyRepository.findById(request.apiKeyId)
            ?: throw ResourceNotFoundException("API Key", request.apiKeyId)

        // Ensure the user owns this API key
        if (apiKey.userId != request.userId) {
            throw AuthorizationException.InsufficientPermissions("API_KEY_DELETE")
        }

        val success = apiKeyRepository.delete(request.apiKeyId)

        return DeleteApiKeyResponse(
            success = success,
            message = if (success) "API key deleted successfully" else "Failed to delete API key"
        )
    }
}

data class DeleteApiKeyRequest(
    val userId: String,
    val apiKeyId: String
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(apiKeyId.isNotBlank()) { "API Key ID cannot be blank" }
    }
}

data class DeleteApiKeyResponse(
    val success: Boolean,
    val message: String
)
