package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.exceptions.*
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.usecases.billing.CheckCreditsRequest
import dev.screenshotapi.core.usecases.billing.CheckCreditsUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Use case for manually retrying a failed or stuck screenshot job.
 * Validates authorization, credits, and job state before queuing retry.
 */
class ManualRetryScreenshotUseCase(
    private val screenshotRepository: ScreenshotRepository,
    private val queueRepository: QueueRepository,
    private val checkCreditsUseCase: CheckCreditsUseCase,
    private val logUsageUseCase: LogUsageUseCase
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend operator fun invoke(request: Request): Response {
        logger.info("Manual retry requested for job: ${request.jobId} by user: ${request.userId}")
        
        val job = screenshotRepository.findById(request.jobId)
            ?: throw ResourceNotFoundException("Job not found: ${request.jobId}")

        if (job.userId != request.userId) {
            logger.warn("Unauthorized retry attempt: job=${request.jobId} by user=${request.userId}")
            throw AuthorizationException.JobNotAuthorized("Not authorized to retry this job")
        }

        if (job.status != ScreenshotStatus.FAILED && !job.isStuck()) {
            throw ValidationException.InvalidState("job", job.status.toString(), "FAILED or stuck")
        }

        // Check if job is already scheduled for automatic retry
        if (job.nextRetryAt != null && job.nextRetryAt > Clock.System.now()) {
            // Try to cancel the delayed job first
            val cancelled = queueRepository.cancelDelayedJob(job.id)
            if (cancelled) {
                logger.info("Cancelled scheduled automatic retry for manual retry: jobId=${job.id}, was scheduled for=${job.nextRetryAt}")
            } else {
                throw ValidationException.InvalidState(
                    "job", 
                    "scheduled for automatic retry", 
                    "available for manual retry"
                )
            }
        }

        val hasCredits = checkCreditsUseCase(CheckCreditsRequest(
            userId = request.userId,
            requiredCredits = job.jobType.defaultCredits
        ))

        if (!hasCredits.hasEnoughCredits) {
            logger.warn("Insufficient credits for retry: user=${request.userId}, required=${job.jobType.defaultCredits}")
            throw InsufficientCreditsException(
                userId = request.userId,
                requiredCredits = job.jobType.defaultCredits,
                availableCredits = hasCredits.availableCredits,
                message = "Insufficient credits for retry"
            )
        }

        val lockedJob = screenshotRepository.tryLockJob(job.id, "manual-retry-${request.requestedBy}")
            ?: throw ConcurrentModificationException(
                message = "Job is currently being processed by another worker",
                resourceId = job.id
            )

        try {
            val retryJob = lockedJob.resetForManualRetry()
            val savedJob = screenshotRepository.update(retryJob)
            queueRepository.enqueue(savedJob)

            logUsageUseCase.invoke(LogUsageUseCase.Request(
                userId = request.userId,
                action = UsageLogAction.SCREENSHOT_RETRIED,
                apiKeyId = job.apiKeyId,
                screenshotId = job.id,
                metadata = mapOf(
                    "retryType" to "MANUAL",
                    "originalFailureReason" to (job.lastFailureReason ?: "Unknown"),
                    "requestedBy" to request.requestedBy
                )
            ))

            logger.info("Manual retry queued successfully: job=${request.jobId}")
            
            return Response(
                jobId = savedJob.id,
                message = "Job queued for manual retry",
                queuePosition = queueRepository.size().toInt()
            )
        } finally {
            screenshotRepository.update(lockedJob.unlock())
        }
    }

    data class Request(
        val jobId: String,
        val userId: String,
        val requestedBy: String // API key ID or "web"
    )

    data class Response(
        val jobId: String,
        val message: String,
        val queuePosition: Int
    )
}