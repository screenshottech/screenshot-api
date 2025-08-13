package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.OcrException
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.services.OcrService
import dev.screenshotapi.core.usecases.billing.DeductCreditsRequest
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import org.slf4j.LoggerFactory

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
            logger.info("Starting OCR extraction for user ${request.userId}, tier: ${request.tier}, analysisType: ${request.analysisType}")

            // Validate user and credits
            val user = userRepository.findById(request.userId)
                ?: throw OcrException.ConfigurationException("User not found: ${request.userId}")

            val requiredCredits = calculateRequiredCredits(request.tier, request.analysisType)

            // Check and deduct credits upfront
            val analysisType = request.analysisType ?: AnalysisType.BASIC_OCR
            try {
                deductCreditsUseCase(
                    DeductCreditsRequest(
                        userId = request.userId,
                        amount = requiredCredits,
                        reason = getDeductionReason(analysisType),
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
                    screenshotId = request.screenshotJobId,
                    metadata = mapOf(
                        "tier" to request.tier.name,
                        "analysis_type" to analysisType.name,
                        "engine" to (request.engine?.name ?: "AUTO"),
                        "language" to request.language,
                        "use_case" to request.useCase.name,
                        "requires_ai" to analysisType.requiresAI.toString(),
                        "ocrRequestId" to request.id
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
                    screenshotId = request.screenshotJobId,
                    metadata = mapOf(
                        "processing_time" to processingTime.toString(),
                        "confidence" to result.confidence.toString(),
                        "word_count" to result.wordCount.toString(),
                        "analysis_type" to analysisType.name,
                        "engine" to result.engine.name,
                        "success" to result.success.toString(),
                        "ocrRequestId" to request.id
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
                    screenshotId = request.screenshotJobId,
                    metadata = mapOf(
                        "processing_time" to processingTime.toString(),
                        "error" to e.message.orEmpty(),
                        "error_type" to e::class.simpleName.orEmpty(),
                        "ocrRequestId" to request.id
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
                    screenshotId = request.screenshotJobId,
                    metadata = mapOf(
                        "processing_time" to processingTime.toString(),
                        "error" to e.message.orEmpty(),
                        "error_type" to "UnexpectedException",
                        "ocrRequestId" to request.id
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

    /**
     * Calculate required credits based on analysis type and tier
     * Analysis type takes precedence over tier for credit calculation
     */
    private fun calculateRequiredCredits(tier: OcrTier, analysisType: AnalysisType?): Int {
        return if (analysisType != null) {
            // Use analysis type credits (new system)
            analysisType.credits
        } else {
            // Fallback to tier-based credits (legacy system)
            when (tier) {
                OcrTier.BASIC -> 2
                OcrTier.LOCAL_AI -> 2
                OcrTier.AI_STANDARD -> 3
                OcrTier.AI_PREMIUM -> 5
                OcrTier.AI_ELITE -> 5
            }
        }
    }

    /**
     * Get credit deduction reason based on analysis type
     */
    private fun getDeductionReason(analysisType: AnalysisType): CreditDeductionReason {
        return when (analysisType) {
            AnalysisType.BASIC_OCR -> CreditDeductionReason.OCR
            AnalysisType.UX_ANALYSIS, AnalysisType.CONTENT_SUMMARY, AnalysisType.GENERAL, AnalysisType.CUSTOM -> CreditDeductionReason.AI_ANALYSIS
        }
    }
}
