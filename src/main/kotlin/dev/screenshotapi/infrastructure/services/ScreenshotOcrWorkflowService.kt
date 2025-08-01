package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.usecases.ocr.ExtractTextUseCase
import dev.screenshotapi.core.usecases.ocr.ExtractPriceDataUseCase
import dev.screenshotapi.core.ports.output.StorageOutputPort
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

/**
 * Screenshot OCR Workflow Service - Orchestrates screenshot and OCR processing
 * GitHub Issue #4: Enhance screenshot workflow with OCR integration
 * 
 * This service belongs in the infrastructure layer as it orchestrates
 * multiple domain services and handles file I/O operations
 */
class ScreenshotOcrWorkflowService(
    private val screenshotRepository: ScreenshotRepository,
    private val ocrResultRepository: OcrResultRepository,
    private val extractTextUseCase: ExtractTextUseCase,
    private val extractPriceDataUseCase: ExtractPriceDataUseCase,
    private val storagePort: StorageOutputPort,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(ScreenshotOcrWorkflowService::class.java)

    /**
     * Process OCR for a completed screenshot job
     * This is called by the ScreenshotWorker after successful screenshot capture
     */
    suspend fun processOcrForScreenshot(
        job: ScreenshotJob,
        screenshotUrl: String? = null
    ): OcrResult? {
        if (!job.ocrRequested) {
            logger.debug("OCR not requested for job ${job.id}")
            return null
        }

        return try {
            logger.info("Starting OCR processing for screenshot job ${job.id}")
            
            // Get screenshot URL from job or parameter
            val url = screenshotUrl ?: job.resultUrl
            if (url == null) {
                logger.error("No screenshot URL available for OCR processing: job ${job.id}")
                return null
            }
            
            // Download screenshot data from URL
            val imageData = withContext(Dispatchers.IO) {
                downloadImageFromUrl(url)
            }
            
            // Create OCR request from job metadata
            val ocrRequest = createOcrRequest(job, imageData)
            
            // Perform OCR based on metadata flags
            val ocrResult = when {
                shouldExtractPrices(job) -> extractPriceDataUseCase.invoke(ocrRequest)
                else -> extractTextUseCase.invoke(ocrRequest)
            }
            
            // Save OCR result
            val savedResult = ocrResultRepository.save(ocrResult)
            
            // Update screenshot job with OCR result reference
            val updatedJob = job.copy(
                ocrResultId = savedResult.id,
                updatedAt = Clock.System.now()
            )
            screenshotRepository.save(updatedJob)
            
            logger.info(
                "OCR processing completed for job ${job.id}. " +
                "OCR result ID: ${savedResult.id}, " +
                "Words: ${savedResult.wordCount}, " +
                "Confidence: ${savedResult.confidence}"
            )
            
            savedResult
            
        } catch (e: Exception) {
            logger.error("OCR processing failed for job ${job.id}", e)
            
            // Create failed OCR result for tracking
            val failedResult = createFailedOcrResult(job, e)
            ocrResultRepository.save(failedResult)
            
            // Update job with failed OCR result
            val updatedJob = job.copy(
                ocrResultId = failedResult.id,
                updatedAt = Clock.System.now()
            )
            screenshotRepository.save(updatedJob)
            
            failedResult
        }
    }
    
    /**
     * Check if a screenshot job has OCR processing enabled
     */
    fun isOcrEnabled(job: ScreenshotJob): Boolean {
        return job.ocrRequested
    }
    
    /**
     * Get OCR configuration from screenshot job - using defaults for workflow processing
     */
    private fun getOcrConfig(job: ScreenshotJob): OcrWorkflowConfig {
        return OcrWorkflowConfig(
            language = "en",
            tier = OcrTier.BASIC,
            engine = null, // Let OCR service choose
            extractPrices = false,
            extractTables = false,
            extractForms = false,
            confidenceThreshold = 0.8
        )
    }
    
    private fun createOcrRequest(job: ScreenshotJob, imageData: ByteArray): OcrRequest {
        val config = getOcrConfig(job)
        
        return OcrRequest(
            id = UUID.randomUUID().toString(),
            userId = job.userId,
            screenshotJobId = job.id,
            imageBytes = imageData,
            language = config.language,
            tier = config.tier,
            engine = config.engine,
            useCase = determineOcrUseCase(config),
            analysisType = determineAnalysisType(config),
            options = OcrOptions(
                extractPrices = config.extractPrices,
                extractTables = config.extractTables,
                extractForms = config.extractForms,
                confidenceThreshold = config.confidenceThreshold,
                enableStructuredData = config.extractPrices || 
                                     config.extractTables || 
                                     config.extractForms
            )
        )
    }
    
    private fun determineOcrUseCase(config: OcrWorkflowConfig): OcrUseCase {
        return when {
            config.extractPrices -> OcrUseCase.PRICE_MONITORING
            config.extractTables -> OcrUseCase.TABLE_EXTRACTION
            config.extractForms -> OcrUseCase.FORM_PROCESSING
            else -> OcrUseCase.GENERAL
        }
    }
    
    /**
     * Determine analysis type based on OCR workflow configuration
     * Maps legacy tier-based config to new analysis type system
     */
    private fun determineAnalysisType(config: OcrWorkflowConfig): AnalysisType {
        return when {
            // If extracting structured data, use appropriate AI analysis
            config.extractPrices -> AnalysisType.CONTENT_SUMMARY  // Price extraction benefits from content analysis
            config.extractTables || config.extractForms -> AnalysisType.UX_ANALYSIS  // Forms/tables are UX elements
            
            // Map tier to analysis type for general OCR
            config.tier == OcrTier.AI_PREMIUM || config.tier == OcrTier.AI_ELITE -> AnalysisType.UX_ANALYSIS
            config.tier == OcrTier.AI_STANDARD -> AnalysisType.CONTENT_SUMMARY
            
            // Default to basic OCR for BASIC and LOCAL_AI tiers
            else -> AnalysisType.BASIC_OCR
        }
    }
    
    private fun shouldExtractPrices(job: ScreenshotJob): Boolean {
        return false // Default: no price extraction for screenshot workflow
    }
    
    private fun createFailedOcrResult(job: ScreenshotJob, error: Exception): OcrResult {
        val config = getOcrConfig(job)
        
        return OcrResult(
            id = UUID.randomUUID().toString(),
            userId = job.userId,
            success = false,
            extractedText = "",
            confidence = 0.0,
            wordCount = 0,
            lines = emptyList(),
            processingTime = 0.0,
            language = config.language,
            engine = config.engine ?: OcrEngine.PADDLE_OCR,
            createdAt = Clock.System.now(),
            metadata = mapOf(
                "screenshotJobId" to job.id,
                "userId" to job.userId,
                "error" to (error.message ?: "Unknown error"),
                "errorType" to error::class.simpleName.orEmpty()
            )
        )
    }
    
    private suspend fun downloadImageFromUrl(url: String): ByteArray {
        return try {
            httpClient.get(url).readBytes()
        } catch (e: Exception) {
            logger.error("Failed to download image from URL: $url", e)
            throw Exception("Failed to download screenshot for OCR processing", e)
        }
    }
    
    private fun extractFilenameFromUrl(url: String): String {
        return url.substringAfterLast('/').substringBefore('?')
    }
}

/**
 * OCR Workflow Configuration
 * Infrastructure-layer configuration for OCR processing
 */
private data class OcrWorkflowConfig(
    val language: String,
    val tier: OcrTier,
    val engine: OcrEngine?,
    val extractPrices: Boolean,
    val extractTables: Boolean,
    val extractForms: Boolean,
    val confidenceThreshold: Double
)