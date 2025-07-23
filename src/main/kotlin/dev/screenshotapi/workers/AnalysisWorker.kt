package dev.screenshotapi.workers

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import dev.screenshotapi.core.domain.repositories.AnalysisJobQueueRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.analysis.ProcessAnalysisUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.infrastructure.config.AnalysisConfig
import dev.screenshotapi.infrastructure.services.MetricsService
import dev.screenshotapi.infrastructure.services.AnalysisMetricsCollector
import dev.screenshotapi.infrastructure.services.NotificationService
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Analysis Worker - Processes AI analysis jobs asynchronously
 * 
 * This worker handles the lifecycle of analysis jobs:
 * - Polls for QUEUED analysis jobs
 * - Downloads screenshot images
 * - Processes with AWS Bedrock
 * - Updates job status and results
 * - Sends webhooks on completion
 */
class AnalysisWorker(
    val id: String,
    private val analysisJobRepository: AnalysisJobRepository,
    private val analysisJobQueueRepository: AnalysisJobQueueRepository,
    private val userRepository: UserRepository,
    private val processAnalysisUseCase: ProcessAnalysisUseCase,
    private val logUsageUseCase: LogUsageUseCase,
    private val notificationService: NotificationService,
    private val metricsService: MetricsService,
    private val metricsCollector: AnalysisMetricsCollector,
    private val config: AppConfig,
    private val analysisConfig: AnalysisConfig
) {
    private val logger = LoggerFactory.getLogger("${this::class.simpleName}-$id")

    // State management
    private val isRunning = AtomicBoolean(false)
    private val currentState = AtomicReference(WorkerState.IDLE)
    private val currentJob = AtomicReference<AnalysisJob?>(null)

    // Metrics
    private val jobsProcessed = AtomicLong(0)
    private val jobsSuccessful = AtomicLong(0)
    private val jobsFailed = AtomicLong(0)
    private val lastActivity = AtomicReference<Instant?>(null)
    private val startTime = Clock.System.now()

    // Health monitoring
    private val lastHeartbeat = AtomicReference(Clock.System.now())
    private val consecutiveErrors = AtomicLong(0)
    private val maxConsecutiveErrors = 5

    // Configuration
    private val pollingInterval = config.analysisWorker.pollingIntervalMs.milliseconds
    private val processingTimeout = config.analysisWorker.processingTimeoutMs.milliseconds

    suspend fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Analysis Worker $id starting")
            currentState.set(WorkerState.PROCESSING)

            try {
                workLoop()
            } catch (e: CancellationException) {
                logger.info("Analysis Worker $id was cancelled")
            } catch (e: Exception) {
                logger.error("Analysis Worker $id encountered fatal error", e)
                currentState.set(WorkerState.ERROR)
            } finally {
                currentState.set(WorkerState.STOPPED)
                isRunning.set(false)
                logger.info("Analysis Worker $id stopped. Stats: processed=${jobsProcessed.get()}, successful=${jobsSuccessful.get()}, failed=${jobsFailed.get()}")
            }
        } else {
            logger.warn("Analysis Worker $id is already running")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Analysis Worker $id stopping...")
            currentState.set(WorkerState.STOPPING)
        }
    }

    suspend fun shutdown() {
        logger.info("Analysis Worker $id shutting down gracefully...")
        stop()

        val currentJob = currentJob.get()
        if (currentJob != null) {
            logger.info("Analysis Worker $id waiting for current job ${currentJob.id} to complete...")

            var waitTime = 0L
            val maxWaitTime = 30_000L

            while (this.currentJob.get() != null && waitTime < maxWaitTime) {
                delay(1000)
                waitTime += 1000
            }

            if (this.currentJob.get() != null) {
                logger.warn("Analysis Worker $id shutdown timeout, job may be interrupted")
            }
        }

        currentState.set(WorkerState.STOPPED)
        logger.info("Analysis Worker $id shutdown completed")
    }

    private suspend fun workLoop() {
        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                updateHeartbeat()

                val job = dequeueJob()

                if (job != null) {
                    processJob(job)
                    consecutiveErrors.set(0) // Reset error counter on successful processing
                } else {
                    // No jobs available, enter idle mode
                    currentState.set(WorkerState.IDLE)
                    delay(pollingInterval)
                }

            } catch (e: CancellationException) {
                logger.debug("Analysis Worker $id work loop cancelled")
                break
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private suspend fun dequeueJob(): AnalysisJob? {
        return try {
            // Use Redis queue only (atomic, prevents race conditions)
            val job = analysisJobQueueRepository.dequeue()
            if (job != null) {
                logger.debug("Dequeued analysis job from Redis: ${job.id}")
            }
            job
        } catch (e: Exception) {
            logger.error("Failed to dequeue analysis job from Redis", e)
            null
        }
    }

    private suspend fun processJob(job: AnalysisJob) {
        currentJob.set(job)
        currentState.set(WorkerState.PROCESSING)
        val startTime = System.currentTimeMillis()

        try {
            logger.info("Processing analysis job: jobId=${job.id}, type=${job.analysisType}, userId=${job.userId}")
            lastActivity.set(Clock.System.now())
            jobsProcessed.incrementAndGet()

            // Double-check job status and try to claim it atomically at worker level
            // This provides additional protection against race conditions
            val claimSuccessful = analysisJobRepository.updateStatus(
                job.id, 
                AnalysisStatus.PROCESSING
            )
            
            if (!claimSuccessful) {
                logger.warn("Failed to claim job ${job.id} - already claimed by another worker")
                return
            }
            
            // Record metrics for job start
            metricsCollector.recordJobStart(job.id, job.analysisType, job.createdAt)

            // Log analysis started
            logUsageUseCase(LogUsageUseCase.Request(
                userId = job.userId,
                action = UsageLogAction.AI_ANALYSIS_STARTED,
                creditsUsed = 0,
                screenshotId = null, // This is for Screenshots table, not ScreenshotJobs
                metadata = mapOf(
                    "analysisType" to job.analysisType.name,
                    "analysisJobId" to job.id,
                    "screenshotJobId" to job.screenshotJobId,
                    "language" to job.language
                )
            ))

            // Process with timeout
            withTimeout(processingTimeout) {
                processAnalysisJob(job, startTime)
            }

        } catch (e: TimeoutCancellationException) {
            logger.error("Analysis job ${job.id} timed out after ${processingTimeout.inWholeMilliseconds / 1000} seconds")
            handleJobFailure(job, "Analysis timed out", startTime)
        } catch (e: Exception) {
            logger.error("Failed to process analysis job ${job.id}", e)
            handleJobFailure(job, e.message ?: "Unknown error", startTime)
        } finally {
            currentJob.set(null)
        }
    }

    private suspend fun processAnalysisJob(job: AnalysisJob, startTime: Long) {
        try {
            // Process analysis using use case
            val result = processAnalysisUseCase(ProcessAnalysisUseCase.Request(
                analysisJobId = job.id,
                userId = job.userId
            ))

            val processingTime = System.currentTimeMillis() - startTime

            // Log success
            logger.info(
                "Analysis completed successfully: jobId=${job.id}, " +
                "processingTime=${processingTime}ms, " +
                "tokensUsed=${result.result.tokensUsed}, " +
                "costUsd=${result.result.costUsd}"
            )

            // Log completion
            logUsageUseCase(LogUsageUseCase.Request(
                userId = job.userId,
                action = UsageLogAction.AI_ANALYSIS_COMPLETED,
                creditsUsed = 0, // Credits already deducted
                screenshotId = null, // This is for Screenshots table, not ScreenshotJobs
                metadata = mapOf(
                    "analysisType" to job.analysisType.name,
                    "analysisJobId" to job.id,
                    "screenshotJobId" to job.screenshotJobId,
                    "processingTime" to processingTime.toString(),
                    "tokensUsed" to result.result.tokensUsed.toString(),
                    "costUsd" to result.result.costUsd.toString(),
                    "confidence" to ((result.result as? AnalysisResult.Success)?.confidence?.toString() ?: "0.0")
                )
            ))

            // Update metrics
            jobsSuccessful.incrementAndGet()
            metricsService.recordAnalysisProcessingTime(processingTime)
            metricsService.recordAnalysisTokensUsed(result.result.tokensUsed)
            
            // Record detailed metrics
            metricsCollector.recordJobCompletion(
                jobId = job.id,
                analysisType = job.analysisType,
                processingTimeMs = processingTime,
                tokensUsed = result.result.tokensUsed,
                costUsd = result.result.costUsd,
                confidence = (result.result as? AnalysisResult.Success)?.confidence ?: 0.0,
                status = AnalysisStatus.COMPLETED
            )

            // Send webhook if configured
            if (job.webhookUrl != null) {
                sendWebhook(job.copy(
                    status = AnalysisStatus.COMPLETED,
                    resultData = (result.result as? AnalysisResult.Success)?.data?.toString() ?: "",
                    confidence = (result.result as? AnalysisResult.Success)?.confidence ?: 0.0,
                    tokensUsed = result.result.tokensUsed,
                    costUsd = result.result.costUsd,
                    processingTimeMs = processingTime,
                    completedAt = Clock.System.now()
                ))
            }

        } catch (e: Exception) {
            throw e // Re-throw to be handled by outer try-catch
        }
    }

    private suspend fun handleJobFailure(job: AnalysisJob, errorMessage: String, startTime: Long) {
        val processingTime = System.currentTimeMillis() - startTime

        // Update job status to FAILED
        analysisJobRepository.updateStatus(job.id, AnalysisStatus.FAILED, errorMessage)

        // Log failure
        logUsageUseCase(LogUsageUseCase.Request(
            userId = job.userId,
            action = UsageLogAction.AI_ANALYSIS_FAILED,
            creditsUsed = 0, // Credits not refunded on failure
            screenshotId = null, // This is for Screenshots table, not ScreenshotJobs
            metadata = mapOf(
                "analysisType" to job.analysisType.name,
                "analysisJobId" to job.id,
                "screenshotJobId" to job.screenshotJobId,
                "error" to errorMessage,
                "processingTime" to processingTime.toString()
            )
        ))

        // Update metrics
        jobsFailed.incrementAndGet()
        metricsService.recordAnalysisFailure(job.analysisType.name)
        
        // Record detailed error metrics
        metricsCollector.recordError(
            jobId = job.id,
            analysisType = job.analysisType,
            errorCategory = dev.screenshotapi.core.domain.entities.ErrorCategory.PROCESSING,
            errorCode = "JOB_PROCESSING_FAILED",
            retryable = false,
            processingTimeMs = processingTime
        )

        // Send webhook for failure if configured
        if (job.webhookUrl != null) {
            sendWebhook(job.copy(
                status = AnalysisStatus.FAILED,
                errorMessage = errorMessage,
                processingTimeMs = processingTime,
                completedAt = Clock.System.now()
            ))
        }
    }

    private suspend fun sendWebhook(job: AnalysisJob) {
        try {
            val event = if (job.status == AnalysisStatus.COMPLETED) {
                WebhookEvent.ANALYSIS_COMPLETED
            } else {
                WebhookEvent.ANALYSIS_FAILED
            }

            notificationService.sendAnalysisWebhook(
                userId = job.userId,
                analysisJob = job,
                event = event
            )
        } catch (e: Exception) {
            logger.error("Failed to send webhook for analysis job ${job.id}", e)
            // Don't fail the job if webhook fails
        }
    }

    private fun updateHeartbeat() {
        lastHeartbeat.set(Clock.System.now())
    }

    private suspend fun handleError(e: Exception) {
        logger.error("Analysis Worker $id encountered error", e)
        consecutiveErrors.incrementAndGet()

        if (consecutiveErrors.get() >= maxConsecutiveErrors) {
            logger.error("Analysis Worker $id exceeded max consecutive errors ($maxConsecutiveErrors), entering error state")
            currentState.set(WorkerState.ERROR)
            stop()
        } else {
            // Exponential backoff
            val backoffDelay = minOf(1000L * (1 shl consecutiveErrors.get().toInt()), 60_000L)
            logger.warn("Analysis Worker $id backing off for ${backoffDelay}ms after error")
            delay(backoffDelay)
        }
    }

    // Health check
    fun isHealthy(): Boolean {
        val timeSinceLastHeartbeat = Clock.System.now() - lastHeartbeat.get()
        return isRunning.get() && 
               currentState.get() != WorkerState.ERROR &&
               timeSinceLastHeartbeat < kotlin.time.Duration.parse("2m")
    }

    fun getStats(): WorkerStats {
        return WorkerStats(
            id = id,
            state = currentState.get(),
            isHealthy = isHealthy(),
            jobsProcessed = jobsProcessed.get(),
            jobsSuccessful = jobsSuccessful.get(),
            jobsFailed = jobsFailed.get(),
            currentJob = currentJob.get()?.id,
            lastActivity = lastActivity.get(),
            uptime = Clock.System.now() - startTime
        )
    }
}

data class WorkerStats(
    val id: String,
    val state: WorkerState,
    val isHealthy: Boolean,
    val jobsProcessed: Long,
    val jobsSuccessful: Long,
    val jobsFailed: Long,
    val currentJob: String?,
    val lastActivity: Instant?,
    val uptime: Duration
)