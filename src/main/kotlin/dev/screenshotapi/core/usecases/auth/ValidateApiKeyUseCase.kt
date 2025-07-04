package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.ports.output.HashingPort
import dev.screenshotapi.infrastructure.config.AppConfig
import org.slf4j.LoggerFactory

class ValidateApiKeyUseCase(
    private val apiKeyRepository: ApiKeyRepository,
    private val config: AppConfig,
    private val logUsageUseCase: LogUsageUseCase,
    private val hashingPort: HashingPort
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend operator fun invoke(apiKey: String): ApiKeyValidationResult {
        return try {
            // For development, use hardcoded validation
            if (config.database.useInMemory) {
                return developmentValidation(apiKey)
            }
            
            // Production: lookup API key in database by secure hash
            val keyHash = hashingPort.hashForLookup(apiKey)
            val dbApiKey = apiKeyRepository.findByKeyHash(keyHash)
            
            if (dbApiKey != null && dbApiKey.isActive) {
                // Log API key usage
                logUsageUseCase.invoke(LogUsageUseCase.Request(
                    userId = dbApiKey.userId,
                    action = UsageLogAction.API_KEY_USED,
                    apiKeyId = dbApiKey.id,
                    metadata = mapOf(
                        "keyName" to dbApiKey.name,
                        "keyPrefix" to dbApiKey.keyPrefix
                    )
                ))
                
                ApiKeyValidationResult(
                    isValid = true,
                    userId = dbApiKey.userId,
                    keyId = dbApiKey.id
                )
            } else {
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
    
    private suspend fun developmentValidation(apiKey: String): ApiKeyValidationResult {
        // For development, accept any API key that starts with "sk_"
        return if (apiKey.startsWith("sk_") && apiKey.length > 10) {
            // Map specific keys to appropriate users for testing
            val userId = when {
                apiKey.contains("starter") || apiKey.contains("development") -> "user_starter_1"  // Starter plan user
                apiKey.contains("free") -> "user_free_1"  // Free plan user
                else -> "user_starter_1"  // Default to starter for testing
            }
            val keyId = "key_${userId}"
            
            // Log API key usage for development mode too
            logUsageUseCase.invoke(LogUsageUseCase.Request(
                userId = userId,
                action = UsageLogAction.API_KEY_USED,
                apiKeyId = keyId,
                metadata = mapOf(
                    "keyName" to "development_key",
                    "keyPrefix" to apiKey.take(8),
                    "mode" to "development"
                )
            ))
            
            ApiKeyValidationResult(
                isValid = true,
                userId = userId,
                keyId = keyId
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
