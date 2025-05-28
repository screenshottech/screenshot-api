package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.ApiKey
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import org.jetbrains.exposed.sql.Database

class PostgreSQLApiKeyRepository(private val database: Database) : ApiKeyRepository {
    override suspend fun save(apiKey: ApiKey): ApiKey {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findById(id: String): ApiKey? {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findByKeyHash(keyHash: String): ApiKey? {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun findByUserId(userId: String): List<ApiKey> {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun update(apiKey: ApiKey): ApiKey {
        TODO("PostgreSQL implementation not yet completed")
    }

    override suspend fun delete(id: String): Boolean {
        TODO("PostgreSQL implementation not yet completed")
    }
}
