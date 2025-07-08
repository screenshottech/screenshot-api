package dev.screenshotapi.infrastructure.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyUseCase
import dev.screenshotapi.infrastructure.auth.*
import dev.screenshotapi.infrastructure.config.AuthConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory


/**
 * Common API key validation logic
 */
private fun validateApiKey(
    validateApiKeyUseCase: ValidateApiKeyUseCase,
    logger: org.slf4j.Logger,
    authMethod: String
): suspend (String) -> ApiKeyPrincipal? = { apiKey ->
    try {
        val result = validateApiKeyUseCase(apiKey)
        if (result.isValid && result.userId != null && result.keyId != null) {
            logger.info("$authMethod authentication successful: userId=${result.userId}, keyId=${result.keyId}")
            ApiKeyPrincipal(
                keyId = result.keyId,
                userId = result.userId,
                name = "API Key"
            )
        } else {
            logger.warn("$authMethod authentication failed: invalid or inactive key")
            null
        }
    } catch (e: Exception) {
        logger.error("$authMethod authentication error: ${e.message}", e)
        null
    }
}

fun Application.configureSecurity() {
    val authConfig by inject<AuthConfig>()
    val validateApiKeyUseCase by inject<ValidateApiKeyUseCase>()
    val authProviderFactory by inject<AuthProviderFactory>()
    val jwtAuthProvider by inject<JwtAuthProvider>()
    val logger = LoggerFactory.getLogger("Security")

    // Create common API key validator
    val apiKeyValidator = validateApiKey(validateApiKeyUseCase, logger, "API Key")

    authentication {
        // JWT Authentication for user management routes
        jwt(AuthProviders.JWT_AUTH) {
            realm = authConfig.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(authConfig.jwtSecret))
                    .withAudience(authConfig.jwtAudience)
                    .withIssuer(authConfig.jwtIssuer)
                    .build()
            )
            validate { credential ->
                jwtAuthProvider.validateJwt(credential)
            }
        }

        // API Key Authentication for operational routes
        bearer(AuthProviders.API_KEY_AUTH) {
            realm = authConfig.realm
            authenticate { tokenCredential ->
                apiKeyValidator(tokenCredential.token)
            }
        }

        // Legacy API Key for backwards compatibility
        bearer(AuthProviders.API_KEY_LEGACY) {
            realm = authConfig.realm
            authenticate { tokenCredential ->
                apiKeyValidator(tokenCredential.token)
            }
        }

        // X-API-Key Authentication for developer-friendly API access
        apiKey(AuthProviders.X_API_KEY) {
            headerName = "X-API-Key"
            validate { apiKey ->
                apiKeyValidator(apiKey)
            }
        }

        // Multi-provider authentication for external provider integration
        bearer(AuthProviders.MULTI_PROVIDER) {
            realm = authConfig.realm
            authenticate { tokenCredential ->
                val token = tokenCredential.token
                val providerName = authConfig.defaultAuthProvider

                try {
                    val authProvider = authProviderFactory.getProvider(providerName) ?: return@authenticate null
                    val authResult = authProvider.validateToken(token) ?: return@authenticate null

                    MultiProviderPrincipal(
                        userId = authResult.userId,
                        email = authResult.email,
                        name = authResult.name,
                        provider = providerName
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
