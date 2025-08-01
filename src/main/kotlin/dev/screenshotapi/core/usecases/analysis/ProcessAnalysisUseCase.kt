package dev.screenshotapi.core.usecases.analysis

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import dev.screenshotapi.core.domain.services.OcrService
import dev.screenshotapi.core.domain.exceptions.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import dev.screenshotapi.infrastructure.utils.JsonSerializationUtils
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Process Analysis Use Case - Executes AI analysis on screenshot images
 *
 * This use case handles:
 * - Downloading screenshot images
 * - Processing with AWS Bedrock via OcrService
 * - Parsing and storing results
 * - Cost tracking and token usage
 * - Error handling and recovery
 */
class ProcessAnalysisUseCase(
    private val analysisJobRepository: AnalysisJobRepository,
    private val ocrResultRepository: OcrResultRepository,
    private val ocrService: OcrService,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(ProcessAnalysisUseCase::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(request: Request): Response {
        logger.info("Processing analysis job ${request.analysisJobId}")

        // 1. Get analysis job
        val analysisJob = analysisJobRepository.findByIdAndUserId(request.analysisJobId, request.userId)
            ?: throw AnalysisException.JobNotFoundError(
                "Analysis job not found: ${request.analysisJobId}",
                request.analysisJobId
            )

        if (analysisJob.status != AnalysisStatus.PROCESSING) {
            throw AnalysisException.InvalidJobStatusError(
                "Analysis job must be in PROCESSING status",
                analysisJob.id,
                analysisJob.analysisType,
                analysisJob.status.name,
                AnalysisStatus.PROCESSING.name
            )
        }

        try {
            // 2. Download image from screenshot URL
            logger.debug("Downloading image from ${analysisJob.screenshotUrl}")
            val imageBytes = downloadImage(analysisJob.screenshotUrl)

            // 3. Create OCR request with analysis type configuration
            val ocrRequest = createOcrRequest(analysisJob, imageBytes)

            // 4. Process with OCR service (AWS Bedrock)
            logger.info("Processing with OCR service: ${analysisJob.analysisType.displayName}")
            val ocrResult = ocrService.extractText(ocrRequest)

            // 5. Save OCR result for audit trail
            val savedOcrResult = ocrResultRepository.save(ocrResult)

            // 6. Parse and format results based on analysis type
            val formattedResults = formatAnalysisResults(analysisJob.analysisType, ocrResult)

            // 7. Calculate costs
            val costUsd = calculateCost(ocrResult)

            // 8. Update analysis job with results
            val completedJob = analysisJob.copy(
                status = AnalysisStatus.COMPLETED,
                resultData = formattedResults,
                confidence = ocrResult.confidence,
                processingTimeMs = (ocrResult.processingTime * 1000).toLong(),
                tokensUsed = extractTokenCount(ocrResult),
                costUsd = costUsd,
                completedAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                metadata = analysisJob.metadata + mapOf(
                    "ocrResultId" to savedOcrResult.id,
                    "engine" to ocrResult.engine.name,
                    "modelUsed" to (ocrResult.metadata["model"] ?: "unknown")
                )
            )

            analysisJobRepository.save(completedJob)

            logger.info(
                "Analysis completed successfully: jobId=${analysisJob.id}, " +
                "confidence=${ocrResult.confidence}, " +
                "tokensUsed=${completedJob.tokensUsed}, " +
                "costUsd=$costUsd"
            )

            val analysisData = parseAnalysisData(completedJob.analysisType, completedJob.resultData!!)

            return Response(
                analysisJobId = completedJob.id,
                result = AnalysisResult.Success(
                    jobId = completedJob.id,
                    analysisType = completedJob.analysisType,
                    processingTimeMs = completedJob.processingTimeMs!!,
                    tokensUsed = completedJob.tokensUsed!!,
                    costUsd = completedJob.costUsd!!,
                    timestamp = completedJob.completedAt!!,
                    data = analysisData,
                    confidence = completedJob.confidence!!,
                    metadata = completedJob.metadata
                )
            )

        } catch (e: AnalysisException) {
            logger.error("Analysis exception for job ${analysisJob.id}", e)
            val failedJob = handleAnalysisFailure(analysisJob, e.message ?: "Analysis failed")

            return Response(
                analysisJobId = failedJob.id,
                result = AnalysisResult.Failure(
                    jobId = failedJob.id,
                    analysisType = failedJob.analysisType,
                    processingTimeMs = failedJob.processingTimeMs ?: 0L,
                    tokensUsed = 0,
                    costUsd = 0.0,
                    timestamp = failedJob.completedAt!!,
                    error = AnalysisError(
                        code = e::class.simpleName.orEmpty(),
                        message = e.message ?: "Analysis failed",
                        category = e.errorCategory,
                        details = buildMap {
                            put("exceptionType", e::class.simpleName.orEmpty())
                            e.analysisJobId?.let { put("analysisJobId", it) }
                            e.analysisType?.let { put("analysisType", it.name) }
                            if (e is AnalysisException.RateLimitExceeded) {
                                e.retryAfterSeconds?.let { put("retryAfterSeconds", it.toString()) }
                            }
                            if (e is AnalysisException.ExternalServiceError) {
                                put("serviceName", e.serviceName)
                            }
                        }
                    ),
                    retryable = e.retryable
                )
            )
        } catch (e: OcrException) {
            logger.error("OCR processing failed for analysis ${analysisJob.id}", e)
            val failedJob = handleAnalysisFailure(analysisJob, "OCR processing failed: ${e.message}")

            return Response(
                analysisJobId = failedJob.id,
                result = AnalysisResult.Failure(
                    jobId = failedJob.id,
                    analysisType = failedJob.analysisType,
                    processingTimeMs = failedJob.processingTimeMs ?: 0L,
                    tokensUsed = 0,
                    costUsd = 0.0,
                    timestamp = failedJob.completedAt!!,
                    error = AnalysisError(
                        code = "OCR_PROCESSING_FAILED",
                        message = e.message ?: "OCR processing failed",
                        category = ErrorCategory.PROCESSING,
                        details = mapOf("exceptionType" to e::class.simpleName.orEmpty())
                    ),
                    retryable = true
                )
            )
        } catch (e: Exception) {
            logger.error("Unexpected error processing analysis ${analysisJob.id}", e)
            val failedJob = handleAnalysisFailure(analysisJob, "Analysis failed: ${e.message}")

            return Response(
                analysisJobId = failedJob.id,
                result = AnalysisResult.Failure(
                    jobId = failedJob.id,
                    analysisType = failedJob.analysisType,
                    processingTimeMs = failedJob.processingTimeMs ?: 0L,
                    tokensUsed = 0,
                    costUsd = 0.0,
                    timestamp = failedJob.completedAt!!,
                    error = AnalysisError(
                        code = "UNEXPECTED_ERROR",
                        message = e.message ?: "Unexpected error occurred",
                        category = ErrorCategory.UNKNOWN,
                        details = mapOf("exceptionType" to e::class.simpleName.orEmpty())
                    ),
                    retryable = false
                )
            )
        }
    }

    private suspend fun downloadImage(url: String): ByteArray = withContext(Dispatchers.IO) {
        try {
            httpClient.get(url).bodyAsBytes()
        } catch (e: Exception) {
            logger.error("Failed to download image from $url", e)
            throw AnalysisException.ImageDownloadError(
                "Failed to download screenshot image: ${e.message}",
                cause = e,
                imageUrl = url
            )
        }
    }

    private fun createOcrRequest(job: AnalysisJob, imageBytes: ByteArray): OcrRequest {
        // For custom analysis, combine system and user prompts
        val customPrompt = if (job.analysisType == AnalysisType.CUSTOM) {
            buildCustomPrompt(job)
        } else {
            null
        }

        return OcrRequest(
            id = UUID.randomUUID().toString(),
            userId = job.userId,
            screenshotJobId = job.screenshotJobId,
            imageBytes = imageBytes,
            language = job.language,
            tier = mapAnalysisTypeToTier(job.analysisType),
            engine = OcrEngine.CLAUDE_VISION,
            useCase = mapAnalysisTypeToUseCase(job.analysisType),
            analysisType = job.analysisType,
            options = OcrOptions(
                extractPrices = false,
                extractTables = false,
                extractForms = false,
                confidenceThreshold = 0.7,
                enableStructuredData = false,
                customPrompt = customPrompt
            )
        )
    }

    /**
     * Builds custom prompt combining system and user prompts for CUSTOM analysis
     */
    private fun buildCustomPrompt(job: AnalysisJob): String? {
        if (job.analysisType != AnalysisType.CUSTOM) return null

        val systemPrompt = job.getEffectiveSystemPrompt()
        val userPrompt = job.getEffectiveUserPrompt()

        // If no custom prompts provided, return null to use default
        if (systemPrompt.isBlank() && userPrompt.isBlank()) {
            return null
        }

        // Build combined prompt with clear structure
        val promptBuilder = StringBuilder()

        if (systemPrompt.isNotBlank()) {
            promptBuilder.append("System Instructions: ")
            promptBuilder.append(systemPrompt)
            promptBuilder.append("\n\n")
        }

        if (userPrompt.isNotBlank()) {
            promptBuilder.append("Analysis Request: ")
            promptBuilder.append(userPrompt)
        }

        return promptBuilder.toString().trim()
    }

    private fun mapAnalysisTypeToTier(analysisType: AnalysisType): OcrTier {
        return when (analysisType) {
            AnalysisType.BASIC_OCR -> OcrTier.BASIC
            AnalysisType.UX_ANALYSIS -> OcrTier.AI_PREMIUM
            AnalysisType.CONTENT_SUMMARY -> OcrTier.AI_STANDARD
            AnalysisType.GENERAL -> OcrTier.AI_STANDARD
            AnalysisType.CUSTOM -> OcrTier.AI_PREMIUM
        }
    }

    private fun mapAnalysisTypeToUseCase(analysisType: AnalysisType): OcrUseCase {
        return when (analysisType) {
            AnalysisType.BASIC_OCR -> OcrUseCase.GENERAL
            AnalysisType.UX_ANALYSIS -> OcrUseCase.GENERAL
            AnalysisType.CONTENT_SUMMARY -> OcrUseCase.GENERAL
            AnalysisType.GENERAL -> OcrUseCase.GENERAL
            AnalysisType.CUSTOM -> OcrUseCase.GENERAL
        }
    }

    private fun formatAnalysisResults(analysisType: AnalysisType, ocrResult: OcrResult): String {
        // For now, return the extracted text as JSON
        // In the future, we can add more sophisticated formatting based on analysis type
        val resultMap = mutableMapOf<String, Any>(
            "extractedText" to ocrResult.extractedText,
            "confidence" to ocrResult.confidence,
            "wordCount" to ocrResult.wordCount,
            "language" to ocrResult.language,
            "engine" to ocrResult.engine.name
        )

        // Add structured data if available
        ocrResult.structuredData?.let { structured ->
            if (structured.prices.isNotEmpty()) {
                resultMap["prices"] = structured.prices.map { price ->
                    mapOf(
                        "value" to price.value,
                        "numericValue" to price.numericValue,
                        "currency" to price.currency,
                        "confidence" to price.confidence
                    )
                }
            }
        }

        // Add metadata if available
        if (ocrResult.metadata.isNotEmpty()) {
            resultMap["metadata"] = ocrResult.metadata
        }

        // Use centralized JSON serialization utility
        return JsonSerializationUtils.mapToJsonString(resultMap)
    }

    private fun extractTokenCount(ocrResult: OcrResult): Int {
        // Extract token count from metadata if available
        return ocrResult.metadata["tokensUsed"]?.toIntOrNull() ?: estimateTokens(ocrResult.extractedText)
    }

    private fun estimateTokens(text: String): Int {
        // Simple estimation: ~4 characters per token
        return (text.length / 4).coerceAtLeast(1)
    }

    private fun calculateCost(ocrResult: OcrResult): Double {
        // Extract cost from metadata if available
        ocrResult.metadata["costUsd"]?.toDoubleOrNull()?.let { return it }

        // Otherwise estimate based on tokens
        val tokens = extractTokenCount(ocrResult)
        val costPerToken = 0.00025 // Default Claude 3 Haiku input cost
        return tokens * costPerToken
    }

    private suspend fun handleAnalysisFailure(job: AnalysisJob, errorMessage: String): AnalysisJob {
        return try {
            val failedJob = job.copy(
                status = AnalysisStatus.FAILED,
                errorMessage = errorMessage,
                completedAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            analysisJobRepository.save(failedJob)
        } catch (e: Exception) {
            logger.error("Failed to update analysis job status to FAILED", e)
            job.copy(
                status = AnalysisStatus.FAILED,
                errorMessage = errorMessage,
                completedAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
        }
    }

    private fun parseAnalysisData(analysisType: AnalysisType, resultData: String): AnalysisData {
        return try {
            val jsonResult = json.parseToJsonElement(resultData) as JsonObject

            when (analysisType) {
                AnalysisType.BASIC_OCR -> {
                    val extractedText = jsonResult["extractedText"]?.jsonPrimitive?.content ?: ""
                    val language = jsonResult["language"]?.jsonPrimitive?.content ?: "en"

                    AnalysisData.OcrData(
                        text = extractedText,
                        lines = emptyList(), // TODO: Parse lines from result
                        language = language,
                        structuredData = emptyMap()
                    )
                }
                AnalysisType.UX_ANALYSIS -> {
                    AnalysisData.UxAnalysisData(
                        accessibilityScore = 0.0,
                        issues = emptyList(),
                        recommendations = emptyList(),
                        colorContrast = emptyMap(),
                        readabilityScore = 0.0
                    )
                }
                AnalysisType.CONTENT_SUMMARY -> {
                    AnalysisData.ContentSummaryData(
                        summary = "",
                        keyPoints = emptyList(),
                        entities = emptyList(),
                        sentiment = SentimentAnalysis("neutral", 0.0),
                        topics = emptyList()
                    )
                }
                AnalysisType.GENERAL -> {
                    AnalysisData.GeneralData(
                        results = emptyMap()
                    )
                }
                AnalysisType.CUSTOM -> {
                    // Custom analysis returns general data format
                    AnalysisData.GeneralData(
                        results = mapOf("customAnalysis" to resultData)
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse analysis data, returning general data", e)
            AnalysisData.GeneralData(
                results = mapOf("rawData" to resultData, "parseError" to e.message.orEmpty())
            )
        }
    }

    data class Request(
        val analysisJobId: String,
        val userId: String
    )

    data class Response(
        val analysisJobId: String,
        val result: AnalysisResult
    )
}
