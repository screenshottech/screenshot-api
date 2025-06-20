package dev.screenshotapi.infrastructure.auth

import dev.screenshotapi.core.domain.exceptions.AuthenticationException
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import io.ktor.server.application.*
import io.ktor.server.auth.*

/**
 * Enum representing the type of authentication being used
 */
enum class AuthType {
    JWT,
    API_KEY
}

/**
 * Utility class for safe and consistent authentication context handling.
 * Provides type-safe access to authentication principals with proper error handling.
 */
object AuthenticationContext {
    
    /**
     * Safely extracts UserPrincipal from JWT-authenticated routes.
     * Use this for user management, billing, and admin endpoints.
     * @throws AuthenticationException.InvalidCredentials if no valid JWT principal is found
     */
    fun getUserPrincipal(call: ApplicationCall): UserPrincipal {
        return call.principal<UserPrincipal>()
            ?: throw AuthenticationException.InvalidCredentials()
    }
    
    /**
     * Safely extracts ApiKeyPrincipal from API key-authenticated routes.
     * Use this for operational endpoints (screenshots, OCR, etc.).
     * @throws AuthorizationException.ApiKeyRequired if no valid API key principal is found
     */
    fun getApiKeyPrincipal(call: ApplicationCall): ApiKeyPrincipal {
        return call.principal<ApiKeyPrincipal>()
            ?: throw AuthorizationException.ApiKeyRequired()
    }
    
    /**
     * Gets user ID from JWT authentication context.
     * Throws exception if no valid JWT principal is found.
     */
    fun getUserId(call: ApplicationCall): String {
        return getUserPrincipal(call).userId
    }
    
    /**
     * Gets user ID from API key authentication context.
     * Throws exception if no valid API key principal is found.
     */
    fun getApiKeyUserId(call: ApplicationCall): String {
        return getApiKeyPrincipal(call).userId
    }
    
    /**
     * Gets API key ID from API key authentication context.
     * Throws exception if no valid API key principal is found.
     */
    fun getApiKeyId(call: ApplicationCall): String {
        return getApiKeyPrincipal(call).keyId
    }
    
    /**
     * Gets user ID from hybrid authentication context (JWT OR API Key).
     * Tries JWT first, then API Key. Use this for read endpoints that support both auth types.
     * @throws AuthenticationException.InvalidCredentials if no valid principal is found
     */
    fun getHybridUserId(call: ApplicationCall): String {
        // Try JWT first
        val userPrincipal = call.principal<UserPrincipal>()
        if (userPrincipal != null) {
            return userPrincipal.userId
        }
        
        // Try API Key second
        val apiKeyPrincipal = call.principal<ApiKeyPrincipal>()
        if (apiKeyPrincipal != null) {
            return apiKeyPrincipal.userId
        }
        
        throw AuthenticationException.InvalidCredentials()
    }
    
    /**
     * Gets authentication type from hybrid authentication context.
     * Returns the type of authentication being used.
     */
    fun getAuthenticationType(call: ApplicationCall): AuthType {
        val userPrincipal = call.principal<UserPrincipal>()
        if (userPrincipal != null) {
            return AuthType.JWT
        }
        
        val apiKeyPrincipal = call.principal<ApiKeyPrincipal>()
        if (apiKeyPrincipal != null) {
            return AuthType.API_KEY
        }
        
        throw AuthenticationException.InvalidCredentials()
    }
    
    /**
     * Gets API key ID from hybrid authentication context, but only if using API key auth.
     * Returns null if using JWT authentication.
     */
    fun getHybridApiKeyId(call: ApplicationCall): String? {
        val apiKeyPrincipal = call.principal<ApiKeyPrincipal>()
        return apiKeyPrincipal?.keyId
    }
    
    // Note: MultiProvider authentication is configured but not currently used in any routes
    // Uncomment if needed for future multi-provider endpoints
    /*
    /**
     * Safely extracts MultiProviderPrincipal from multi-provider routes.
     * Use this for external provider integration endpoints.
     * @throws AuthenticationException.InvalidCredentials if no valid multi-provider principal is found
     */
    fun getMultiProviderPrincipal(call: ApplicationCall): MultiProviderPrincipal {
        return call.principal<MultiProviderPrincipal>()
            ?: throw AuthenticationException.InvalidCredentials()
    }
    */
}

/**
 * Extension functions for ApplicationCall to provide convenient access to authentication context.
 */

/**
 * Gets UserPrincipal with proper error handling.
 * Use for JWT-authenticated routes.
 */
fun ApplicationCall.requireUserPrincipal(): UserPrincipal = 
    AuthenticationContext.getUserPrincipal(this)

/**
 * Gets ApiKeyPrincipal with proper error handling.
 * Use for API key-authenticated routes.
 */
fun ApplicationCall.requireApiKeyPrincipal(): ApiKeyPrincipal = 
    AuthenticationContext.getApiKeyPrincipal(this)

/**
 * Gets user ID from JWT authentication.
 */
fun ApplicationCall.requireUserId(): String = 
    AuthenticationContext.getUserId(this)

/**
 * Gets user ID from API key authentication.
 */
fun ApplicationCall.requireApiKeyUserId(): String = 
    AuthenticationContext.getApiKeyUserId(this)

/**
 * Gets API key ID from API key authentication.
 */
fun ApplicationCall.requireApiKeyId(): String = 
    AuthenticationContext.getApiKeyId(this)

/**
 * Gets user ID from hybrid authentication (JWT OR API Key).
 * Use for read endpoints that support both authentication types.
 */
fun ApplicationCall.requireHybridUserId(): String = 
    AuthenticationContext.getHybridUserId(this)

/**
 * Gets authentication type being used.
 */
fun ApplicationCall.getAuthType(): AuthType = 
    AuthenticationContext.getAuthenticationType(this)

/**
 * Gets API key ID if using API key authentication, null if using JWT.
 */
fun ApplicationCall.getHybridApiKeyId(): String? = 
    AuthenticationContext.getHybridApiKeyId(this)