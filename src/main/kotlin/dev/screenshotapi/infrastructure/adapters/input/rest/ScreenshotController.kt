package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.usecases.screenshot.*
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyUseCase
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyOwnershipUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.exceptions.AuthenticationException
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import dev.screenshotapi.infrastructure.auth.ApiKeyPrincipal
import dev.screenshotapi.infrastructure.auth.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * REST adapter for screenshot operations.
 * Handles HTTP requests, converts DTOs to domain objects, and domain objects back to DTOs.
 */
class ScreenshotController : KoinComponent {
    private val takeScreenshotUseCase: TakeScreenshotUseCase by inject()
    private val getScreenshotStatusUseCase: GetScreenshotStatusUseCase by inject()
    private val bulkGetScreenshotStatusUseCase: BulkGetScreenshotStatusUseCase by inject()
    private val listScreenshotsUseCase: ListScreenshotsUseCase by inject()
    private val validateApiKeyUseCase: ValidateApiKeyUseCase by inject()
    private val validateApiKeyOwnershipUseCase: ValidateApiKeyOwnershipUseCase by inject()
    private val logUsageUseCase: LogUsageUseCase by inject()

    suspend fun takeScreenshot(call: ApplicationCall) {
        val dto = call.receive<TakeScreenshotRequestDto>()
        
        // Extract authentication info with priority handling - throws exceptions if issues
        val authResult = extractAuthenticationInfo(call)

        // Convert DTO to domain request
        val useCaseRequest = dto.toDomainRequest(
            userId = authResult.userId,
            apiKeyId = authResult.apiKeyId
        )

        // Execute use case and convert response back to DTO
        val result = takeScreenshotUseCase(useCaseRequest)
        call.respond(HttpStatusCode.Accepted, result.toDto())
    }

    /**
     * Extracts authentication information with the following priority:
     * 1. X-API-Key header (with optional JWT validation)
     * 2. X-API-Key-ID header with JWT (requires JWT authentication)
     * 3. API Key from Authorization header
     * 4. JWT only (throws ApiKeyRequired exception)
     * 5. No authentication (throws InvalidCredentials exception)
     */
    private suspend fun extractAuthenticationInfo(call: ApplicationCall): AuthInfo {
        val apiKeyHeader = call.request.headers["X-API-Key"]
        val apiKeyIdHeader = call.request.headers["X-API-Key-ID"]
        val jwtPrincipal = call.principal<UserPrincipal>()
        val apiKeyPrincipal = call.principal<ApiKeyPrincipal>()

        return when {
            // Priority 1: X-API-Key header provided
            apiKeyHeader != null -> {
                val apiKey = validateApiKeyHeader(apiKeyHeader)
                    ?: throw AuthenticationException.InvalidCredentials()
                
                // If JWT is also present, validate that API key belongs to JWT user
                if (jwtPrincipal != null && apiKey.userId != jwtPrincipal.userId) {
                    throw AuthorizationException.ApiKeyNotOwned()
                }
                
                AuthInfo(apiKey.userId, apiKey.keyId)
            }
            
            // Priority 2: X-API-Key-ID header with JWT authentication
            apiKeyIdHeader != null && jwtPrincipal != null -> {
                val isValidApiKey = validateApiKeyId(apiKeyIdHeader, jwtPrincipal.userId)
                if (!isValidApiKey) {
                    throw AuthorizationException.ApiKeyNotOwned()
                }
                
                AuthInfo(jwtPrincipal.userId, apiKeyIdHeader)
            }
            
            // Priority 3: API Key from Authorization header (standalone)
            apiKeyPrincipal != null -> {
                AuthInfo(apiKeyPrincipal.userId, apiKeyPrincipal.keyId)
            }
            
            // Priority 4: JWT only - require API key
            jwtPrincipal != null -> {
                throw AuthorizationException.ApiKeyRequired()
            }
            
            // No authentication found
            else -> throw AuthenticationException.InvalidCredentials()
        }
    }

    private suspend fun validateApiKeyHeader(apiKey: String): ApiKeyInfo? {
        if (!apiKey.startsWith("sk_")) return null
        
        try {
            val result = validateApiKeyUseCase(apiKey)
            return if (result.isValid && result.userId != null && result.keyId != null) {
                ApiKeyInfo(result.userId, result.keyId)
            } else null
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun validateApiKeyId(apiKeyId: String, userId: String): Boolean {
        return try {
            val request = ValidateApiKeyOwnershipUseCase.Request(
                apiKeyId = apiKeyId,
                userId = userId
            )
            val result = validateApiKeyOwnershipUseCase(request)
            
            if (result.isValid && result.isActive) {
                // Log API key usage when ownership is validated
                logUsageUseCase.invoke(LogUsageUseCase.Request(
                    userId = userId,
                    action = UsageLogAction.API_KEY_USED,
                    apiKeyId = apiKeyId,
                    metadata = mapOf(
                        "validationType" to "ownership",
                        "method" to "X-API-Key-ID"
                    )
                ))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    data class AuthInfo(val userId: String, val apiKeyId: String)
    data class ApiKeyInfo(val userId: String, val keyId: String)

    suspend fun getScreenshotStatus(call: ApplicationCall) {
        val jobId = call.parameters["jobId"]!!
        
        // Support both API Key and JWT authentication
        val userId = when {
            call.principal<ApiKeyPrincipal>() != null -> {
                call.principal<ApiKeyPrincipal>()!!.userId
            }
            call.principal<UserPrincipal>() != null -> {
                call.principal<UserPrincipal>()!!.userId
            }
            else -> {
                call.respond(HttpStatusCode.Unauthorized, 
                    ErrorResponseDto.unauthorized("Authentication required"))
                return
            }
        }

        // Convert to domain request
        val useCaseRequest = GetScreenshotStatusUseCase.Request(
            jobId = jobId,
            userId = userId
        )

        // Execute use case and convert response back to DTO
        val result = getScreenshotStatusUseCase(useCaseRequest)
        call.respond(HttpStatusCode.OK, result.toDto())
    }

    suspend fun listScreenshots(call: ApplicationCall) {
        // Support both API Key and JWT authentication
        val userId = when {
            call.principal<ApiKeyPrincipal>() != null -> {
                call.principal<ApiKeyPrincipal>()!!.userId
            }
            call.principal<UserPrincipal>() != null -> {
                call.principal<UserPrincipal>()!!.userId
            }
            else -> {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
                return
            }
        }
        
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val statusParam = call.request.queryParameters["status"]

        // Convert to domain request
        val useCaseRequest = ListScreenshotsUseCase.Request(
            userId = userId,
            page = page,
            limit = limit,
            status = statusParam?.let { 
                dev.screenshotapi.core.domain.entities.ScreenshotStatus.valueOf(it.uppercase()) 
            }
        )

        // Execute use case and convert response back to DTO
        val result = listScreenshotsUseCase(useCaseRequest)
        call.respond(HttpStatusCode.OK, result.toDto())
    }

    suspend fun getBulkScreenshotStatus(call: ApplicationCall) {
        // Support both API Key and JWT authentication
        val userId = when {
            call.principal<ApiKeyPrincipal>() != null -> {
                call.principal<ApiKeyPrincipal>()!!.userId
            }
            call.principal<UserPrincipal>() != null -> {
                call.principal<UserPrincipal>()!!.userId
            }
            else -> {
                call.respond(HttpStatusCode.Unauthorized, 
                    ErrorResponseDto.unauthorized("Authentication required"))
                return
            }
        }

        // Parse job IDs from request body
        val requestDto = call.receive<BulkStatusRequestDto>()
        
        // Convert to domain request
        val useCaseRequest = BulkStatusRequest(
            jobIds = requestDto.jobIds,
            userId = userId
        )

        // Execute use case and convert response back to DTO
        val result = bulkGetScreenshotStatusUseCase(useCaseRequest)
        call.respond(HttpStatusCode.OK, result.toDto())
    }

}
