package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.services.RetryPolicy
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Scheduled use case for processing failed jobs that are ready for automatic retry.
 * Identifies FAILED jobs that are retryable and schedules them for retry.
 */
class ProcessFailedRetryableJobsUseCase(
    private val screenshotRepository: ScreenshotRepository,
    private val queueRepository: QueueRepository,
    private val retryPolicy: RetryPolicy,
    private val logUsageUseCase: LogUsageUseCase
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend operator fun invoke(request: Request = Request()): Response {
        logger.info("Processing failed retryable jobs - maxJobs: ${request.maxJobs}")
        
        val failedJobs = screenshotRepository.findFailedRetryableJobs(limit = request.maxJobs)
        
        if (failedJobs.isEmpty()) {
            logger.debug("No failed retryable jobs found")
            return Response(
                processedJobs = 0,
                retriedJobs = 0,
                markedNonRetryableJobs = 0,
                skippedJobs = 0,
                details = emptyList()
            )
        }
        
        logger.info("Found ${failedJobs.size} failed retryable jobs")
        
        val results = mutableListOf<JobProcessingResult>()
        var retriedCount = 0
        var markedNonRetryableCount = 0
        var skippedCount = 0
        
        for (job in failedJobs) {
            val result = processFailedJob(job)
            results.add(result)
            
            when (result.action) {
                ProcessingAction.RETRIED -> retriedCount++
                ProcessingAction.MARKED_NON_RETRYABLE -> markedNonRetryableCount++
                ProcessingAction.SKIPPED -> skippedCount++
            }
        }
        
        logger.info("Failed jobs processing completed - processed: ${failedJobs.size}, retried: $retriedCount, marked non-retryable: $markedNonRetryableCount, skipped: $skippedCount")
        
        return Response(
            processedJobs = failedJobs.size,
            retriedJobs = retriedCount,
            markedNonRetryableJobs = markedNonRetryableCount,
            skippedJobs = skippedCount,
            details = results
        )
    }
    
    private suspend fun processFailedJob(job: ScreenshotJob): JobProcessingResult {
        logger.info("Processing failed job: ${job.id}, retryCount: ${job.retryCount}, lastFailure: ${job.lastFailureReason}")
        
        if (job.nextRetryAt != null && job.nextRetryAt > Clock.System.now()) {
            logger.debug("Job ${job.id} not ready for retry yet - nextRetryAt: ${job.nextRetryAt}")
            return JobProcessingResult(
                jobId = job.id,
                action = ProcessingAction.SKIPPED,
                reason = "Not ready for retry yet (nextRetryAt: ${job.nextRetryAt})"
            )
        }
        
        val lockedJob = screenshotRepository.tryLockJob(job.id, "failed-job-processor")
        if (lockedJob == null) {
            logger.debug("Job ${job.id} is already being processed by another worker")
            return JobProcessingResult(
                jobId = job.id,
                action = ProcessingAction.SKIPPED,
                reason = "Job locked by another worker"
            )
        }
        
        return try {
            when {
                lockedJob.canRetry() -> {
                    val lastException = createExceptionFromFailureReason(lockedJob.lastFailureReason)
                    
                    if (retryPolicy.shouldRetry(lastException)) {
                        val delay = retryPolicy.calculateDelay(lockedJob.retryCount)
                        val retryJob = lockedJob.scheduleRetry(
                            reason = "Automatic retry for failed job",
                            delay = delay
                        )
                        
                        screenshotRepository.update(retryJob)
                        queueRepository.enqueueDelayed(retryJob, delay)
                        
                        logUsageUseCase.invoke(LogUsageUseCase.Request(
                            userId = job.userId,
                            action = UsageLogAction.SCREENSHOT_RETRIED,
                            apiKeyId = job.apiKeyId,
                            screenshotId = job.id,
                            metadata = mapOf(
                                "retryType" to "AUTOMATIC",
                                "reason" to "failed_job_retry",
                                "retryCount" to job.retryCount.toString(),
                                "delaySeconds" to delay.inWholeSeconds.toString(),
                                "originalFailure" to (job.lastFailureReason ?: "Unknown")
                            )
                        ))
                        
                        logger.info("Failed job ${job.id} scheduled for retry in ${delay.inWholeSeconds}s")
                        
                        JobProcessingResult(
                            jobId = job.id,
                            action = ProcessingAction.RETRIED,
                            reason = "Scheduled for retry with ${delay.inWholeSeconds}s delay"
                        )
                    } else {
                        val nonRetryableJob = lockedJob.markAsNonRetryable(
                            "Failed job - non-retryable error: ${lockedJob.lastFailureReason}"
                        )
                        
                        screenshotRepository.update(nonRetryableJob)
                        
                        logger.warn("Failed job ${job.id} marked as non-retryable - error type not retryable")
                        
                        JobProcessingResult(
                            jobId = job.id,
                            action = ProcessingAction.MARKED_NON_RETRYABLE,
                            reason = "Non-retryable error type: ${job.lastFailureReason}"
                        )
                    }
                }
                
                else -> {
                    val nonRetryableJob = lockedJob.markAsNonRetryable(
                        "Maximum retry attempts exceeded (${lockedJob.maxRetries})"
                    )
                    
                    screenshotRepository.update(nonRetryableJob)
                    
                    logger.warn("Failed job ${job.id} marked as non-retryable - max retries exceeded")
                    
                    JobProcessingResult(
                        jobId = job.id,
                        action = ProcessingAction.MARKED_NON_RETRYABLE,
                        reason = "Maximum retry attempts exceeded"
                    )
                }
            }
        } finally {
            screenshotRepository.update(lockedJob.unlock())
        }
    }
    
    private fun createExceptionFromFailureReason(failureReason: String?): Exception {
        if (failureReason == null) return RuntimeException("Unknown error")
        
        return when {
            failureReason.contains("timeout", ignoreCase = true) -> 
                java.net.SocketTimeoutException(failureReason)
            failureReason.contains("connection", ignoreCase = true) -> 
                java.net.ConnectException(failureReason)
            failureReason.contains("network", ignoreCase = true) -> 
                java.io.IOException(failureReason)
            failureReason.contains("invalid url", ignoreCase = true) -> 
                IllegalArgumentException(failureReason)
            failureReason.contains("unauthorized", ignoreCase = true) -> 
                SecurityException(failureReason)
            failureReason.contains("credits", ignoreCase = true) -> 
                IllegalStateException(failureReason) // Will be classified as non-retryable
            else -> 
                RuntimeException(failureReason) // Default to retryable
        }
    }

    data class Request(
        val maxJobs: Int = 100
    )

    data class Response(
        val processedJobs: Int,
        val retriedJobs: Int,
        val markedNonRetryableJobs: Int,
        val skippedJobs: Int,
        val details: List<JobProcessingResult>
    )

    data class JobProcessingResult(
        val jobId: String,
        val action: ProcessingAction,
        val reason: String
    )

    enum class ProcessingAction {
        RETRIED,
        MARKED_NON_RETRYABLE,
        SKIPPED
    }
}