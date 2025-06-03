package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.infrastructure.config.AppConfig
import org.slf4j.LoggerFactory

class ValidateApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val config: AppConfig
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend operator fun invoke(apiKey: String): ApiKeyValidationResult {
        return try {
            // For development, use hardcoded validation
            if (config.database.useInMemory) {
                return developmentValidation(apiKey)
            }
            
            // Production: lookup API key in database by hash
            val keyHash = apiKey.hashCode().toString()
            val dbApiKey = apiKeyRepository.findByKeyHash(keyHash)
            
            if (dbApiKey != null && dbApiKey.isActive) {
                logger.info("API key validated for user: ${dbApiKey.userId}")
                ApiKeyValidationResult(
                    isValid = true,
                    userId = dbApiKey.userId,
                    keyId = dbApiKey.id
                )
            } else {
                logger.warn("Invalid or inactive API key attempted")
                ApiKeyValidationResult(
                    isValid = false,
                    userId = null,
                    keyId = null
                )
            }
        } catch (e: Exception) {
            logger.error("Error validating API key", e)
            ApiKeyValidationResult(
                isValid = false,
                userId = null,
                keyId = null
            )
        }
    }
    
    private fun developmentValidation(apiKey: String): ApiKeyValidationResult {
        // For development, accept any API key that starts with "sk_"
        return if (apiKey.startsWith("sk_") && apiKey.length > 10) {
            // Map specific keys to appropriate users for testing
            val userId = when {
                apiKey.contains("starter") || apiKey.contains("development") -> "user_starter_1"  // Starter plan user
                apiKey.contains("free") -> "user_free_1"  // Free plan user
                else -> "user_starter_1"  // Default to starter for testing
            }
            
            ApiKeyValidationResult(
                isValid = true,
                userId = userId,
                keyId = "key_${userId}"
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
