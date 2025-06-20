package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.ApiKey
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository

class InMemoryApiKeyRepository : ApiKeyRepository {
    override suspend fun save(apiKey: ApiKey): ApiKey = InMemoryDatabase.saveApiKey(apiKey)

    override suspend fun findById(id: String): ApiKey? = InMemoryDatabase.findApiKey(id)

    override suspend fun findByKeyHash(keyHash: String): ApiKey? = InMemoryDatabase.findApiKeyByHash(keyHash)

    override suspend fun findByUserId(userId: String): List<ApiKey> = InMemoryDatabase.findApiKeysByUserId(userId)
    override suspend fun findDefaultByUserId(userId: String): ApiKey? {
        TODO("Not yet implemented")
    }

    override suspend fun update(apiKey: ApiKey): ApiKey = InMemoryDatabase.saveApiKey(apiKey)

    override suspend fun delete(id: String): Boolean = InMemoryDatabase.deleteApiKey(id)
    override suspend fun setAsDefault(userId: String, apiKeyId: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun clearDefaultForUser(userId: String): Boolean {
        TODO("Not yet implemented")
    }
}
