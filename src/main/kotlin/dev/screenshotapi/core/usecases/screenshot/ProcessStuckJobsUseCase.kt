package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.services.RetryPolicy
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import org.slf4j.LoggerFactory

/**
 * Scheduled use case for processing stuck jobs and recovering them.
 * Identifies jobs stuck in PROCESSING state and either retries or marks as failed.
 */
class ProcessStuckJobsUseCase(
    private val screenshotRepository: ScreenshotRepository,
    private val queueRepository: QueueRepository,
    private val retryPolicy: RetryPolicy,
    private val logUsageUseCase: LogUsageUseCase
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend operator fun invoke(request: Request = Request()): Response {
        logger.info("Processing stuck jobs - stuckAfterMinutes: ${request.stuckAfterMinutes}, maxJobs: ${request.maxJobs}")
        
        val stuckJobs = screenshotRepository.findStuckJobs(
            stuckAfterMinutes = request.stuckAfterMinutes,
            limit = request.maxJobs
        )
        
        if (stuckJobs.isEmpty()) {
            logger.debug("No stuck jobs found")
            return Response(
                processedJobs = 0,
                retriedJobs = 0,
                failedJobs = 0,
                details = emptyList()
            )
        }
        
        logger.warn("Found ${stuckJobs.size} stuck jobs")
        
        val results = mutableListOf<JobProcessingResult>()
        var retriedCount = 0
        var failedCount = 0
        
        for (job in stuckJobs) {
            val result = processStuckJob(job)
            results.add(result)
            
            when (result.action) {
                ProcessingAction.RETRIED -> retriedCount++
                ProcessingAction.MARKED_FAILED -> failedCount++
                ProcessingAction.SKIPPED -> {} // No action needed
            }
        }
        
        logger.info("Stuck jobs processing completed - processed: ${stuckJobs.size}, retried: $retriedCount, failed: $failedCount")
        
        return Response(
            processedJobs = stuckJobs.size,
            retriedJobs = retriedCount,
            failedJobs = failedCount,
            details = results
        )
    }
    
    private suspend fun processStuckJob(job: ScreenshotJob): JobProcessingResult {
        logger.info("Processing stuck job: ${job.id}, status: ${job.status}, retryCount: ${job.retryCount}")
        
        val lockedJob = screenshotRepository.tryLockJob(job.id, "stuck-job-processor")
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
                    val stuckException = RuntimeException("Job stuck in processing for >30 minutes")
                    
                    if (retryPolicy.shouldRetry(stuckException)) {
                        val delay = retryPolicy.calculateDelay(lockedJob.retryCount)
                        val retryJob = lockedJob.scheduleRetry("Job stuck in processing", delay)
                        
                        screenshotRepository.update(retryJob)
                        queueRepository.enqueueDelayed(retryJob, delay)
                        
                        logUsageUseCase.invoke(LogUsageUseCase.Request(
                            userId = job.userId,
                            action = UsageLogAction.SCREENSHOT_CREATED,
                            apiKeyId = job.apiKeyId,
                            screenshotId = job.id,
                            metadata = mapOf(
                                "retryType" to "AUTOMATIC",
                                "reason" to "stuck_job_recovery",
                                "retryCount" to job.retryCount.toString(),
                                "delaySeconds" to delay.inWholeSeconds.toString()
                            )
                        ))
                        
                        logger.info("Stuck job ${job.id} scheduled for retry in ${delay.inWholeSeconds}s")
                        
                        JobProcessingResult(
                            jobId = job.id,
                            action = ProcessingAction.RETRIED,
                            reason = "Scheduled for retry with ${delay.inWholeSeconds}s delay"
                        )
                    } else {
                        val failedJob = lockedJob
                            .markAsNonRetryable("Stuck job - non-retryable error type")
                            .copy(status = ScreenshotStatus.FAILED)
                        
                        screenshotRepository.update(failedJob)
                        
                        logger.warn("Stuck job ${job.id} marked as failed - non-retryable error")
                        
                        JobProcessingResult(
                            jobId = job.id,
                            action = ProcessingAction.MARKED_FAILED,
                            reason = "Non-retryable error type"
                        )
                    }
                }
                
                else -> {
                    val failedJob = lockedJob.copy(
                        status = ScreenshotStatus.FAILED,
                        errorMessage = "Job exceeded maximum retry attempts (${lockedJob.maxRetries})",
                        lastFailureReason = "Max retries exceeded"
                    )
                    
                    screenshotRepository.update(failedJob)
                    
                    logger.warn("Stuck job ${job.id} marked as failed - max retries exceeded")
                    
                    JobProcessingResult(
                        jobId = job.id,
                        action = ProcessingAction.MARKED_FAILED,
                        reason = "Maximum retry attempts exceeded"
                    )
                }
            }
        } finally {
            screenshotRepository.update(lockedJob.unlock())
        }
    }

    data class Request(
        val stuckAfterMinutes: Int = 30,
        val maxJobs: Int = 100
    )

    data class Response(
        val processedJobs: Int,
        val retriedJobs: Int,
        val failedJobs: Int,
        val details: List<JobProcessingResult>
    )

    data class JobProcessingResult(
        val jobId: String,
        val action: ProcessingAction,
        val reason: String
    )

    enum class ProcessingAction {
        RETRIED,
        MARKED_FAILED,
        SKIPPED
    }
}