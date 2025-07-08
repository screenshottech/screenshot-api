package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.OcrException
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyUseCase
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyOwnershipUseCase
import dev.screenshotapi.core.usecases.ocr.ExtractTextUseCase
import dev.screenshotapi.core.usecases.ocr.ExtractPriceDataUseCase
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.ocr.*
import dev.screenshotapi.infrastructure.auth.requireHybridUserId
import dev.screenshotapi.infrastructure.auth.getHybridApiKeyId
import dev.screenshotapi.core.domain.exceptions.AuthorizationException
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
    val extractTextUseCase by inject<ExtractTextUseCase>()
    val extractPriceDataUseCase by inject<ExtractPriceDataUseCase>()
    val ocrResultRepository by inject<OcrResultRepository>()
    val validateApiKeyUseCase by inject<ValidateApiKeyUseCase>()
    val validateApiKeyOwnershipUseCase by inject<ValidateApiKeyOwnershipUseCase>()

    routing {
        route("/api/v1/ocr") {
            
            /**
             * Extract text from image
             * POST /api/v1/ocr/extract
             */
            authenticate("jwt-auth", "api-key-auth") {
                post("/extract") {
                    try {
                        val request = call.receive<OcrExtractRequestDto>()
                        val userId = call.requireHybridUserId()
                        val apiKeyId = call.getHybridApiKeyId() ?: throw AuthorizationException.ApiKeyRequired()

                        // Validate API key ownership if provided in request
                        if (request.apiKeyId != null) {
                            val ownershipResult = validateApiKeyOwnershipUseCase(
                                ValidateApiKeyOwnershipUseCase.Request(
                                    apiKeyId = request.apiKeyId,
                                    userId = userId
                                )
                            )
                            if (!ownershipResult.isValid) {
                                return@post call.respond(
                                    HttpStatusCode.Forbidden,
                                    ErrorResponseDto.forbidden("API key ownership validation failed")
                                )
                            }
                        }

                        // Convert DTO to domain request
                        val ocrRequest = request.toDomainRequest(userId)

                        // Perform OCR extraction
                        val result = if (request.extractPrices) {
                            extractPriceDataUseCase.invoke(ocrRequest)
                        } else {
                            extractTextUseCase.invoke(ocrRequest)
                        }

                        logger.info(
                            "OCR extraction completed: userId={}, resultId={}, success={}, words={}",
                            userId, result.id, result.success, result.wordCount
                        )

                        call.respond(HttpStatusCode.OK, result.toDto())

                    } catch (e: OcrException.InsufficientCreditsException) {
                        logger.warn("Insufficient credits for OCR: ${e.message}")
                        call.respond(
                            HttpStatusCode.PaymentRequired,
                            ErrorResponseDto(
                                code = "INSUFFICIENT_CREDITS",
                                message = "Insufficient credits for OCR processing"
                            )
                        )
                    } catch (e: OcrException.InvalidImageException) {
                        logger.warn("Invalid image for OCR: ${e.message}")
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponseDto(
                                code = "INVALID_IMAGE",
                                message = "Invalid image format or content"
                            )
                        )
                    } catch (e: OcrException.UnsupportedLanguageException) {
                        logger.warn("Unsupported language for OCR: ${e.message}")
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponseDto(
                                code = "UNSUPPORTED_LANGUAGE",
                                message = "Unsupported language: ${e.message}"
                            )
                        )
                    } catch (e: OcrException) {
                        logger.error("OCR processing failed: ${e.message}", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponseDto(
                                code = "OCR_PROCESSING_FAILED",
                                message = "OCR processing failed: ${e.message}"
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("Unexpected error during OCR extraction", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponseDto.internal()
                        )
                    }
                }
            }

            /**
             * Get OCR result by ID
             * GET /api/v1/ocr/results/{resultId}
             */
            authenticate("jwt-auth", "api-key-auth") {
                get("/results/{resultId}") {
                    try {
                        val resultId = call.parameters["resultId"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponseDto.validation("Result ID is required")
                            )

                        val userId = call.requireHybridUserId()

                        val result = ocrResultRepository.findById(resultId)
                            ?: return@get call.respond(
                                HttpStatusCode.NotFound,
                                ErrorResponseDto.notFound("OCR result", resultId)
                            )

                        // Verify ownership through metadata
                        val resultUserId = result.metadata["userId"]
                        if (resultUserId != userId) {
                            return@get call.respond(
                                HttpStatusCode.Forbidden,
                                ErrorResponseDto.forbidden("Access denied to OCR result")
                            )
                        }

                        call.respond(HttpStatusCode.OK, result.toDto())

                    } catch (e: Exception) {
                        logger.error("Error retrieving OCR result", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponseDto.internal()
                        )
                    }
                }
            }

            /**
             * List OCR results for user
             * GET /api/v1/ocr/results
             */
            authenticate("jwt-auth", "api-key-auth") {
                get("/results") {
                    try {
                        val userId = call.requireHybridUserId()

                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

                        // Validate pagination parameters
                        if (offset < 0) {
                            return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponseDto.validation("Offset must be non-negative", "offset")
                            )
                        }
                        if (limit < 1 || limit > 100) {
                            return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponseDto.validation("Limit must be between 1 and 100", "limit")
                            )
                        }

                        val results = ocrResultRepository.findByUserId(
                            userId = userId,
                            offset = offset,
                            limit = limit
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

                    } catch (e: Exception) {
                        logger.error("Error listing OCR results", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponseDto.internal()
                        )
                    }
                }
            }

            /**
             * Get OCR analytics for user
             * GET /api/v1/ocr/analytics
             */
            authenticate("jwt-auth", "api-key-auth") {
                get("/analytics") {
                    try {
                        val userId = call.requireHybridUserId()

                        val fromDate = call.request.queryParameters["fromDate"]?.let {
                            try {
                                kotlinx.datetime.Instant.parse(it)
                            } catch (e: Exception) {
                                return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponseDto.validation("Invalid fromDate format. Use ISO 8601 format.", "fromDate")
                                )
                            }
                        } ?: Clock.System.now().minus(30.days)

                        val toDate = call.request.queryParameters["toDate"]?.let {
                            try {
                                kotlinx.datetime.Instant.parse(it)
                            } catch (e: Exception) {
                                return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponseDto.validation("Invalid toDate format. Use ISO 8601 format.", "toDate")
                                )
                            }
                        } ?: Clock.System.now()

                        val analytics = ocrResultRepository.getUserOcrAnalytics(
                            userId = userId,
                            fromDate = fromDate,
                            toDate = toDate
                        )

                        call.respond(HttpStatusCode.OK, analytics.toDto())

                    } catch (e: Exception) {
                        logger.error("Error retrieving OCR analytics", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponseDto.internal()
                        )
                    }
                }
            }

            /**
             * Get OCR engine capabilities
             * GET /api/v1/ocr/engines
             */
            get("/engines") {
                try {
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

                } catch (e: Exception) {
                    logger.error("Error retrieving OCR engines", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponseDto("Internal server error", "INTERNAL_ERROR")
                    )
                }
            }
        }
    }
}