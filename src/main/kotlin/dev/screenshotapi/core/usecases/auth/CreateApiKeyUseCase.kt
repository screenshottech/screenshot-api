package dev.screenshotapi.core.usecases.auth

class CreateApiKeyUseCase {
    suspend operator fun invoke(request: CreateApiKeyRequest): CreateApiKeyResponse {
        return CreateApiKeyResponse(
            id = "api_${System.currentTimeMillis()}",
            name = request.name,
            keyValue = "sk_${System.currentTimeMillis()}_key",
            isActive = true,
            createdAt = "2023-01-01T00:00:00Z"
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
