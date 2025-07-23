package dev.screenshotapi.core.usecases.analysis

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import dev.screenshotapi.core.domain.repositories.AnalysisJobQueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyOwnershipUseCase
import dev.screenshotapi.core.usecases.billing.CheckCreditsUseCase
import dev.screenshotapi.core.usecases.billing.CheckCreditsRequest
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.core.usecases.billing.DeductCreditsRequest
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.domain.exceptions.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Create Analysis Use Case - Initiates AI analysis for a completed screenshot
 * 
 * This use case handles:
 * - Validation of screenshot completion
 * - Credit checking and deduction
 * - Analysis job creation and queuing
 * - Usage logging and audit trail
 */
class CreateAnalysisUseCase(
    private val analysisJobRepository: AnalysisJobRepository,
    private val analysisJobQueueRepository: AnalysisJobQueueRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val validateApiKeyOwnershipUseCase: ValidateApiKeyOwnershipUseCase,
    private val checkCreditsUseCase: CheckCreditsUseCase,
    private val deductCreditsUseCase: DeductCreditsUseCase,
    private val logUsageUseCase: LogUsageUseCase
) {
    private val logger = LoggerFactory.getLogger(CreateAnalysisUseCase::class.java)

    suspend operator fun invoke(request: Request): Response {
        logger.info("Creating analysis for screenshot ${request.screenshotJobId} with type ${request.analysisType}")
        
        try {
            // 1. Validate API key ownership if provided
            request.apiKeyId?.let { apiKeyId ->
                validateApiKeyOwnershipUseCase(
                    ValidateApiKeyOwnershipUseCase.Request(
                        userId = request.userId,
                        apiKeyId = apiKeyId
                    )
                )
            }
            
            // 2. Validate screenshot exists and is completed
            val screenshot = screenshotRepository.findByIdAndUserId(request.screenshotJobId, request.userId)
                ?: throw ValidationException.Custom("Screenshot not found or access denied")
            
            if (screenshot.status != ScreenshotStatus.COMPLETED) {
                throw ValidationException.InvalidState("Screenshot", screenshot.status.toString(), "COMPLETED")
            }
            
            if (screenshot.resultUrl.isNullOrBlank()) {
                throw ValidationException.Custom("Screenshot result URL is not available")
            }
            
            // 3. Check if user has sufficient credits
            val requiredCredits = request.analysisType.credits
            val creditCheck = checkCreditsUseCase(
                CheckCreditsRequest(
                    userId = request.userId,
                    requiredCredits = requiredCredits
                )
            )
            
            if (!creditCheck.hasEnoughCredits) {
                throw InsufficientCreditsException(
                    userId = request.userId,
                    requiredCredits = requiredCredits,
                    availableCredits = creditCheck.availableCredits,
                    message = "Insufficient credits for ${request.analysisType.displayName}. Required: $requiredCredits, Available: ${creditCheck.availableCredits}"
                )
            }
            
            // 4. Create analysis job
            val analysisJob = AnalysisJob(
                id = UUID.randomUUID().toString(),
                userId = request.userId,
                screenshotJobId = request.screenshotJobId,
                screenshotUrl = screenshot.resultUrl!!,
                analysisType = request.analysisType,
                status = AnalysisStatus.QUEUED,
                language = request.language,
                webhookUrl = request.webhookUrl,
                createdAt = Clock.System.now()
            )
            
            // 5. Save analysis job
            val savedJob = analysisJobRepository.save(analysisJob)
            
            // 6. Enqueue job for processing in Redis queue
            // This prevents race conditions that occur with database polling
            try {
                analysisJobQueueRepository.enqueue(savedJob)
                logger.debug("Analysis job ${savedJob.id} enqueued to Redis for processing")
            } catch (e: Exception) {
                logger.error("Failed to enqueue analysis job ${savedJob.id} to Redis", e)
                // Don't fail the whole operation, workers can still poll from DB as fallback
            }
            
            // 7. Deduct credits immediately upon job creation
            deductCreditsUseCase(
                DeductCreditsRequest(
                    userId = request.userId,
                    amount = requiredCredits,
                    reason = CreditDeductionReason.AI_ANALYSIS,
                    jobId = savedJob.id
                )
            )
            
            // 8. Log analysis creation
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = request.userId,
                    action = UsageLogAction.AI_ANALYSIS_STARTED,
                    creditsUsed = requiredCredits,
                    apiKeyId = request.apiKeyId,
                    screenshotId = null, // This is for Screenshots table, not ScreenshotJobs
                    metadata = mapOf(
                        "analysisType" to request.analysisType.name,
                        "analysisJobId" to savedJob.id,
                        "screenshotJobId" to request.screenshotJobId, // Store the actual job ID in metadata
                        "language" to request.language,
                        "hasWebhook" to (request.webhookUrl != null).toString()
                    )
                )
            )
            
            logger.info(
                "Analysis job created successfully. " +
                "Job ID: ${savedJob.id}, " +
                "Type: ${request.analysisType.displayName}, " +
                "Credits deducted: $requiredCredits, " +
                "User: ${request.userId}"
            )
            
            return Response(
                analysisJobId = savedJob.id,
                status = savedJob.status,
                analysisType = savedJob.analysisType,
                creditsDeducted = requiredCredits,
                estimatedCompletion = calculateEstimatedCompletion(),
                queuePosition = getQueuePosition()
            )
            
        } catch (e: Exception) {
            logger.error("Failed to create analysis for screenshot ${request.screenshotJobId}", e)
            
            // Log failed attempt
            logUsageUseCase(
                LogUsageUseCase.Request(
                    userId = request.userId,
                    action = UsageLogAction.AI_ANALYSIS_FAILED,
                    creditsUsed = 0, // No credits deducted on creation failure
                    apiKeyId = request.apiKeyId,
                    metadata = mapOf(
                        "error" to (e.message ?: "Unknown error"),
                        "analysisType" to request.analysisType.name,
                        "screenshotJobId" to request.screenshotJobId
                    )
                )
            )
            
            throw e
        }
    }

    data class Request(
        val userId: String,
        val screenshotJobId: String,
        val analysisType: AnalysisType,
        val language: String = "en",
        val webhookUrl: String? = null,
        val apiKeyId: String? = null
    )

    data class Response(
        val analysisJobId: String,
        val status: AnalysisStatus,
        val analysisType: AnalysisType,
        val creditsDeducted: Int,
        val estimatedCompletion: String,
        val queuePosition: Int
    )
    
    /**
     * Calculate estimated completion time based on queue depth
     */
    private suspend fun calculateEstimatedCompletion(): String {
        // Simple estimation: 30 seconds per job in queue + 60 seconds processing
        val queueSize = getQueuePosition()
        val estimatedSeconds = (queueSize * 30) + 60
        val futureTime = Clock.System.now().plus(kotlin.time.Duration.parse("${estimatedSeconds}s"))
        return futureTime.toString()
    }
    
    /**
     * Get current queue position
     */
    private suspend fun getQueuePosition(): Int {
        return try {
            analysisJobRepository.findByStatus(AnalysisStatus.QUEUED).size + 1
        } catch (e: Exception) {
            logger.warn("Could not determine queue position", e)
            1 // Default to position 1
        }
    }

    private suspend fun validateApiKeyOwnership(request: Request) {
        request.apiKeyId?.let { keyId ->
            val isValid = validateApiKeyOwnershipUseCase(
                ValidateApiKeyOwnershipUseCase.Request(
                    userId = request.userId,
                    apiKeyId = keyId
                )
            ).isValid

            if (!isValid) {
                throw ValidationException.UnauthorizedAccess("API key")
            }
        }
    }

    private suspend fun validateScreenshotStatus(request: Request): ScreenshotJob {
        val screenshot = screenshotRepository.findByIdAndUserId(request.screenshotJobId, request.userId)
            ?: throw ResourceNotFoundException("ScreenshotJob", request.screenshotJobId)

        if (screenshot.status != ScreenshotStatus.COMPLETED) {
            throw ValidationException.InvalidState("Screenshot", screenshot.status.toString(), "COMPLETED")
        }

        return screenshot
    }

    private suspend fun validateSufficientCredits(request: Request) {
        val creditCost = AnalysisType.getCreditCost(request.analysisType)
        val checkResult = checkCreditsUseCase(
            CheckCreditsRequest(
                userId = request.userId,
                requiredCredits = creditCost
            )
        )

        if (!checkResult.hasEnoughCredits) {
            throw InsufficientCreditsException(
                userId = request.userId,
                requiredCredits = creditCost,
                availableCredits = checkResult.availableCredits
            )
        }
    }

    private suspend fun deductCreditsForAnalysis(job: AnalysisJob, request: Request) {
        val creditCost = AnalysisType.getCreditCost(request.analysisType)
        val deductionReason = AnalysisType.getDeductionReason(request.analysisType)

        deductCreditsUseCase(
            DeductCreditsRequest(
                userId = request.userId,
                amount = creditCost,
                reason = deductionReason,
                jobId = job.id
            )
        )
    }

    private suspend fun logAnalysisCreation(job: AnalysisJob, request: Request) {
        logUsageUseCase(
            LogUsageUseCase.Request(
                userId = request.userId,
                action = UsageLogAction.AI_ANALYSIS_STARTED,
                creditsUsed = AnalysisType.getCreditCost(request.analysisType),
                apiKeyId = request.apiKeyId,
                screenshotId = null, // This is for Screenshots table, not ScreenshotJobs
                metadata = mapOf(
                    "analysisJobId" to job.id,
                    "screenshotJobId" to request.screenshotJobId,
                    "analysisType" to request.analysisType.name,
                    "language" to request.language
                )
            )
        )
    }
}