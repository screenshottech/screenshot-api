package dev.screenshotapi.infrastructure.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyUseCase
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.infrastructure.auth.ApiKeyPrincipal
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
import dev.screenshotapi.infrastructure.auth.JwtAuthProvider
import dev.screenshotapi.infrastructure.auth.MultiProviderPrincipal
import dev.screenshotapi.infrastructure.auth.UserPrincipal
import dev.screenshotapi.infrastructure.config.AuthConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private const val REALM = "screenshotapi-api"

fun Application.configureSecurity() {
    val authConfig by inject<AuthConfig>()
    val validateApiKeyUseCase by inject<ValidateApiKeyUseCase>()
    val authProviderFactory by inject<AuthProviderFactory>()
    val userRepository by inject<UserRepository>()
    val jwtAuthProvider by inject<JwtAuthProvider>()
    val logger = LoggerFactory.getLogger("Security")

    authentication {
        // JWT Authentication for admin routes
        jwt("jwt") {
            realm = REALM
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

        // API Key Authentication for API routes
        bearer("api-key") {
            realm = REALM
            authenticate { tokenCredential ->
                val apiKey = tokenCredential.token
                try {
                    val result = validateApiKeyUseCase(apiKey)
                    if (result.isValid && result.userId != null && result.keyId != null) {
                        logger.info("API key authentication successful: userId=${result.userId}, keyId=${result.keyId}")
                        ApiKeyPrincipal(
                            keyId = result.keyId,
                            userId = result.userId,
                            name = "API Key"
                        )
                    } else {
                        logger.warn("API key authentication failed: invalid or inactive key")
                        null
                    }
                } catch (e: Exception) {
                    logger.error("API key authentication error: ${e.message}", e)
                    null
                }
            }
        }

        // Multi-provider authentication for dashboard routes
        bearer("multi-provider") {
            realm = REALM
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
