package dev.screenshotapi.core.usecases.auth

class ValidateApiKeyUseCase {
    suspend operator fun invoke(apiKey: String): ApiKeyValidationResult {
        // For development, accept any API key that starts with "sk_"
        return if (apiKey.startsWith("sk_") && apiKey.length > 10) {
            ApiKeyValidationResult(
                isValid = true,
                userId = "user_123",
                keyId = "key_456"
            )
        } else {
            ApiKeyValidationResult(
                isValid = false,
                userId = null,
                keyId = null
            )
        }
    }
}

data class ApiKeyValidationResult(
    val isValid: Boolean,
    val userId: String? = null,
    val keyId: String? = null
)
