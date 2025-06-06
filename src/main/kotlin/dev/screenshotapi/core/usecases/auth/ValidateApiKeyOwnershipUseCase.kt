package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.core.usecases.common.UseCase

/**
 * Use case to validate that an API key belongs to a specific user
 */
class ValidateApiKeyOwnershipUseCase(
    private val apiKeyRepository: ApiKeyRepository
) : UseCase<ValidateApiKeyOwnershipUseCase.Request, ValidateApiKeyOwnershipUseCase.Response> {

    data class Request(
        val apiKeyId: String,
        val userId: String
    )

    data class Response(
        val isValid: Boolean,
        val isActive: Boolean = false
    )

    override suspend fun invoke(request: Request): Response {
        return try {
            val apiKey = apiKeyRepository.findById(request.apiKeyId)
            
            when {
                apiKey == null -> Response(isValid = false)
                apiKey.userId != request.userId -> Response(isValid = false)
                !apiKey.isActive -> Response(isValid = false, isActive = false)
                else -> Response(isValid = true, isActive = true)
            }
        } catch (e: Exception) {
            Response(isValid = false)
        }
    }
}