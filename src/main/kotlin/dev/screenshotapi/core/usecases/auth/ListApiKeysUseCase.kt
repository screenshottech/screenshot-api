package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.ApiKey
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase

class ListApiKeysUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository
) : UseCase<ListApiKeysRequest, ListApiKeysResponse> {

    override suspend fun invoke(request: ListApiKeysRequest): ListApiKeysResponse {
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)

        val apiKeys = apiKeyRepository.findByUserId(request.userId)

        return ListApiKeysResponse(
            apiKeys = apiKeys.map { it.toSummary() }
        )
    }

    private fun ApiKey.toSummary() = ApiKeySummary(
        id = id,
        name = name,
        isActive = isActive,
        maskedKey = "sk_***${keyHash.takeLast(4)}",
        usageCount = usageCount,
        createdAt = createdAt,
        lastUsedAt = lastUsed
    )
}

data class ListApiKeysRequest(
    val userId: String
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
    }
}

data class ListApiKeysResponse(
    val apiKeys: List<ApiKeySummary>
)

data class ApiKeySummary(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val maskedKey: String,
    val usageCount: Long,
    val createdAt: kotlinx.datetime.Instant,
    val lastUsedAt: kotlinx.datetime.Instant?
)
