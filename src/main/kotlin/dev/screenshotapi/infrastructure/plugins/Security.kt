package dev.screenshotapi.infrastructure.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyUseCase
import dev.screenshotapi.infrastructure.auth.ApiKeyPrincipal
import dev.screenshotapi.infrastructure.config.AuthConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
    val authConfig by inject<AuthConfig>()
    val validateApiKeyUseCase by inject<ValidateApiKeyUseCase>()

    authentication {
        // JWT Authentication for admin routes
        jwt("jwt") {
            realm = "screenshotapi-api"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(authConfig.jwtSecret))
                    .withAudience(authConfig.jwtAudience)
                    .withIssuer(authConfig.jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(authConfig.jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }

        // API Key Authentication for API routes
        bearer("api-key") {
            realm = "screenshotapi-api"
            authenticate { tokenCredential ->
                val apiKey = tokenCredential.token
                try {
                    // Validate API key using use case
                    val result = validateApiKeyUseCase(apiKey)
                    if (result.isValid && result.userId != null && result.keyId != null) {
                        // Return a custom principal with user info
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
    }
}
