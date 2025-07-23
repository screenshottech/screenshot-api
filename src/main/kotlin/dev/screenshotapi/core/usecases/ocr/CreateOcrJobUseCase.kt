package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.repositories.QueueRepository
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Create OCR Job Use Case - Handles independent OCR job creation and queuing
 * 
 * Moved from OcrController to follow Clean Architecture principles.
 * Responsible for creating OCR jobs, storing results, and queuing for processing.
 * 
 * This handles OCR INDEPENDENT flow (POST /api/v1/ocr/extract) where users
 * upload base64 images directly for OCR processing.
 */
class CreateOcrJobUseCase(
    private val ocrResultRepository: OcrResultRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val queueRepository: QueueRepository
) {
    private val logger = LoggerFactory.getLogger(CreateOcrJobUseCase::class.java)

    data class Request(
        val userId: String,
        val apiKeyId: String,
        val ocrRequest: OcrRequest,
        val requestMetadata: Map<String, String> = emptyMap()
    )

    data class Response(
        val jobId: String,
        val status: String,
        val estimatedCompletion: String,
        val queuePosition: Int
    )

    suspend operator fun invoke(request: Request): Response {
        logger.info(
            "Creating independent OCR job for user ${request.userId}, tier: ${request.ocrRequest.tier}, " +
            "analysisType: ${request.ocrRequest.analysisType}"
        )

        try {
            // Generate job ID for the OCR job (independent job, not related to any screenshot)
            val jobId = UUID.randomUUID().toString()
            
            // Update OCR request with the correct screenshotJobId for foreign key reference
            val updatedOcrRequest = request.ocrRequest.copy(screenshotJobId = jobId)

            // Create a pending OCR result to store the request data
            val pendingOcrResult = OcrResult(
                id = updatedOcrRequest.id,
                userId = request.userId,
                success = false, // Will be updated when processed
                extractedText = "", // Will be populated when processed
                confidence = 0.0,
                wordCount = 0,
                lines = emptyList(), // Will be populated when processed
                processingTime = 0.0,
                language = updatedOcrRequest.language,
                engine = OcrEngine.PADDLE_OCR, // Default, will be updated
                metadata = buildResultMetadata(request),
                createdAt = Clock.System.now()
            )

            // DEBUG: Log the OcrResult userId before saving
            logger.info("OCR Extract: pendingOcrResult.userId = '${pendingOcrResult.userId}'")
            logger.info("OCR Extract: updatedOcrRequest.userId = '${updatedOcrRequest.userId}'")
            logger.info("OCR Extract: about to save OCR result with ID = '${pendingOcrResult.id}'")

            // Save pending OCR result
            ocrResultRepository.save(pendingOcrResult)

            // Create OCR job using the screenshot_jobs table with JobType.OCR
            val ocrJob = ScreenshotJob.createOcrJob(
                id = jobId,
                userId = request.userId,
                apiKeyId = request.apiKeyId,
                ocrRequest = updatedOcrRequest
            ).copy(ocrResultId = pendingOcrResult.id)

            // Save job to database
            screenshotRepository.save(ocrJob)
            
            // Enqueue for processing
            queueRepository.enqueue(ocrJob)

            logger.info(
                "Independent OCR job created and queued: userId={}, jobId={}, tier={}, analysisType={}",
                request.userId, 
                jobId, 
                updatedOcrRequest.tier, 
                updatedOcrRequest.analysisType
            )

            return Response(
                jobId = jobId,
                status = "QUEUED",
                estimatedCompletion = "~30 seconds",
                queuePosition = 1 // We'll improve this later with actual queue position
            )

        } catch (e: Exception) {
            logger.error("Failed to create independent OCR job for user ${request.userId}", e)
            throw e
        }
    }

    private fun buildResultMetadata(request: Request): Map<String, String> {
        return request.requestMetadata + mapOf(
            "status" to "PENDING"
        )
    }
}