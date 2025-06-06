package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.infrastructure.adapters.input.rest.dto.AuthProviderLoginRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.AuthProviderLoginResponseDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.AuthProvidersListResponseDto
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
import dev.screenshotapi.infrastructure.auth.providers.LocalAuthProvider
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.multiProviderAuthRoutes(authProviderFactory: AuthProviderFactory) {
    
    route("/auth") {
        
        // List available auth providers
        get("/providers") {
            val enabledProviders = authProviderFactory.getEnabledProviders().map { it.providerName }
            val defaultProvider = authProviderFactory.getDefaultProvider()?.providerName ?: "local"
            
            call.respond(
                HttpStatusCode.OK,
                AuthProvidersListResponseDto(
                    enabledProviders = enabledProviders,
                    defaultProvider = defaultProvider
                )
            )
        }
        
        // Login with specific provider
        post("/{provider}/login") {
            val providerName = call.parameters["provider"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                AuthProviderLoginResponseDto(
                    success = false,
                    message = "Provider name is required"
                )
            )
            
            val authProvider = authProviderFactory.getProvider(providerName) ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                AuthProviderLoginResponseDto(
                    success = false,
                    message = "Unsupported auth provider: $providerName"
                )
            )
            
            try {
                println("Received login request for provider: $providerName")
                
                val request = call.receive<AuthProviderLoginRequestDto>()
                println("Token received: ${request.token.take(50)}...")
                
                // For external providers, create user if needed; for local, just validate
                val authResult = if (providerName == "local") {
                    authProvider.validateToken(request.token)
                } else {
                    println("Creating user from token for provider: $providerName")
                    authProvider.createUserFromToken(request.token)
                }
                
                println("Auth result: $authResult")
                
                if (authResult == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        AuthProviderLoginResponseDto(
                            success = false,
                            message = "Invalid token"
                        )
                    )
                    return@post
                }
                
                // For external providers, generate a local JWT token
                val jwt = if (providerName == "local") {
                    request.token // Return the same token for local provider
                } else {
                    // Get local provider to generate JWT for external auth
                    val localProvider = authProviderFactory.getProvider("local") as? LocalAuthProvider
                    val generatedJwt = localProvider?.createToken(authResult.userId) ?: "token_generation_failed"
                    println("Generated JWT for user ${authResult.userId}: ${generatedJwt.take(50)}...")
                    generatedJwt
                }
                
                call.respond(
                    HttpStatusCode.OK,
                    AuthProviderLoginResponseDto(
                        success = true,
                        message = "Authentication successful",
                        userId = authResult.userId,
                        email = authResult.email,
                        name = authResult.name,
                        jwt = jwt
                    )
                )
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthProviderLoginResponseDto(
                        success = false,
                        message = "Authentication failed: ${e.message}"
                    )
                )
            }
        }
    }
}