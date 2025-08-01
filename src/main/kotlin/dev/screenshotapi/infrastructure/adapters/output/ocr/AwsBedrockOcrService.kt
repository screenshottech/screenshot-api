package dev.screenshotapi.infrastructure.adapters.output.ocr

import aws.sdk.kotlin.runtime.auth.credentials.DefaultChainCredentialsProvider
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.OcrException
import dev.screenshotapi.core.domain.services.OcrService
import dev.screenshotapi.core.domain.services.OcrEngineCapabilities
import dev.screenshotapi.infrastructure.config.BedrockConfig
import dev.screenshotapi.infrastructure.config.BedrockFeatureFlags
import dev.screenshotapi.infrastructure.config.ValidationResult
import dev.screenshotapi.infrastructure.config.validateRegionModelCompatibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * AWS Bedrock OCR Service Implementation using Claude 3 Haiku Vision Model
 *
 * This service provides:
 * - AI-powered OCR and image analysis
 * - Multiple analysis types (BASIC_OCR, UX_ANALYSIS, CONTENT_SUMMARY, GENERAL)
 * - Sophisticated retry logic with exponential backoff
 * - Cost tracking and token usage monitoring
 * 
 * Note: Fallback support removed - if Bedrock fails, clear error messages are returned.
 * PaddleOCR classes preserved for potential future microservice integration.
 */
class AwsBedrockOcrService(
    private val config: BedrockConfig,
    private val featureFlags: BedrockFeatureFlags
) : OcrService {

    private val logger = LoggerFactory.getLogger(AwsBedrockOcrService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true   // Include default values like anthropic_version
        explicitNulls = false   // Don't include null fields in JSON
    }
    private val responseMapper = BedrockResponseMapper()

    // Lazy initialization of Bedrock client
    private val bedrockClient by lazy {
        val hasExplicitCredentials = config.aws.accessKeyId != null && config.aws.secretAccessKey != null
        logger.info("🔑 Bedrock credentials: ${if (hasExplicitCredentials) "Using explicit BEDROCK_AWS_* credentials" else "Using DefaultChainCredentialsProvider"}")
        if (hasExplicitCredentials) {
            logger.info("🔑 Access Key ID: ${config.aws.accessKeyId?.take(8)}...")
        }
        
        BedrockRuntimeClient {
            region = config.region
            credentialsProvider = if (hasExplicitCredentials) {
                // Use explicit Bedrock credentials
                StaticCredentialsProvider {
                    accessKeyId = config.aws.accessKeyId!!
                    secretAccessKey = config.aws.secretAccessKey!!
                    sessionToken = config.aws.sessionToken
                }
            } else {
                // Fallback to default chain only if no explicit credentials
                DefaultChainCredentialsProvider()
            }
        }
    }

    // Supported languages for Claude 3 models (much broader than PaddleOCR)
    private val supportedLanguages = listOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "ar", "hi",
        "th", "vi", "nl", "sv", "da", "no", "fi", "pl", "cs", "sk", "hu", "ro",
        "bg", "hr", "sl", "et", "lv", "lt", "mt", "ga", "cy", "is", "tr", "el",
        "he", "fa", "ur", "bn", "ta", "te", "ml", "kn", "gu", "pa", "or", "as", "ne"
    )

    init {
        logger.info("AWS Bedrock OCR service initialized with config:")
        logger.info("  - Region: ${config.region}")
        logger.info("  - Primary Model: ${config.models.primaryModel} (${config.models.claude3Haiku.modelId})")
        logger.info("  - Fallback Model: ${config.models.fallbackModel} (${config.models.claude3Sonnet.modelId})")
        logger.info("  - Max Tokens: ${config.models.claude3Haiku.maxTokens}")
        logger.info("  - Temperature: ${config.models.claude3Haiku.temperature}")
        logger.info("Feature flags: bedrockEnabled=${featureFlags.enableBedrockOcr}, aiAnalysis=${featureFlags.enableAiAnalysis}")
        logger.info("Operating in Bedrock-only mode (fallback disabled for reliability)")
        
        // Validate region-model compatibility
        val validationResult = validateRegionModelCompatibility(config.region, config.models.claude3Haiku.modelId)
        when (validationResult) {
            is ValidationResult.Success -> {
                logger.info("✅ Region-model compatibility validated: ${config.region} supports ${config.models.claude3Haiku.modelId}")
            }
            is ValidationResult.Warning -> {
                logger.warn("⚠️ Region compatibility warning: ${validationResult.message}")
            }
            is ValidationResult.Error -> {
                logger.error("❌ Region compatibility error: ${validationResult.message}")
            }
        }
    }

    override suspend fun extractText(request: OcrRequest): OcrResult {
        val startTime = System.currentTimeMillis()

        return try {
            logger.info("Starting Bedrock OCR extraction for request ${request.id}")
            logger.debug("Request details - analysisType=${request.analysisType}, language=${request.language}, tier=${request.tier}")

            // Validate if Bedrock is enabled
            if (!config.enabled || !featureFlags.enableBedrockOcr) {
                throw OcrException.ConfigurationException(
                    "Bedrock OCR is disabled. Enable BEDROCK_ENABLED and FEATURE_BEDROCK_OCR to use this service."
                )
            }

            // Validate language support
            if (!supportedLanguages.contains(request.language)) {
                throw OcrException.UnsupportedLanguageException(request.language, "AWS Bedrock Claude")
            }

            // Check if AI analysis is enabled for non-basic OCR
            val analysisType = request.analysisType ?: AnalysisType.BASIC_OCR
            if (analysisType.requiresAI && !featureFlags.enableAiAnalysis) {
                throw OcrException.ProcessingException(
                    engine = "AWS Bedrock",
                    message = "AI analysis features are currently disabled"
                )
            }

            // Process image with Bedrock
            val bedrockResponse = processWithBedrock(request, analysisType)

            val processingTime = (System.currentTimeMillis() - startTime) / 1000.0

            // Convert Bedrock response to domain model using mapper
            val mappedResult = responseMapper.mapBedrockResponse(
                bedrockResponse = bedrockResponse,
                analysisType = analysisType,
                processingTime = (System.currentTimeMillis() - startTime)
            )
            
            // Add screenshotJobId to metadata if present
            val updatedMetadata = if (request.screenshotJobId != null) {
                mappedResult.metadata + ("screenshotJobId" to request.screenshotJobId)
            } else {
                mappedResult.metadata
            }
            
            val result = mappedResult.copy(
                id = request.id,
                userId = request.userId,
                metadata = updatedMetadata
            )

            logger.info("✅ Bedrock OCR extraction completed for request ${request.id} in ${processingTime}s")
            logger.debug("Result summary - analysis: ${analysisType.name}, tokens: ${bedrockResponse.inputTokens}+${bedrockResponse.outputTokens}, wordCount: ${result.wordCount}")
            result

        } catch (e: OcrException) {
            logger.error("Bedrock OCR extraction failed for request ${request.id}: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during Bedrock OCR extraction for request ${request.id}", e)
            throw OcrException.ProcessingException(
                engine = "AWS Bedrock",
                message = "Unexpected error during OCR processing: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Process image using AWS Bedrock Claude 3 Haiku model
     */
    private suspend fun processWithBedrock(request: OcrRequest, analysisType: AnalysisType): BedrockResponse {
        return withContext(Dispatchers.IO) {
            // Retry logic with exponential backoff
            var lastException: Exception? = null
            val selectedModelId = config.models.claude3Haiku.modelId

            repeat(config.retry.maxAttempts) { attempt ->
                try {
                    logger.debug("Bedrock attempt ${attempt + 1}/${config.retry.maxAttempts} for request ${request.id}")
                    logger.debug("Using model: $selectedModelId in region: ${config.region}")

                    // Build Claude request
                    val claudeRequest = buildClaudeRequest(request, analysisType)

                    // Invoke Bedrock model
                    val requestBody = json.encodeToString(ClaudeRequest.serializer(), claudeRequest)
                    logger.debug("Request payload size: ${requestBody.length} chars, analysisType: ${analysisType.name}")
                    logger.debug("Request JSON payload: $requestBody")
                    
                    val response = bedrockClient.invokeModel(
                        InvokeModelRequest {
                            modelId = selectedModelId
                            body = requestBody.toByteArray()
                            contentType = "application/json"
                            accept = "application/json"
                        }
                    )

                    // Parse response
                    val responseBody = response.body.let { String(it) } ?: ""
                    val claudeResponse = json.decodeFromString<ClaudeResponse>(responseBody)

                    // Log token usage for cost tracking
                    logTokenUsage(claudeResponse, request.id)

                    return@withContext BedrockResponse(
                        content = claudeResponse.content.firstOrNull()?.text ?: "",
                        inputTokens = claudeResponse.usage.inputTokens,
                        outputTokens = claudeResponse.usage.outputTokens,
                        model = config.models.claude3Haiku.modelId
                    )

                } catch (e: Exception) {
                    lastException = e
                    val errorMessage = parseBedrockError(e, selectedModelId, config.region)
                    logger.warn("Bedrock attempt ${attempt + 1} failed for request ${request.id}: $errorMessage")

                    // Check if we should retry
                    if (attempt < config.retry.maxAttempts - 1 && isRetryableException(e)) {
                        val delayMs = calculateRetryDelay(attempt)
                        logger.info("Retrying in ${delayMs}ms...")
                        delay(delayMs)
                    }
                }
            }

            // All retries exhausted
            throw lastException ?: OcrException.ProcessingException(
                engine = "AWS Bedrock",
                message = "Failed to process with Bedrock after ${config.retry.maxAttempts} attempts"
            )
        }
    }

    /**
     * Build Claude request payload using data classes with explicit serialization
     */
    private fun buildClaudeRequest(request: OcrRequest, analysisType: AnalysisType): ClaudeRequest {
        // Encode image to base64
        val imageBytes = request.imageBytes ?: throw OcrException.InvalidImageException("No image data provided")
        val imageBase64 = Base64.getEncoder().encodeToString(imageBytes)
        
        // Detect media type from image header
        val mediaType = detectImageType(imageBytes)

        // Build messages with system and user prompts
        val messages = listOf(
            ClaudeMessage(
                role = "user",
                content = listOf(
                    ClaudeContent(
                        type = "image",
                        source = ClaudeImageSource(
                            type = "base64",
                            mediaType = mediaType,
                            data = imageBase64
                        )
                    ),
                    ClaudeContent(
                        type = "text",
                        text = buildLanguageAwarePrompt(analysisType.userPrompt, request.language)
                    )
                )
            )
        )

        return ClaudeRequest(
            anthropicVersion = "bedrock-2023-05-31",
            maxTokens = config.models.claude3Haiku.maxTokens,
            temperature = config.models.claude3Haiku.temperature,
            topP = config.models.claude3Haiku.topP,
            topK = config.models.claude3Haiku.topK,
            stopSequences = config.models.claude3Haiku.stopSequences,
            system = buildLanguageAwareSystemPrompt(analysisType.systemPrompt, request.language),
            messages = messages
        )
    }

    /**
     * Convert Bedrock response to OcrResult domain model
     */
    private fun convertToOcrResult(
        request: OcrRequest,
        bedrockResponse: BedrockResponse,
        processingTime: Double,
        analysisType: AnalysisType
    ): OcrResult {
        val extractedText = bedrockResponse.content

        // For basic OCR, try to extract structured data
        val lines = if (analysisType == AnalysisType.BASIC_OCR) {
            parseBasicOcrLines(extractedText)
        } else {
            // For AI analysis, treat as single block
            listOf(
                OcrTextLine(
                    text = extractedText,
                    confidence = 0.95, // High confidence for AI analysis
                    boundingBox = OcrBoundingBox(0, 0, 0, 0, 0, 0), // No bounding box for AI analysis
                    wordCount = extractedText.split("\\s+".toRegex()).size
                )
            )
        }

        val wordCount = lines.sumOf { it.wordCount }
        val averageConfidence = if (lines.isNotEmpty()) lines.map { it.confidence }.average() else 0.0

        return OcrResult(
            id = request.id,
            userId = request.userId,
            success = true,
            extractedText = extractedText,
            confidence = averageConfidence,
            wordCount = wordCount,
            lines = lines,
            processingTime = processingTime,
            language = request.language,
            engine = OcrEngine.CLAUDE_VISION,
            createdAt = Clock.System.now(),
            metadata = mapOf(
                "engine_version" to "Claude 3 Haiku",
                "model_id" to bedrockResponse.model,
                "analysis_type" to analysisType.name,
                "input_tokens" to bedrockResponse.inputTokens.toString(),
                "output_tokens" to bedrockResponse.outputTokens.toString(),
                "estimated_cost" to calculateEstimatedCost(bedrockResponse).toString()
            )
        )
    }

    /**
     * Parse basic OCR text into lines (simplified version)
     */
    private fun parseBasicOcrLines(text: String): List<OcrTextLine> {
        return text.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                OcrTextLine(
                    text = line.trim(),
                    confidence = 0.95, // Claude typically has high accuracy
                    boundingBox = OcrBoundingBox(
                        x1 = 0,
                        y1 = index * 20, // Estimate line height
                        x2 = line.length * 8, // Estimate character width
                        y2 = (index + 1) * 20,
                        width = line.length * 8,
                        height = 20
                    ),
                    wordCount = line.split("\\s+".toRegex()).size
                )
            }
    }

    /**
     * Calculate estimated cost based on token usage
     */
    private fun calculateEstimatedCost(response: BedrockResponse): Double {
        val inputCost = response.inputTokens * 0.00025 / 1000.0  // $0.25 per 1M tokens
        val outputCost = response.outputTokens * 0.00125 / 1000.0 // $1.25 per 1M tokens
        return inputCost + outputCost + 0.0004 // Add base image processing cost
    }

    /**
     * Log token usage for cost tracking and analytics
     */
    private fun logTokenUsage(response: ClaudeResponse, requestId: String) {
        val inputTokens = response.usage.inputTokens
        val outputTokens = response.usage.outputTokens
        val totalTokens = inputTokens + outputTokens
        
        // Calculate estimated cost
        val inputCost = inputTokens * 0.00025 / 1000.0
        val outputCost = outputTokens * 0.00125 / 1000.0
        val totalCost = inputCost + outputCost + 0.0004 // Base image cost
        
        logger.info("💰 Token usage for request $requestId:")
        logger.info("  - Input tokens: $inputTokens (\$${String.format("%.6f", inputCost)})")
        logger.info("  - Output tokens: $outputTokens (\$${String.format("%.6f", outputCost)})")
        logger.info("  - Total cost: \$${String.format("%.6f", totalCost)} (${totalTokens} tokens)")

        // TODO: Integrate with usage tracking system
        // This could be connected to existing UsageLog system for comprehensive analytics
    }

    /**
     * Check if exception is retryable
     */
    private fun isRetryableException(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return config.retry.retryableExceptions.any { message.contains(it.lowercase()) } ||
               exception is IOException ||
               message.contains("timeout") ||
               message.contains("throttling") ||
               message.contains("rate limit")
    }

    /**
     * Calculate retry delay with exponential backoff and jitter
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        val baseDelay = config.retry.baseDelayMs
        val maxDelay = config.retry.maxDelayMs
        val backoffMultiplier = config.retry.backoffMultiplier
        val jitterFactor = config.retry.jitterFactor

        val exponentialDelay = (baseDelay * backoffMultiplier.pow(attempt)).toLong()
        val delayWithCap = min(exponentialDelay, maxDelay)

        // Add jitter to prevent thundering herd
        val jitter = (delayWithCap * jitterFactor * Random.nextDouble()).toLong()
        return delayWithCap + jitter
    }

    // Note: Fallback methods removed - PaddleOCR classes preserved for potential future microservice use

    override suspend fun isEngineAvailable(engine: OcrEngine): Boolean {
        return when (engine) {
            OcrEngine.CLAUDE_VISION -> config.enabled && featureFlags.enableBedrockOcr
            else -> false  // Only Bedrock Claude Vision is available in this service
        }
    }

    override fun getRecommendedEngine(tier: OcrTier): OcrEngine {
        // Always recommend Claude Vision - this service only supports Bedrock
        return OcrEngine.CLAUDE_VISION
    }

    override fun getEngineCapabilities(engine: OcrEngine): OcrEngineCapabilities {
        return when (engine) {
            OcrEngine.CLAUDE_VISION -> OcrEngineCapabilities(
                engine = OcrEngine.CLAUDE_VISION,
                supportedLanguages = supportedLanguages,
                supportsStructuredData = true,
                supportsTables = true,
                supportsForms = true,
                supportsHandwriting = true,
                averageAccuracy = 0.95, // Claude has excellent accuracy
                averageProcessingTime = 8.0, // Much faster than PaddleOCR
                costPerRequest = 0.005, // Approximately $0.005 per request
                maxImageSize = 20 * 1024 * 1024, // 20MB
                isLocal = false, // Cloud-based
                requiresApiKey = true
            )
            else -> throw OcrException.ConfigurationException(
                "Engine ${engine.name} not supported by AWS Bedrock service. Only CLAUDE_VISION is available."
            )
        }
    }

    /**
     * Detect image type from byte array header
     */
    private fun detectImageType(imageBytes: ByteArray): String {
        return when {
            imageBytes.size >= 8 && 
            imageBytes[0] == 0x89.toByte() && imageBytes[1] == 0x50.toByte() && 
            imageBytes[2] == 0x4E.toByte() && imageBytes[3] == 0x47.toByte() -> "image/png"
            
            imageBytes.size >= 3 && 
            imageBytes[0] == 0xFF.toByte() && imageBytes[1] == 0xD8.toByte() && 
            imageBytes[2] == 0xFF.toByte() -> "image/jpeg"
            
            imageBytes.size >= 6 && 
            imageBytes[0] == 0x47.toByte() && imageBytes[1] == 0x49.toByte() && 
            imageBytes[2] == 0x46.toByte() -> "image/gif"
            
            imageBytes.size >= 12 && 
            imageBytes[8] == 0x57.toByte() && imageBytes[9] == 0x45.toByte() && 
            imageBytes[10] == 0x42.toByte() && imageBytes[11] == 0x50.toByte() -> "image/webp"
            
            else -> "image/png" // Default to PNG
        }
    }

    /**
     * Parse Bedrock exceptions to provide more informative error messages
     */
    private fun parseBedrockError(exception: Exception, modelId: String, region: String): String {
        val className = exception::class.simpleName
        val originalMessage = exception.message ?: "Unknown error"

        return when {
            className?.contains("AccessDeniedException") == true -> {
                buildString {
                    append("Access denied to model $modelId in region $region. ")
                    when {
                        region == "us-east-1" -> append("Claude 3 models are not available in us-east-1. Try us-east-2, us-west-2, or eu-west-1.")
                        originalMessage.contains("not authorized") -> append("Check your IAM permissions for Bedrock.")
                        originalMessage.contains("model") -> append("Model may not be available in this region.")
                        else -> append("Verify your AWS credentials and IAM permissions.")
                    }
                }
            }
            className?.contains("ValidationException") == true -> {
                "Invalid request format for model $modelId: $originalMessage"
            }
            className?.contains("ResourceNotFoundException") == true -> {
                "Model $modelId not found in region $region. Check model availability in this region."
            }
            className?.contains("ThrottlingException") == true -> {
                "Rate limited by Bedrock. Request will be retried automatically."
            }
            className?.contains("ServiceUnavailableException") == true -> {
                "Bedrock service temporarily unavailable in region $region. Request will be retried."
            }
            className?.contains("InternalServerException") == true -> {
                "Internal error in Bedrock service. Request will be retried automatically."
            }
            originalMessage.contains("timeout") || originalMessage.contains("timed out") -> {
                "Request timed out connecting to Bedrock in region $region. Check network connectivity."
            }
            else -> {
                "Bedrock error ($className): $originalMessage [Model: $modelId, Region: $region]"
            }
        }
    }

    /**
     * Build language-aware user prompt by injecting language instructions
     */
    private fun buildLanguageAwarePrompt(originalPrompt: String, language: String): String {
        return if (language != "en") {
            val languageInstruction = "Please respond in ${getLanguageDisplayName(language)}. "
            val enhancedPrompt = languageInstruction + originalPrompt
            logger.info("🌐 Language-aware prompt generated: language=$language (${getLanguageDisplayName(language)})")
            enhancedPrompt
        } else {
            originalPrompt
        }
    }

    /**
     * Build language-aware system prompt for better language adherence
     */
    private fun buildLanguageAwareSystemPrompt(originalSystemPrompt: String, language: String): String {
        return if (language != "en") {
            "$originalSystemPrompt Always respond in ${getLanguageDisplayName(language)}."
        } else {
            originalSystemPrompt
        }
    }

    /**
     * Get display name for language code
     */
    private fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "it" -> "Italian"
            "pt" -> "Portuguese"
            "ru" -> "Russian"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            "th" -> "Thai"
            "vi" -> "Vietnamese"
            "nl" -> "Dutch"
            "sv" -> "Swedish"
            "da" -> "Danish"
            "no" -> "Norwegian"
            "fi" -> "Finnish"
            "pl" -> "Polish"
            "en" -> "English"
            else -> "English" // Fallback to English for unknown codes
        }
    }
}

/**
 * Data classes for Claude API communication
 */
@Serializable
data class ClaudeRequest(
    @SerialName("anthropic_version") val anthropicVersion: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double,
    @SerialName("top_p") val topP: Double,
    @SerialName("top_k") val topK: Int,
    @SerialName("stop_sequences") val stopSequences: List<String>,
    val system: String,
    val messages: List<ClaudeMessage>
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: List<ClaudeContent>
)

@Serializable
data class ClaudeContent(
    val type: String,
    val text: String? = null,
    val source: ClaudeImageSource? = null
)

@Serializable
data class ClaudeImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String,
    val data: String
)

@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeResponseContent>,
    val model: String,
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("stop_sequence") val stopSequence: String? = null,
    val usage: ClaudeUsage
)

@Serializable
data class ClaudeResponseContent(
    val type: String,
    val text: String
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int
)

/**
 * Internal response wrapper
 */
data class BedrockResponse(
    val content: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val model: String
)
