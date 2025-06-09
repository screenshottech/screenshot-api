package dev.screenshotapi.infrastructure.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyUseCase
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.infrastructure.auth.ApiKeyPrincipal
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
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
                if (credential.payload.audience.contains(authConfig.jwtAudience)) {
                    val userId = credential.payload.getClaim("userId")?.asString()

                    if (userId != null) {
                        // First try to find user by direct ID (for local auth)
                        var user = userRepository.findById(userId)

                        // If not found, try to find by external ID (for multi-provider auth)
                        if (user == null) {
                            user = userRepository.findByExternalId(userId, "clerk")
                            println("JWT validation - found user by external ID: ${user?.id}")
                        }

                        if (user != null) {
                            UserPrincipal(
                                userId = user.id, // Use internal ID
                                email = user.email,
                                name = user.name,
                                status = user.status,
                                permissions = emptySet(), // TODO: Load actual permissions
                                planId = user.planId,
                                creditsRemaining = user.creditsRemaining
                            )
                        } else {
                            logger.warn("JWT validation failed - user not found for ID: $userId")
                            null
                        }
                    } else {
                        logger.warn("JWT validation failed - no userId in token")
                        null
                    }
                } else {
                    logger.warn("JWT validation failed - audience mismatch")
                    null
                }
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
                        ApiKeyPrincipal(
                            keyId = result.keyId,
                            userId = result.userId,
                            name = "API Key"
                        )
                    } else null
                } catch (_: Exception) {
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
