package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.OcrException
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.services.OcrService
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.core.usecases.billing.DeductCreditsRequest
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Extract Text Use Case - Core OCR text extraction business logic
 * GitHub Issue #2: OCR Domain Architecture
 */
class ExtractTextUseCase(
    private val ocrService: OcrService,
    private val userRepository: UserRepository,
    private val deductCreditsUseCase: DeductCreditsUseCase,
    private val logUsageUseCase: LogUsageUseCase
) {
    private val logger = LoggerFactory.getLogger(ExtractTextUseCase::class.java)

    suspend fun invoke(request: OcrRequest): OcrResult {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.info("Starting OCR extraction for user ${request.userId}, tier: ${request.tier}")
            
            // Validate user and credits
            val user = userRepository.findById(request.userId)
                ?: throw OcrException.ConfigurationException("User not found: ${request.userId}")
            
            val requiredCredits = calculateRequiredCredits(request.tier)
            
            // Check and deduct credits upfront
            try {
                deductCreditsUseCase(
                    DeductCreditsRequest(
                        userId = request.userId,
                        amount = requiredCredits,
                        reason = CreditDeductionReason.OCR,
                        jobId = request.id
                    )
                )
            } catch (e: Exception) {
                logger.warn("Credit deduction failed for user ${request.userId}", e)
                throw OcrException.InsufficientCreditsException(
                    userId = request.userId,
                    tier = request.tier.name,
                    requiredCredits = requiredCredits,
                    availableCredits = user.creditsRemaining
                )
            }
            
            // Log OCR initiation
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = request.userId,
                    action = UsageLogAction.OCR_CREATED,
                    creditsUsed = requiredCredits,
                    screenshotId = request.id,
                    metadata = mapOf(
                        "tier" to request.tier.name,
                        "engine" to (request.engine?.name ?: "AUTO"),
                        "language" to request.language,
                        "use_case" to request.useCase.name
                    )
                )
            )
            
            // Perform OCR extraction
            val result = ocrService.extractText(request)
            
            val processingTime = (System.currentTimeMillis() - startTime) / 1000.0
            
            // Log successful completion
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = request.userId,
                    action = UsageLogAction.OCR_COMPLETED,
                    screenshotId = request.id,
                    metadata = mapOf(
                        "processing_time" to processingTime.toString(),
                        "confidence" to result.confidence.toString(),
                        "word_count" to result.wordCount.toString(),
                        "engine" to result.engine.name,
                        "success" to result.success.toString()
                    )
                )
            )
            
            logger.info("OCR extraction completed for user ${request.userId} in ${processingTime}s")
            return result
            
        } catch (e: OcrException) {
            val processingTime = (System.currentTimeMillis() - startTime) / 1000.0
            
            // Log failure
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = request.userId,
                    action = UsageLogAction.OCR_FAILED,
                    screenshotId = request.id,
                    metadata = mapOf(
                        "processing_time" to processingTime.toString(),
                        "error" to e.message.orEmpty(),
                        "error_type" to e::class.simpleName.orEmpty()
                    )
                )
            )
            
            logger.error("OCR extraction failed for user ${request.userId}", e)
            throw e
            
        } catch (e: Exception) {
            val processingTime = (System.currentTimeMillis() - startTime) / 1000.0
            
            // Log unexpected failure
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = request.userId,
                    action = UsageLogAction.OCR_FAILED,
                    screenshotId = request.id,
                    metadata = mapOf(
                        "processing_time" to processingTime.toString(),
                        "error" to e.message.orEmpty(),
                        "error_type" to "UnexpectedException"
                    )
                )
            )
            
            logger.error("Unexpected error during OCR extraction for user ${request.userId}", e)
            throw OcrException.ProcessingException(
                engine = request.engine?.name ?: "UNKNOWN",
                message = "Unexpected error during OCR processing",
                cause = e
            )
        }
    }
    
    private fun calculateRequiredCredits(tier: OcrTier): Int {
        return when (tier) {
            OcrTier.BASIC -> 2
            OcrTier.LOCAL_AI -> 2
            OcrTier.AI_STANDARD -> 3
            OcrTier.AI_PREMIUM -> 5
            OcrTier.AI_ELITE -> 5
        }
    }
}