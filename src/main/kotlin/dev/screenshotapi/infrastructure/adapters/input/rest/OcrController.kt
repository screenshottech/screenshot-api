package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.OcrException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyUseCase
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyOwnershipUseCase
import dev.screenshotapi.core.usecases.auth.ListApiKeysUseCase
import dev.screenshotapi.core.usecases.auth.ListApiKeysRequest
import dev.screenshotapi.core.usecases.ocr.CreateOcrJobUseCase
import dev.screenshotapi.core.usecases.ocr.GetOcrResultUseCase
import dev.screenshotapi.core.usecases.ocr.ListOcrResultsUseCase
import dev.screenshotapi.core.usecases.ocr.GetOcrAnalyticsUseCase
import dev.screenshotapi.core.usecases.ocr.ExtractTextUseCase
import dev.screenshotapi.core.usecases.ocr.ExtractPriceDataUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.ocr.*
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.TakeScreenshotResponseDto
import dev.screenshotapi.infrastructure.auth.requireHybridUserId
import dev.screenshotapi.infrastructure.auth.getHybridApiKeyId
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
import dev.screenshotapi.infrastructure.auth.AuthCombinations
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.*

/**
 * OCR Controller - REST endpoints for OCR operations
 * GitHub Issue #5: Create OCR API endpoints and documentation
 */
fun Application.configureOcrRoutes() {
    val logger = LoggerFactory.getLogger("OcrController")

    // Inject dependencies
    val createOcrJobUseCase by inject<CreateOcrJobUseCase>()
    val getOcrResultUseCase by inject<GetOcrResultUseCase>()
    val listOcrResultsUseCase by inject<ListOcrResultsUseCase>()
    val getOcrAnalyticsUseCase by inject<GetOcrAnalyticsUseCase>()
    val extractTextUseCase by inject<ExtractTextUseCase>()
    val extractPriceDataUseCase by inject<ExtractPriceDataUseCase>()
    val validateApiKeyUseCase by inject<ValidateApiKeyUseCase>()
    val validateApiKeyOwnershipUseCase by inject<ValidateApiKeyOwnershipUseCase>()
    val listApiKeysUseCase by inject<ListApiKeysUseCase>()
    val logUsageUseCase by inject<LogUsageUseCase>()

    routing {
        route("/api/v1/ocr") {

            /**
             * Extract text from image
             * POST /api/v1/ocr/extract
             */
            authenticate(*AuthCombinations.OPERATIONS) {
                post("/extract") {
                    val request = call.receive<OcrExtractRequestDto>()
                    val userId = call.requireHybridUserId()
                    
                    // DEBUG: Log the extracted userId
                    logger.info("OCR Extract: userId extracted = '$userId'")
                    
                    // Resolve API key (same logic as ScreenshotController)
                    val providedApiKeyId = call.getHybridApiKeyId()
                    val apiKeyId = providedApiKeyId ?: run {
                        // Auto-select an active API key for the user
                        val response = listApiKeysUseCase(ListApiKeysRequest(userId))
                        val activeApiKeys = response.apiKeys.filter { it.isActive }
                        
                        // Prefer default API key, fallback to first active
                        val defaultApiKey = activeApiKeys.find { it.isDefault }
                        defaultApiKey?.id ?: activeApiKeys.firstOrNull()?.id
                    } ?: throw AuthorizationException.ApiKeyRequired()

                    // If we auto-selected an API key, log the usage
                    if (providedApiKeyId == null) {
                        logUsageUseCase.invoke(LogUsageUseCase.Request(
                            userId = userId,
                            action = UsageLogAction.API_KEY_USED,
                            apiKeyId = apiKeyId,
                            metadata = mapOf("context" to "ocr_auto_selected")
                        ))
                    }

                    // Validate API key ownership if provided in request
                    if (request.apiKeyId != null) {
                        val ownershipResult = validateApiKeyOwnershipUseCase(
                            ValidateApiKeyOwnershipUseCase.Request(
                                apiKeyId = request.apiKeyId,
                                userId = userId
                            )
                        )
                        if (!ownershipResult.isValid) {
                            throw AuthorizationException.ApiKeyNotOwned()
                        }
                    }

                    // Convert DTO to domain request (screenshotJobId will be set in use case)
                    val ocrRequest = request.toDomainRequest(userId)

                    // Create request metadata for better traceability
                    val requestMetadata = mapOf(
                        "imageData" to request.imageData,
                        "tier" to request.tier,
                        "extractPrices" to request.extractPrices.toString(),
                        "extractTables" to request.extractTables.toString(),
                        "extractForms" to request.extractForms.toString(),
                        "confidenceThreshold" to request.confidenceThreshold.toString()
                    )

                    // Use the CreateOcrJobUseCase to handle all business logic
                    val result = createOcrJobUseCase(
                        CreateOcrJobUseCase.Request(
                            userId = userId,
                            apiKeyId = apiKeyId,
                            ocrRequest = ocrRequest,
                            requestMetadata = requestMetadata
                        )
                    )

                    // Return job response (same format as screenshots for consistency)
                    call.respond(HttpStatusCode.Accepted, TakeScreenshotResponseDto(
                        jobId = result.jobId,
                        status = result.status,
                        estimatedCompletion = result.estimatedCompletion,
                        queuePosition = result.queuePosition
                    ))
                }
            }

            /**
             * Get OCR result by ID
             * GET /api/v1/ocr/results/{resultId}
             */
            authenticate(*AuthCombinations.OPERATIONS) {
                get("/results/{resultId}") {
                    val resultId = call.parameters["resultId"]
                        ?: throw ValidationException.Required("resultId")

                    val userId = call.requireHybridUserId()

                    val result = getOcrResultUseCase(
                        GetOcrResultUseCase.Request(
                            resultId = resultId,
                            userId = userId
                        )
                    )

                    call.respond(HttpStatusCode.OK, result.toDto())
                }
            }

            /**
             * List OCR results for user
             * GET /api/v1/ocr/results
             */
            authenticate(*AuthCombinations.OPERATIONS) {
                get("/results") {
                    val userId = call.requireHybridUserId()

                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

                    // Validate pagination parameters
                    if (offset < 0) {
                        throw ValidationException.NonNegative("offset")
                    }
                    if (limit < 1 || limit > 100) {
                        throw ValidationException.InvalidRange("limit", min = 1, max = 100)
                    }

                    val results = listOcrResultsUseCase(
                        ListOcrResultsUseCase.Request(
                            userId = userId,
                            offset = offset,
                            limit = limit
                        )
                    )

                    val response = OcrResultListResponseDto(
                        results = results.map { it.toDto() },
                        pagination = PaginationDto(
                            offset = offset,
                            limit = limit,
                            total = results.size,
                            hasMore = results.size == limit
                        )
                    )

                    call.respond(HttpStatusCode.OK, response)
                }
            }

            /**
             * Get OCR analytics for user
             * GET /api/v1/ocr/analytics
             */
            authenticate(*AuthCombinations.OPERATIONS) {
                get("/analytics") {
                    val userId = call.requireHybridUserId()

                    val fromDate = call.request.queryParameters["fromDate"]?.let {
                        try {
                            kotlinx.datetime.Instant.parse(it)
                        } catch (e: Exception) {
                            throw ValidationException.InvalidFormat("fromDate", "Use ISO 8601 format")
                        }
                    } ?: Clock.System.now().minus(30.days)

                    val toDate = call.request.queryParameters["toDate"]?.let {
                        try {
                            kotlinx.datetime.Instant.parse(it)
                        } catch (e: Exception) {
                            throw ValidationException.InvalidFormat("toDate", "Use ISO 8601 format")
                        }
                    } ?: Clock.System.now()

                    val analytics = getOcrAnalyticsUseCase(
                        GetOcrAnalyticsUseCase.Request(
                            userId = userId,
                            fromDate = fromDate,
                            toDate = toDate
                        )
                    )

                    call.respond(HttpStatusCode.OK, analytics.toDto())
                }
            }

            /**
             * Get OCR engine capabilities
             * GET /api/v1/ocr/engines
             */
            get("/engines") {
                val engines = OcrEngine.values().map { engine ->
                    OcrEngineInfoDto(
                        engine = engine.name,
                        displayName = engine.getDisplayName(),
                        description = engine.getDescription(),
                        supportedLanguages = engine.getSupportedLanguages(),
                        supportedTiers = engine.getSupportedTiers(),
                        capabilities = OcrCapabilitiesDto(
                            supportsStructuredData = engine.supportsStructuredData(),
                            supportsTables = engine.supportsTables(),
                            supportsForms = engine.supportsForms(),
                            supportsHandwriting = engine.supportsHandwriting(),
                            isLocal = engine.isLocal(),
                            requiresApiKey = engine.requiresApiKey()
                        ),
                        averageAccuracy = engine.getAverageAccuracy(),
                        averageProcessingTime = engine.getAverageProcessingTime(),
                        maxImageSize = engine.getMaxImageSize()
                    )
                }

                call.respond(HttpStatusCode.OK, OcrEnginesResponseDto(engines))
            }
        }
    }
}
