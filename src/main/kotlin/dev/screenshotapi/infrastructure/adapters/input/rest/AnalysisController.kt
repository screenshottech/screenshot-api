package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.domain.entities.AnalysisStatus
import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.usecases.analysis.CreateAnalysisUseCase
import dev.screenshotapi.core.usecases.analysis.GetAnalysisStatusUseCase
import dev.screenshotapi.core.usecases.analysis.GetScreenshotAnalysesUseCase
import dev.screenshotapi.core.usecases.analysis.ListAnalysesUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import dev.screenshotapi.infrastructure.auth.requireHybridUserId
import dev.screenshotapi.infrastructure.auth.getHybridApiKeyId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Analysis Controller - REST adapter for AI analysis operations
 * 
 * Handles HTTP requests for the new separate Analysis API:
 * - POST /api/v1/screenshots/{jobId}/analyze - Create analysis request
 * - GET /api/v1/analysis/{analysisJobId} - Get analysis status and results  
 * - GET /api/v1/analysis - List user's analyses with pagination
 * 
 * This controller implements the separate flow architecture where
 * analysis is decoupled from screenshot generation for better scalability.
 */
class AnalysisController : KoinComponent {
    private val createAnalysisUseCase: CreateAnalysisUseCase by inject()
    private val getAnalysisStatusUseCase: GetAnalysisStatusUseCase by inject()
    private val getScreenshotAnalysesUseCase: GetScreenshotAnalysesUseCase by inject()
    private val listAnalysesUseCase: ListAnalysesUseCase by inject()
    private val logUsageUseCase: LogUsageUseCase by inject()
    
    private val logger = LoggerFactory.getLogger(AnalysisController::class.java)

    // Supported languages for analysis
    private val supportedLanguages = setOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh",
        "ar", "hi", "th", "vi", "nl", "sv", "da", "no", "fi", "pl"
    )

    /**
     * Create Analysis Request
     * POST /api/v1/screenshots/{jobId}/analyze
     * 
     * Creates an AI analysis job for a completed screenshot.
     * Requires the screenshot to be in COMPLETED status.
     */
    suspend fun createAnalysis(call: ApplicationCall) {
        val screenshotJobId = call.parameters["jobId"]
            ?: return call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing jobId parameter"))
            
        // Validate job ID format (allow alphanumeric, hyphens, and underscores)
        if (screenshotJobId.isBlank() || !screenshotJobId.matches(Regex("^[a-zA-Z0-9_-]{1,100}$"))) {
            throw ValidationException.InvalidFormat(
                "jobId",
                "must be alphanumeric with hyphens and underscores, max 100 characters"
            )
        }
        
        val dto = call.receive<CreateAnalysisRequestDto>()
        
        // Get user ID from hybrid authentication (JWT OR API Key)
        val userId = call.requireHybridUserId()
        val apiKeyId = call.getHybridApiKeyId()
        
        logger.info(
            "Creating analysis for screenshot $screenshotJobId, " +
            "type: ${dto.analysisType}, " +
            "user: $userId"
        )
        
        try {
            // Validate analysis type
            val analysisType = validateAnalysisType(dto.analysisType)
                ?: return call.respond(
                    HttpStatusCode.BadRequest, 
                    createValidationErrorResponse(
                        "analysisType", 
                        dto.analysisType, 
                        AnalysisType.entries.map { it.name }
                    )
                )
            
            // Validate language
            val language = dto.language ?: "en"
            if (!supportedLanguages.contains(language)) {
                return call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Unsupported language: $language",
                        "supportedLanguages" to supportedLanguages.toList()
                    )
                )
            }
            
            // Validate webhook URL if provided
            dto.webhookUrl?.let { url ->
                if (!isValidWebhookUrl(url)) {
                    return call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "Invalid webhook URL format: $url",
                            "requirement" to "Must be a valid HTTP/HTTPS URL"
                        )
                    )
                }
            }
            
            // Convert DTO to domain request
            val useCaseRequest = dto.toDomainRequest(
                userId = userId,
                screenshotJobId = screenshotJobId,
                apiKeyId = apiKeyId
            )
            
            // Execute use case
            val result = createAnalysisUseCase(useCaseRequest)
            
            // Log successful analysis creation
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = userId,
                    action = UsageLogAction.API_KEY_USED,
                    creditsUsed = 0, // Credits already deducted in use case
                    apiKeyId = apiKeyId,
                    metadata = mapOf(
                        "endpoint" to "POST /api/v1/screenshots/{jobId}/analyze",
                        "analysisType" to dto.analysisType,
                        "screenshotJobId" to screenshotJobId,
                        "analysisJobId" to result.analysisJobId
                    )
                )
            )
            
            call.respond(HttpStatusCode.Accepted, result.toDto())
            
        } catch (e: Exception) {
            logger.error("Failed to create analysis for screenshot $screenshotJobId", e)
            
            // Log failed attempt
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = userId,
                    action = UsageLogAction.API_KEY_USED,
                    creditsUsed = 0,
                    apiKeyId = apiKeyId,
                    metadata = mapOf(
                        "endpoint" to "POST /api/v1/screenshots/{jobId}/analyze",
                        "error" to (e.message ?: "Unknown error"),
                        "screenshotJobId" to screenshotJobId
                    )
                )
            )
            
            throw e
        }
    }

    /**
     * Get Analysis Status
     * GET /api/v1/analysis/{analysisJobId}
     * 
     * Retrieves the status, progress, and results of an analysis job.
     */
    suspend fun getAnalysisStatus(call: ApplicationCall) {
        val analysisJobId = call.parameters["analysisJobId"]
            ?: return call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing analysisJobId parameter"))
        
        // Get user ID from hybrid authentication (JWT OR API Key)  
        val userId = call.requireHybridUserId()
        val apiKeyId = call.getHybridApiKeyId()
        
        logger.debug("Getting analysis status for job $analysisJobId, user: $userId")
        
        try {
            // Convert to domain request
            val useCaseRequest = GetAnalysisStatusUseCase.Request(
                analysisJobId = analysisJobId,
                userId = userId
            )
            
            // Execute use case
            val result = getAnalysisStatusUseCase(useCaseRequest)
            
            // Log API usage
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = userId,
                    action = UsageLogAction.API_KEY_USED,
                    creditsUsed = 0,
                    apiKeyId = apiKeyId,
                    metadata = mapOf(
                        "endpoint" to "GET /api/v1/analysis/{analysisJobId}",
                        "analysisJobId" to analysisJobId,
                        "status" to result.status.name
                    )
                )
            )
            
            call.respond(HttpStatusCode.OK, result.toDto())
            
        } catch (e: Exception) {
            logger.error("Failed to get analysis status for job $analysisJobId", e)
            throw e
        }
    }

    /**
     * List Analyses
     * GET /api/v1/analysis
     * 
     * Lists user's analysis jobs with pagination and optional filtering.
     * Query parameters:
     * - page: Page number (default: 1)
     * - limit: Items per page (default: 20, max: 100)
     * - status: Filter by status (optional)
     */
    suspend fun listAnalyses(call: ApplicationCall) {
        // Get user ID from hybrid authentication (JWT OR API Key)
        val userId = call.requireHybridUserId()
        val apiKeyId = call.getHybridApiKeyId()
        
        // Parse query parameters
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val statusParam = call.request.queryParameters["status"]
        
        logger.debug("Listing analyses for user $userId, page: $page, limit: $limit")
        
        try {
            // Validate parameters
            if (page < 1) {
                return call.respond(
                    HttpStatusCode.BadRequest, 
                    mapOf("error" to "Page must be greater than 0")
                )
            }
            
            if (limit < 1 || limit > 100) {
                return call.respond(
                    HttpStatusCode.BadRequest, 
                    mapOf("error" to "Limit must be between 1 and 100")
                )
            }
            
            // Parse status filter
            val status = statusParam?.let { statusStr ->
                try {
                    AnalysisStatus.valueOf(statusStr.uppercase())
                } catch (e: IllegalArgumentException) {
                    return call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "Invalid status: $statusStr",
                            "validStatuses" to AnalysisStatus.entries.map { it.name }
                        )
                    )
                }
            }
            
            // Convert to domain request
            val useCaseRequest = ListAnalysesUseCase.Request(
                userId = userId,
                page = page,
                limit = limit,
                status = status
            )
            
            // Execute use case
            val result = listAnalysesUseCase(useCaseRequest)
            
            // Log API usage
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = userId,
                    action = UsageLogAction.API_KEY_USED,
                    creditsUsed = 0,
                    apiKeyId = apiKeyId,
                    metadata = mapOf(
                        "endpoint" to "GET /api/v1/analysis",
                        "page" to page.toString(),
                        "limit" to limit.toString(),
                        "statusFilter" to (status?.name ?: "none"),
                        "resultsCount" to result.analyses.size.toString()
                    )
                )
            )
            
            call.respond(HttpStatusCode.OK, result.toDto())
            
        } catch (e: Exception) {
            logger.error("Failed to list analyses for user $userId", e)
            throw e
        }
    }

    /**
     * Get Screenshot Analyses
     * GET /api/v1/screenshots/{jobId}/analyses
     * 
     * Retrieves analysis jobs for a specific screenshot with optional pagination.
     * Query parameters:
     * - page: Page number (default: 1)
     * - limit: Maximum number of analyses to return (default: 20, max: 100)
     * 
     * Provides access control to ensure users can only see analyses for their own screenshots.
     */
    suspend fun getScreenshotAnalyses(call: ApplicationCall) {
        val screenshotJobId = call.parameters["jobId"]
            ?: return call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing jobId parameter"))
            
        // Validate job ID format (allow alphanumeric, hyphens, and underscores)
        if (screenshotJobId.isBlank() || !screenshotJobId.matches(Regex("^[a-zA-Z0-9_-]{1,100}$"))) {
            throw ValidationException.InvalidFormat(
                "jobId",
                "must be alphanumeric with hyphens and underscores, max 100 characters"
            )
        }
        
        // Parse query parameters for pagination (following backend standard)
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        
        // Validate parameters (following backend standard)
        if (page < 1) {
            return call.respond(
                HttpStatusCode.BadRequest, 
                mapOf("error" to "Page must be greater than 0")
            )
        }
        
        if (limit < 1 || limit > 100) {
            return call.respond(
                HttpStatusCode.BadRequest, 
                mapOf("error" to "Limit must be between 1 and 100")
            )
        }
        
        // Get user ID from hybrid authentication (JWT OR API Key)
        val userId = call.requireHybridUserId()
        
        logger.debug(
            "Getting analyses for screenshot $screenshotJobId, user: $userId, " +
            "page: $page, limit: $limit"
        )
        
        // Execute use case
        val result = getScreenshotAnalysesUseCase(
            GetScreenshotAnalysesUseCase.Request(
                screenshotJobId = screenshotJobId,
                userId = userId,
                page = page,
                limit = limit
            )
        )
        
        call.respond(HttpStatusCode.OK, result.toDto())
    }

    private fun validateAnalysisType(analysisTypeStr: String): AnalysisType? {
        return try {
            AnalysisType.valueOf(analysisTypeStr)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun createValidationErrorResponse(
        field: String,
        value: String,
        validValues: List<String>
    ): Map<String, Any> {
        return mapOf(
            "error" to "Invalid $field: $value",
            "valid${field.replaceFirstChar { it.uppercase() }}s" to validValues
        )
    }

    private fun validatePaginationParams(page: Int, limit: Int): String? {
        return when {
            page < 1 -> "Page must be greater than 0"
            limit < 1 || limit > 100 -> "Limit must be between 1 and 100"
            else -> null
        }
    }

    private fun validateAnalysisStatus(statusStr: String): AnalysisStatus? {
        return try {
            AnalysisStatus.valueOf(statusStr.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Validate webhook URL format
     */
    private fun isValidWebhookUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            uri.scheme in listOf("http", "https") && uri.host != null
        } catch (e: Exception) {
            false
        }
    }
}