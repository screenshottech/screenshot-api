package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.ApiKey

interface ApiKeyRepository {
    suspend fun save(apiKey: ApiKey): ApiKey
    suspend fun findById(id: String): ApiKey?
    suspend fun findByKeyHash(keyHash: String): ApiKey?
    suspend fun findByUserId(userId: String): List<ApiKey>
    suspend fun update(apiKey: ApiKey): ApiKey
    suspend fun delete(id: String): Boolean
}
