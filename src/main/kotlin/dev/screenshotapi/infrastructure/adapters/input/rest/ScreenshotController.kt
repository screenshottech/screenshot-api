package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.usecases.screenshot.*
import dev.screenshotapi.core.usecases.auth.ListApiKeysUseCase
import dev.screenshotapi.core.usecases.auth.ListApiKeysRequest
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import dev.screenshotapi.infrastructure.auth.requireApiKeyUserId
import dev.screenshotapi.infrastructure.auth.requireApiKeyId
import dev.screenshotapi.infrastructure.auth.requireHybridUserId
import dev.screenshotapi.infrastructure.auth.getHybridApiKeyId
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
    private val manualRetryScreenshotUseCase: ManualRetryScreenshotUseCase by inject()
    private val listApiKeysUseCase: ListApiKeysUseCase by inject()
    private val logUsageUseCase: LogUsageUseCase by inject()

    /**
     * Find an active API key for the user (prefers default, falls back to any active)
     */
    private suspend fun findActiveApiKeyForUser(userId: String): String? {
        val response = listApiKeysUseCase(ListApiKeysRequest(userId))
        val activeApiKeys = response.apiKeys.filter { it.isActive }
        
        // First, try to find the default API key
        val defaultApiKey = activeApiKeys.find { it.isDefault }
        if (defaultApiKey != null) {
            return defaultApiKey.id
        }
        
        // If no default, return the first active API key
        return activeApiKeys.firstOrNull()?.id
    }

    suspend fun takeScreenshot(call: ApplicationCall) {
        val dto = call.receive<TakeScreenshotRequestDto>()
        
        // Get user ID from hybrid authentication (JWT OR API Key)
        val userId = call.requireHybridUserId()
        
        // Check if API key was provided in request or needs to be auto-selected
        val providedApiKeyId = call.getHybridApiKeyId()
        val apiKeyId = providedApiKeyId 
            ?: findActiveApiKeyForUser(userId)
            ?: throw AuthorizationException.ApiKeyRequired()

        // If we auto-selected an API key (not provided in request), log the usage
        // This ensures consistent tracking between pure API key auth and hybrid auth
        if (providedApiKeyId == null) {
            // Log API key usage for auto-selected key
            logUsageUseCase.invoke(LogUsageUseCase.Request(
                userId = userId,
                action = UsageLogAction.API_KEY_USED,
                apiKeyId = apiKeyId,
                metadata = mapOf(
                    "mode" to "auto_selected",
                    "endpoint" to "screenshots"
                )
            ))
        }

        // Convert DTO to domain request
        val useCaseRequest = dto.toDomainRequest(
            userId = userId,
            apiKeyId = apiKeyId
        )

        // Execute use case and convert response back to DTO
        val result = takeScreenshotUseCase(useCaseRequest)
        call.respond(HttpStatusCode.Accepted, result.toDto())
    }


    suspend fun getScreenshotStatus(call: ApplicationCall) {
        val jobId = call.parameters["jobId"]!!
        
        // Get user ID from hybrid authentication (JWT OR API Key)
        val userId = call.requireHybridUserId()

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
        // Get user ID from hybrid authentication (JWT OR API Key)
        val userId = call.requireHybridUserId()
        
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
        // Get user ID from hybrid authentication (JWT OR API Key)
        val userId = call.requireHybridUserId()

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

    suspend fun retryScreenshot(call: ApplicationCall) {
        val jobId = call.parameters["jobId"]!!
        
        // Get user ID from hybrid authentication (JWT OR API Key)
        val userId = call.requireHybridUserId()
        val apiKeyId = call.getHybridApiKeyId()

        // Convert to domain request
        val useCaseRequest = ManualRetryScreenshotUseCase.Request(
            jobId = jobId,
            userId = userId,
            requestedBy = apiKeyId ?: "web"
        )

        // Execute use case and convert response back to DTO
        val result = manualRetryScreenshotUseCase(useCaseRequest)
        call.respond(HttpStatusCode.OK, result.toDto())
    }

}
