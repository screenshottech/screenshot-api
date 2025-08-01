package dev.screenshotapi.infrastructure.adapters.output.ocr

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.OcrException
import dev.screenshotapi.core.domain.services.OcrService
import dev.screenshotapi.core.domain.services.OcrEngineCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * PaddleOCR Service Implementation
 * GitHub Issue #3: Implement PaddleOCR service layer with ProcessBuilder
 */
class PaddleOcrService(
    private val pythonPath: String = "python3",
    private val paddleOcrPath: String = "/app/ocr/paddleocr",
    private val workingDirectory: String = "/Users/luiscarbonel/Desktop/dev/git/dev-screenshot/screenshot-api/tmp/ocr",
    private val timeoutSeconds: Long = 30
) : OcrService {

    private val logger = LoggerFactory.getLogger(PaddleOcrService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // Supported languages by PaddleOCR v3.1.0
    private val supportedLanguages = listOf(
        "en", "ch", "ta", "te", "ka", "ja", "ko", "hi", "ar", "fr", "es", "pt", "de", "it", "ru"
    )

    init {
        // Ensure working directory exists
        File(workingDirectory).mkdirs()
        logger.info("PaddleOCR service initialized with working directory: $workingDirectory")
    }

    override suspend fun extractText(request: OcrRequest): OcrResult {
        val startTime = System.currentTimeMillis()

        return try {
            logger.info("Starting PaddleOCR extraction for request ${request.id}")

            // Validate language support
            if (!supportedLanguages.contains(request.language)) {
                throw OcrException.UnsupportedLanguageException(request.language, "PaddleOCR")
            }

            // Process image with PaddleOCR
            val tempImageFile = saveImageToTempFile(request.imageBytes ?: throw OcrException.InvalidImageException("No image data provided"), request.id)
            val ocrResults = runPaddleOcrProcess(tempImageFile, request)

            // Clean up temp file
            tempImageFile.delete()

            val processingTime = (System.currentTimeMillis() - startTime) / 1000.0

            // Convert PaddleOCR results to domain model
            val result = convertToOcrResult(
                request = request,
                paddleResults = ocrResults,
                processingTime = processingTime
            )

            logger.info("PaddleOCR extraction completed for request ${request.id} in ${processingTime}s")
            result

        } catch (e: OcrException) {
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during PaddleOCR extraction for request ${request.id}", e)
            throw OcrException.ProcessingException(
                engine = "PaddleOCR",
                message = "Unexpected error during OCR processing: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun isEngineAvailable(engine: OcrEngine): Boolean {
        return when (engine) {
            OcrEngine.PADDLE_OCR -> checkPaddleOcrAvailability()
            else -> false
        }
    }

    override fun getRecommendedEngine(tier: OcrTier): OcrEngine {
        return when (tier) {
            OcrTier.BASIC -> OcrEngine.PADDLE_OCR
            OcrTier.LOCAL_AI -> OcrEngine.PADDLE_OCR // Will be enhanced with local AI models
            else -> OcrEngine.PADDLE_OCR // Fallback for higher tiers
        }
    }

    override fun getEngineCapabilities(engine: OcrEngine): OcrEngineCapabilities {
        return when (engine) {
            OcrEngine.PADDLE_OCR -> OcrEngineCapabilities(
                engine = OcrEngine.PADDLE_OCR,
                supportedLanguages = supportedLanguages,
                supportsStructuredData = false, // Basic text extraction only
                supportsTables = false,
                supportsForms = false,
                supportsHandwriting = false,
                averageAccuracy = 0.92, // 92% accuracy from research
                averageProcessingTime = 2.5, // seconds
                costPerRequest = 0.0, // Free to use
                maxImageSize = 10 * 1024 * 1024, // 10MB
                isLocal = true,
                requiresApiKey = false
            )
            else -> throw OcrException.ConfigurationException("Engine capabilities not available for ${engine.name}")
        }
    }

    private suspend fun checkPaddleOcrAvailability(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder(pythonPath, "-c", "import paddleocr; print('available')")
                    .directory(File(workingDirectory))
                    .start()

                val available = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
                logger.debug("PaddleOCR availability check: $available")
                available
            } catch (e: Exception) {
                logger.warn("PaddleOCR availability check failed", e)
                false
            }
        }
    }

    private suspend fun saveImageToTempFile(imageData: ByteArray, requestId: String): File {
        return withContext(Dispatchers.IO) {
            val tempFile = File(workingDirectory, "ocr_input_${requestId}_${UUID.randomUUID()}.png")
            try {
                Files.copy(imageData.inputStream(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                logger.debug("Saved image to temp file: ${tempFile.absolutePath}")
                tempFile
            } catch (e: IOException) {
                throw OcrException.ProcessingException(
                    engine = "PaddleOCR",
                    message = "Failed to save image to temp file",
                    cause = e
                )
            }
        }
    }

    private suspend fun runPaddleOcrProcess(imageFile: File, request: OcrRequest): String {
        // Get retry attempts from config
        val maxRetries = 3 // Default to 3 retries
        var currentRetry = 0
        var lastException: Exception? = null

        // Memory optimization levels for retries
        val memoryOptimizationLevels = listOf(
            MemoryOptimizationLevel.NORMAL,
            MemoryOptimizationLevel.REDUCED,
            MemoryOptimizationLevel.MINIMAL
        )

        while (currentRetry < maxRetries) {
            try {
                // Select memory optimization level based on retry count
                val optimizationLevel = memoryOptimizationLevels[minOf(currentRetry, memoryOptimizationLevels.size - 1)]

                // Execute OCR process with current optimization level
                return executeOcrProcess(imageFile, request, optimizationLevel)
            } catch (e: OcrException.ProcessingException) {
                // Only retry for segmentation fault (exit code 139)
                if (e.message?.contains("segmentation fault (exit code 139)") == true) {
                    lastException = e
                    currentRetry++

                    if (currentRetry < maxRetries) {
                        logger.warn("Segmentation fault detected (attempt $currentRetry/$maxRetries). " +
                                "Retrying with reduced memory settings...")

                        // Force garbage collection between retries
                        System.gc()

                        // Wait a bit before retrying
                        delay(1.seconds)
                    } else {
                        logger.error("Maximum retry attempts reached for segmentation fault")
                        throw e
                    }
                } else {
                    // For other processing exceptions, don't retry
                    throw e
                }
            } catch (e: Exception) {
                // Don't retry for other exceptions
                throw e
            }
        }

        // If we've exhausted all retries, throw the last exception
        throw lastException ?: OcrException.ProcessingException(
            engine = "PaddleOCR",
            message = "Failed to process OCR after $maxRetries attempts"
        )
    }

    /**
     * Memory optimization levels for PaddleOCR
     */
    private enum class MemoryOptimizationLevel {
        NORMAL,    // Default settings
        REDUCED,   // Reduced memory usage
        MINIMAL    // Minimal memory usage
    }

    /**
     * Execute OCR process with specified memory optimization level
     */
    private suspend fun executeOcrProcess(
        imageFile: File,
        request: OcrRequest,
        optimizationLevel: MemoryOptimizationLevel
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // Build PaddleOCR command with memory optimization
                val command = buildPaddleOcrCommand(imageFile, request, optimizationLevel)

                logger.info("Executing PaddleOCR command with ${optimizationLevel.name} memory optimization: ${command.joinToString(" ")}")

                val process = ProcessBuilder(command)
                    .directory(File(workingDirectory))
                    .redirectErrorStream(false) // Separate error stream for better debugging
                    .start()

                // Wait for process completion with timeout
                val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

                if (!completed) {
                    process.destroyForcibly()
                    throw OcrException.TimeoutException(
                        processingTime = timeoutSeconds,
                        maxTime = timeoutSeconds
                    )
                }

                val exitCode = process.exitValue()
                val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
                val errorOutput = process.errorStream.readBytes().toString(Charsets.UTF_8)

                if (exitCode != 0) {
                    logger.error("PaddleOCR process failed with exit code $exitCode")
                    logger.error("STDOUT: $output")
                    logger.error("STDERR: $errorOutput")

                    // Check if it's a segmentation fault (exit code 139)
                    if (exitCode == 139) {
                        throw OcrException.ProcessingException(
                            engine = "PaddleOCR",
                            message = "PaddleOCR segmentation fault (exit code 139). This usually indicates a memory or compatibility issue."
                        )
                    }

                    throw OcrException.ProcessingException(
                        engine = "PaddleOCR",
                        message = "PaddleOCR process failed with exit code $exitCode. Error: $errorOutput"
                    )
                }

                logger.info("PaddleOCR process completed successfully")
                logger.debug("OCR output: ${output.take(200)}...")
                output

            } catch (e: IOException) {
                logger.error("Failed to execute PaddleOCR process", e)
                throw OcrException.ProcessingException(
                    engine = "PaddleOCR",
                    message = "Failed to execute PaddleOCR process: ${e.message}",
                    cause = e
                )
            } catch (e: InterruptedException) {
                logger.error("PaddleOCR process was interrupted", e)
                throw OcrException.ProcessingException(
                    engine = "PaddleOCR",
                    message = "PaddleOCR process was interrupted: ${e.message}",
                    cause = e
                )
            }
        }
    }

    private fun buildPaddleOcrCommand(
        imageFile: File,
        request: OcrRequest,
        optimizationLevel: MemoryOptimizationLevel = MemoryOptimizationLevel.NORMAL
    ): List<String> {
        // Use dedicated OCR worker script to avoid subprocess issues
        val ocrWorkerScript = File(workingDirectory, "ocr_worker.py")

        // Ensure OCR worker script exists
        if (!ocrWorkerScript.exists()) {
            throw OcrException.ConfigurationException("OCR worker script not found: ${ocrWorkerScript.absolutePath}")
        }

        // Base command
        val command = mutableListOf(
            pythonPath,
            ocrWorkerScript.absolutePath,
            imageFile.absolutePath,
            request.language
        )

        // Add memory optimization flags based on level
        when (optimizationLevel) {
            MemoryOptimizationLevel.NORMAL -> {
                // Default settings, no additional flags
            }
            MemoryOptimizationLevel.REDUCED -> {
                // Add environment variables for reduced memory usage
                command.add("--memory-level")
                command.add("reduced")
            }
            MemoryOptimizationLevel.MINIMAL -> {
                // Add environment variables for minimal memory usage
                command.add("--memory-level")
                command.add("minimal")
                command.add("--low-resolution")
            }
        }

        return command
    }

    private fun convertToOcrResult(
        request: OcrRequest,
        paddleResults: String,
        processingTime: Double
    ): OcrResult {
        try {
            val resultJson = json.parseToJsonElement(paddleResults).jsonObject
            val success = resultJson["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val results = resultJson["results"]?.jsonArray ?: emptyList()

            if (!success || results.isEmpty()) {
                return OcrResult(
                    id = request.id,
                    userId = request.userId,
                    success = false,
                    extractedText = "",
                    confidence = 0.0,
                    wordCount = 0,
                    lines = emptyList(),
                    processingTime = processingTime,
                    language = request.language,
                    engine = OcrEngine.PADDLE_OCR,
                    createdAt = Clock.System.now(),
                    metadata = mapOf(
                        "error" to "No text detected in image",
                        "engine_version" to "PaddleOCR"
                    )
                )
            }

            // Parse PaddleOCR results
            val lines = mutableListOf<OcrTextLine>()
            val allText = mutableListOf<String>()
            var totalConfidence = 0.0
            var wordCount = 0

            results.forEach { result ->
                val resultArray = result.jsonArray
                if (resultArray.size >= 2) {
                    // Extract bounding box coordinates
                    val coordinates = resultArray[0].jsonArray
                    val boundingBox = extractBoundingBox(coordinates)

                    // Extract text and confidence
                    val textData = resultArray[1].jsonArray
                    val text = textData[0].jsonPrimitive.content
                    val confidence = textData[1].jsonPrimitive.content.toDouble()

                    lines.add(OcrTextLine(
                        text = text,
                        confidence = confidence,
                        boundingBox = boundingBox,
                        wordCount = text.split("\\s+".toRegex()).size
                    ))

                    allText.add(text)
                    totalConfidence += confidence
                    wordCount += text.split("\\s+".toRegex()).size
                }
            }

            val averageConfidence = if (lines.isNotEmpty()) totalConfidence / lines.size else 0.0
            val extractedText = allText.joinToString(" ")

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
                engine = OcrEngine.PADDLE_OCR,
                createdAt = Clock.System.now(),
                metadata = mapOf(
                    "lines_detected" to lines.size.toString(),
                    "engine_version" to "PaddleOCR",
                    "use_angle_cls" to "true",
                    "use_gpu" to "false"
                )
            )

        } catch (e: Exception) {
            logger.error("Failed to parse PaddleOCR results", e)
            throw OcrException.ProcessingException(
                engine = "PaddleOCR",
                message = "Failed to parse OCR results",
                cause = e
            )
        }
    }

    private fun extractBoundingBox(coordinates: kotlinx.serialization.json.JsonArray): OcrBoundingBox {
        return try {
            // PaddleOCR returns 4 points: [[x1,y1], [x2,y2], [x3,y3], [x4,y4]]
            val points = coordinates.map { point ->
                val coords = point.jsonArray
                Pair(
                    coords[0].jsonPrimitive.content.toDouble(),
                    coords[1].jsonPrimitive.content.toDouble()
                )
            }

            // Calculate bounding box from 4 corner points
            val minX = points.minOf { it.first }.toInt()
            val minY = points.minOf { it.second }.toInt()
            val maxX = points.maxOf { it.first }.toInt()
            val maxY = points.maxOf { it.second }.toInt()

            OcrBoundingBox(
                x1 = minX,
                y1 = minY,
                x2 = maxX,
                y2 = maxY,
                width = maxX - minX,
                height = maxY - minY
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse bounding box coordinates", e)
            OcrBoundingBox(x1 = 0, y1 = 0, x2 = 0, y2 = 0, width = 0, height = 0)
        }
    }
}
