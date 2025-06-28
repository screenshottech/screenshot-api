package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.ApiKey
import dev.screenshotapi.core.domain.entities.Permission
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.ApiKeys
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Users
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import java.util.*

class PostgreSQLApiKeyRepository(private val database: Database) : ApiKeyRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(apiKey: ApiKey): ApiKey {
        return try {
            dbQuery(database) {
                val userExists = Users.select { Users.id eq apiKey.userId }.count() > 0

                if (!userExists) {
                    throw DatabaseException.OperationFailed("User not found: ${apiKey.userId}")
                }

                val entityId = if (apiKey.id.isNotBlank()) {
                    apiKey.id
                } else {
                    UUID.randomUUID().toString()
                }

                val insertedId = ApiKeys.insertAndGetId {
                    it[id] = entityId
                    it[userId] = apiKey.userId
                    it[name] = apiKey.name
                    it[keyHash] = apiKey.keyHash
                    it[keyPrefix] = apiKey.keyPrefix
                    it[permissions] = json.encodeToString(apiKey.permissions.map { p -> p.name })
                    it[rateLimit] = apiKey.rateLimit
                    it[usageCount] = apiKey.usageCount
                    it[isActive] = apiKey.isActive
                    it[isDefault] = apiKey.isDefault
                    it[lastUsed] = apiKey.lastUsed
                    it[expiresAt] = apiKey.expiresAt
                    it[createdAt] = apiKey.createdAt
                }

                apiKey.copy(id = insertedId.value)
            }
        } catch (e: Exception) {
            logger.error("Error saving API key: ${apiKey.id}", e)
            throw DatabaseException.OperationFailed("Failed to save API key", e)
        }
    }

    override suspend fun findById(id: String): ApiKey? {
        return try {
            dbQuery(database) {
                ApiKeys.select { ApiKeys.id eq id }
                    .singleOrNull()
                    ?.let { row -> mapRowToApiKey(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding API key by ID: $id", e)
            throw DatabaseException.OperationFailed("Failed to find API key by ID", e)
        }
    }

    override suspend fun findByKeyHash(keyHash: String): ApiKey? {
        return try {
            dbQuery(database) {
                ApiKeys.select { ApiKeys.keyHash eq keyHash }
                    .singleOrNull()
                    ?.let { row -> mapRowToApiKey(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding API key by hash", e)
            throw DatabaseException.OperationFailed("Failed to find API key by hash", e)
        }
    }

    override suspend fun findByUserId(userId: String): List<ApiKey> {
        return try {
            dbQuery(database) {
                ApiKeys.select { ApiKeys.userId eq userId }
                    .orderBy(ApiKeys.createdAt, SortOrder.DESC)
                    .map { row -> mapRowToApiKey(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding API keys for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find API keys for user", e)
        }
    }

    override suspend fun update(apiKey: ApiKey): ApiKey {
        return try {
            dbQuery(database) {
                val updatedRows = ApiKeys.update({ ApiKeys.id eq apiKey.id }) {
                    it[name] = apiKey.name
                    it[keyHash] = apiKey.keyHash
                    it[keyPrefix] = apiKey.keyPrefix
                    it[permissions] = json.encodeToString(apiKey.permissions.map { p -> p.name })
                    it[rateLimit] = apiKey.rateLimit
                    it[usageCount] = apiKey.usageCount
                    it[isActive] = apiKey.isActive
                    it[isDefault] = apiKey.isDefault
                    it[lastUsed] = apiKey.lastUsed
                    it[expiresAt] = apiKey.expiresAt
                }

                if (updatedRows == 0) {
                    throw DatabaseException.OperationFailed("API key not found: ${apiKey.id}")
                }

                apiKey
            }
        } catch (e: Exception) {
            logger.error("Error updating API key: ${apiKey.id}", e)
            throw DatabaseException.OperationFailed("Failed to update API key", e)
        }
    }

    override suspend fun delete(id: String): Boolean {
        return try {
            dbQuery(database) {
                val deletedRows = ApiKeys.deleteWhere { ApiKeys.id eq id }
                deletedRows > 0
            }
        } catch (e: Exception) {
            logger.error("Error deleting API key: $id", e)
            throw DatabaseException.OperationFailed("Failed to delete API key", e)
        }
    }

    override suspend fun findDefaultByUserId(userId: String): ApiKey? {
        return try {
            dbQuery(database) {
                ApiKeys.select { 
                    (ApiKeys.userId eq userId) and 
                    (ApiKeys.isDefault eq true) and 
                    (ApiKeys.isActive eq true)
                }
                    .singleOrNull()
                    ?.let { row -> mapRowToApiKey(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding default API key for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find default API key for user", e)
        }
    }

    override suspend fun setAsDefault(userId: String, apiKeyId: String): Boolean {
        return try {
            dbQuery(database) {
                // First clear any existing default key for this user
                clearDefaultForUser(userId)
                
                // Set the specified key as default
                val updatedRows = ApiKeys.update({ 
                    (ApiKeys.id eq apiKeyId) and 
                    (ApiKeys.userId eq userId) and 
                    (ApiKeys.isActive eq true)
                }) {
                    it[isDefault] = true
                }
                
                updatedRows > 0
            }
        } catch (e: Exception) {
            logger.error("Error setting API key as default: $apiKeyId for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to set API key as default", e)
        }
    }

    override suspend fun clearDefaultForUser(userId: String): Boolean {
        return try {
            dbQuery(database) {
                val updatedRows = ApiKeys.update({ 
                    (ApiKeys.userId eq userId) and (ApiKeys.isDefault eq true) 
                }) {
                    it[isDefault] = false
                }
                
                true // Always return true, even if no rows were updated
            }
        } catch (e: Exception) {
            logger.error("Error clearing default API key for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to clear default API key for user", e)
        }
    }

    private fun mapRowToApiKey(row: ResultRow): ApiKey {
        val permissionsJson = row[ApiKeys.permissions]
        val permissions = try {
            json.decodeFromString<List<String>>(permissionsJson)
                .mapNotNull { permissionName ->
                    try {
                        Permission.valueOf(permissionName)
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Unknown permission: $permissionName")
                        null
                    }
                }
                .toSet()
        } catch (e: Exception) {
            logger.warn("Failed to parse permissions JSON: $permissionsJson", e)
            emptySet()
        }

        return ApiKey(
            id = row[ApiKeys.id].value.toString(),
            userId = row[ApiKeys.userId].value.toString(),
            name = row[ApiKeys.name],
            keyHash = row[ApiKeys.keyHash],
            keyPrefix = row[ApiKeys.keyPrefix],
            permissions = permissions,
            rateLimit = row[ApiKeys.rateLimit],
            usageCount = row[ApiKeys.usageCount],
            isActive = row[ApiKeys.isActive],
            isDefault = row[ApiKeys.isDefault],
            lastUsed = row[ApiKeys.lastUsed],
            expiresAt = row[ApiKeys.expiresAt],
            createdAt = row[ApiKeys.createdAt]
        )
    }
}
