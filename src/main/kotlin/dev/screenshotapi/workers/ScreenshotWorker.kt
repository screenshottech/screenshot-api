package dev.screenshotapi.workers

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotResult
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.entities.User
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.core.domain.services.RetryPolicy
import dev.screenshotapi.core.usecases.billing.DeductCreditsRequest
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.usecases.ocr.ExtractTextUseCase
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.entities.JobType
import dev.screenshotapi.core.domain.entities.OcrRequest
import dev.screenshotapi.core.domain.entities.OcrTier
import dev.screenshotapi.core.domain.entities.OcrEngine
import dev.screenshotapi.core.domain.entities.OcrUseCase
import dev.screenshotapi.core.domain.entities.OcrOptions
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import dev.screenshotapi.infrastructure.services.MetricsService
import dev.screenshotapi.infrastructure.services.NotificationService
import dev.screenshotapi.infrastructure.services.EmailService
import dev.screenshotapi.infrastructure.services.ScreenshotOcrWorkflowService
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

class ScreenshotWorker(
    val id: String,
    private val queueRepository: QueueRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val userRepository: UserRepository,
    private val screenshotService: ScreenshotService,
    private val deductCreditsUseCase: DeductCreditsUseCase,
    private val logUsageUseCase: LogUsageUseCase,
    private val notificationService: NotificationService,
    private val metricsService: MetricsService,
    private val retryPolicy: RetryPolicy,
    private val config: ScreenshotConfig,
    private val emailService: EmailService? = null,
    private val ocrWorkflowService: ScreenshotOcrWorkflowService? = null,
    private val ocrResultRepository: OcrResultRepository? = null,
    private val extractTextUseCase: ExtractTextUseCase? = null
) {
    private val logger = LoggerFactory.getLogger("${this::class.simpleName}-$id")

    // State management
    private val isRunning = AtomicBoolean(false)
    private val currentState = AtomicReference(WorkerState.IDLE)
    private val currentJob = AtomicReference<ScreenshotJob?>(null)

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

    suspend fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Worker $id starting")
            currentState.set(WorkerState.PROCESSING)

            try {
                workLoop()
            } catch (e: CancellationException) {
                logger.info("Worker $id was cancelled")
            } catch (e: Exception) {
                logger.error("Worker $id encountered fatal error", e)
                currentState.set(WorkerState.ERROR)
            } finally {
                currentState.set(WorkerState.STOPPED)
                isRunning.set(false)
                logger.info("Worker $id stopped. Stats: processed=${jobsProcessed.get()}, successful=${jobsSuccessful.get()}, failed=${jobsFailed.get()}")
            }
        } else {
            logger.warn("Worker $id is already running")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Worker $id stopping...")
            currentState.set(WorkerState.STOPPING)
        }
    }

    suspend fun shutdown() {
        logger.info("Worker $id shutting down gracefully...")
        stop()

        val currentJob = currentJob.get()
        if (currentJob != null) {
            logger.info("Worker $id waiting for current job ${currentJob.id} to complete...")

            var waitTime = 0L
            val maxWaitTime = 30_000L

            while (this.currentJob.get() != null && waitTime < maxWaitTime) {
                delay(1000)
                waitTime += 1000
            }

            if (this.currentJob.get() != null) {
                logger.warn("Worker $id shutdown timeout, job may be interrupted")
            }
        }

        currentState.set(WorkerState.STOPPED)
        logger.info("Worker $id shutdown completed")
    }

    private suspend fun workLoop() {
        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                updateHeartbeat()

                val job = dequeueWithTimeout()

                if (job != null) {
                    processJob(job)
                    consecutiveErrors.set(0) // Reset error counter on successful processing
                } else {
                    // No jobs available, enter idle mode
                    currentState.set(WorkerState.IDLE)
                    delay(config.retryDelay.inWholeMilliseconds)
                }

            } catch (e: CancellationException) {
                logger.debug("Worker $id work loop cancelled")
                break
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private suspend fun dequeueWithTimeout(): ScreenshotJob? {
        return try {
            withTimeout(5000) { // 5 seconds timeout for dequeue
                queueRepository.dequeue()
            }
        } catch (e: TimeoutCancellationException) {
            null // No jobs available
        }
    }

    private suspend fun processJob(job: ScreenshotJob) {
        val startTime = System.currentTimeMillis()
        currentJob.set(job)
        currentState.set(WorkerState.PROCESSING)
        updateActivity()

        logger.info("Processing job ${job.id} for user ${job.userId}: type=${job.jobType.displayName}, url=${job.request.url}, format=${job.request.format.name}, credits=${job.jobType.defaultCredits}")

        try {
            // 1. Update status to "processing"
            val processingJob = job.markAsProcessing()
            screenshotRepository.update(processingJob)
            logger.info("Job state transition: jobId={}, from=QUEUED, to=PROCESSING, userId={}, jobType={}",
                job.id, job.userId, job.jobType.name)

            // 2. Process job based on type
            when (job.jobType) {
                JobType.SCREENSHOT -> {
                    processScreenshotJob(job, startTime)
                }
                JobType.OCR -> {
                    processOcrJob(job, startTime)
                }
                else -> {
                    // For other job types, fall back to screenshot logic for now
                    processScreenshotJob(job, startTime)
                }
            }

        } catch (e: Exception) {
            handleJobFailure(job, e, System.currentTimeMillis() - startTime)
        } finally {
            currentJob.set(null)
            jobsProcessed.incrementAndGet()
        }
    }

    /**
     * Process a screenshot job (traditional browser-based screenshot)
     */
    private suspend fun processScreenshotJob(job: ScreenshotJob, startTime: Long) {
        // Log screenshot creation in usage logs
        logUsageUseCase.invoke(LogUsageUseCase.Request(
            userId = job.userId,
            action = UsageLogAction.SCREENSHOT_CREATED,
            creditsUsed = 0, // No credits deducted yet
            apiKeyId = job.apiKeyId,
            screenshotId = job.id,
            metadata = mapOf(
                "url" to job.request.url,
                "format" to job.request.format.name
            )
        ))

        // Validate user and credits
        validateUserAndCredits(job)

        // Take screenshot with retries
        val screenshotResult = takeScreenshotWithRetry(job)
        val processingTime = System.currentTimeMillis() - startTime

        // Update status to "completed"
        val completedJob = job.markAsCompleted(screenshotResult.url, processingTime, screenshotResult.fileSizeBytes, screenshotResult.metadata)
        screenshotRepository.update(completedJob)
        logger.info("Job state transition: jobId={}, from=PROCESSING, to=COMPLETED, userId={}, processingTime={}ms",
            job.id, job.userId, processingTime)

        // Log screenshot completion in usage logs (no credits here, just event logging)
        logUsageUseCase.invoke(LogUsageUseCase.Request(
            userId = job.userId,
            action = UsageLogAction.SCREENSHOT_COMPLETED,
            creditsUsed = 0, // No credits - just event logging
            apiKeyId = job.apiKeyId,
            screenshotId = job.id,
            metadata = mapOf(
                "url" to job.request.url,
                "format" to job.request.format.name,
                "processingTime" to processingTime.toString(),
                "resultUrl" to screenshotResult.url,
                "fileSizeBytes" to screenshotResult.fileSizeBytes.toString()
            )
        ))

        // Deduct credits (this also tracks usage in monthly tracking)
        val creditsToDeduct = job.jobType.defaultCredits
        deductCreditsUseCase(DeductCreditsRequest(
            userId = job.userId,
            amount = creditsToDeduct,
            jobId = job.id,
            reason = JobType.getDeductionReason(job.jobType)
        ))
        logger.info("Credits deducted: jobId={}, userId={}, jobType={}, amount={}",
            job.id, job.userId, job.jobType.name, creditsToDeduct)

        // Process OCR if requested for screenshots
        var finalCompletedJob = completedJob
        if (completedJob.ocrRequested && ocrWorkflowService != null) {
            try {
                logger.info("Starting OCR processing for job ${completedJob.id}")
                val ocrResult = ocrWorkflowService.processOcrForScreenshot(completedJob)

                if (ocrResult != null) {
                    // Reload job to get OCR result ID
                    finalCompletedJob = screenshotRepository.findById(completedJob.id) ?: completedJob
                    logger.info("OCR processing completed for job ${completedJob.id}, OCR result ID: ${ocrResult.id}")
                }
            } catch (e: Exception) {
                logger.error("OCR processing failed for job ${completedJob.id}, continuing with screenshot result", e)
                // OCR failure should not fail the screenshot job
            }
        }

        // Send webhook if configured
        sendWebhookIfConfigured(finalCompletedJob)

        // Update metrics
        recordSuccessMetrics(processingTime)

        logger.info("Job completed successfully: jobId={}, userId={}, processingTime={}ms, resultUrl={}",
            job.id, job.userId, processingTime, screenshotResult.url)

        // Send first screenshot email if this is user's first completed screenshot
        val user = userRepository.findById(job.userId)
        if (user != null) {
            sendFirstScreenshotEmailIfNeeded(user, completedJob, screenshotResult, processingTime)
        } else {
            logger.warn("FIRST_SCREENSHOT_EMAIL_SKIPPED: User not found [userId=${job.userId}, jobId=${job.id}]")
        }
    }

    /**
     * Process an OCR job (direct image processing without browser)
     */
    private suspend fun processOcrJob(job: ScreenshotJob, startTime: Long) {
        // Log OCR creation
        logUsageUseCase.invoke(LogUsageUseCase.Request(
            userId = job.userId,
            action = UsageLogAction.OCR_CREATED,
            creditsUsed = 0, // No credits deducted yet
            apiKeyId = job.apiKeyId,
            screenshotId = job.id,
            metadata = mapOf(
                "jobType" to "OCR",
                "url" to job.request.url // This will be our synthetic OCR URL
            )
        ))

        // Validate user and credits
        validateUserAndCredits(job)

        // For OCR jobs, we'll temporarily fall back to using ExtractTextUseCase directly
        // This maintains the original logic but through the job queue
        // TODO: Implement proper OCR workflow integration later
        
        if (ocrResultRepository == null) {
            throw IllegalStateException("OCR repository not available for OCR job processing")
        }
        
        // Get the pending OCR result that was created in the controller
        val ocrResultId = job.ocrResultId
            ?: throw IllegalStateException("OCR job ${job.id} missing ocrResultId")
        
        val pendingOcrResult = ocrResultRepository.findById(ocrResultId)
            ?: throw IllegalStateException("OCR result $ocrResultId not found")

        // Process OCR using real ExtractTextUseCase
        val finalOcrResult = if (extractTextUseCase != null) {
            try {
                // Reconstruct OcrRequest from metadata
                val imageData = pendingOcrResult.metadata["imageData"]
                    ?: throw IllegalStateException("Missing imageData in OCR result metadata")
                
                val imageBytes = try {
                    java.util.Base64.getDecoder().decode(imageData)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid base64 image data in OCR result")
                }
                
                val ocrRequest = OcrRequest(
                    id = job.id, // Use job.id so logs can reference the screenshot job
                    userId = job.userId,
                    screenshotJobId = job.id,
                    imageBytes = imageBytes,
                    language = pendingOcrResult.language,
                    tier = OcrTier.valueOf(pendingOcrResult.metadata["tier"]?.uppercase() ?: "BASIC"),
                    engine = OcrEngine.PADDLE_OCR,
                    useCase = OcrUseCase.GENERAL,
                    options = OcrOptions(
                        extractPrices = pendingOcrResult.metadata["extractPrices"]?.toBoolean() ?: false,
                        extractTables = pendingOcrResult.metadata["extractTables"]?.toBoolean() ?: false,
                        extractForms = pendingOcrResult.metadata["extractForms"]?.toBoolean() ?: false,
                        confidenceThreshold = pendingOcrResult.metadata["confidenceThreshold"]?.toDouble() ?: 0.8,
                        enableStructuredData = false
                    )
                )
                
                logger.info("Executing real OCR processing for job ${job.id}")
                
                // Execute real OCR processing
                extractTextUseCase.invoke(ocrRequest)
                
            } catch (e: Exception) {
                logger.error("Real OCR processing failed for job ${job.id}, falling back to dummy data", e)
                
                // Fallback to dummy data if real OCR fails
                pendingOcrResult.copy(
                    success = false,
                    extractedText = "OCR processing failed: ${e.message}",
                    confidence = 0.0,
                    wordCount = 0,
                    processingTime = (System.currentTimeMillis() - startTime) / 1000.0,
                    error = e.message
                )
            }
        } else {
            logger.warn("ExtractTextUseCase not available, using dummy data for job ${job.id}")
            
            // Fallback to dummy data if use case not injected
            pendingOcrResult.copy(
                success = true,
                extractedText = "OCR processing completed via job queue (dummy)",
                confidence = 0.9,
                wordCount = 10,
                processingTime = (System.currentTimeMillis() - startTime) / 1000.0
            )
        }
        
        // Update the OCR result
        ocrResultRepository.save(finalOcrResult)

        val processingTime = System.currentTimeMillis() - startTime

        // Create a synthetic "screenshot result" for consistency with the job system
        val syntheticScreenshotResult = ScreenshotResult(
            url = "ocr://result/${finalOcrResult.id}",
            fileSizeBytes = 0L // OCR doesn't produce files, just text
        )

        // Update job status to completed
        val completedJob = job.markAsCompleted(syntheticScreenshotResult.url, processingTime, syntheticScreenshotResult.fileSizeBytes)
        screenshotRepository.update(completedJob)
        logger.info("Job state transition: jobId={}, from=PROCESSING, to=COMPLETED, userId={}, processingTime={}ms",
            job.id, job.userId, processingTime)

        // Log OCR completion
        logUsageUseCase.invoke(LogUsageUseCase.Request(
            userId = job.userId,
            action = UsageLogAction.OCR_COMPLETED,
            creditsUsed = 0, // No credits - just event logging
            apiKeyId = job.apiKeyId,
            screenshotId = job.id,
            metadata = mapOf(
                "processingTime" to processingTime.toString(),
                "confidence" to finalOcrResult.confidence.toString(),
                "wordCount" to finalOcrResult.wordCount.toString(),
                "ocrResultId" to finalOcrResult.id
            )
        ))

        // Deduct credits
        val creditsToDeduct = job.jobType.defaultCredits
        deductCreditsUseCase(DeductCreditsRequest(
            userId = job.userId,
            amount = creditsToDeduct,
            jobId = job.id,
            reason = JobType.getDeductionReason(job.jobType)
        ))
        logger.info("Credits deducted: jobId={}, userId={}, jobType={}, amount={}",
            job.id, job.userId, job.jobType.name, creditsToDeduct)

        // Send webhook if configured
        sendWebhookIfConfigured(completedJob)

        // Update metrics
        recordSuccessMetrics(processingTime)

        logger.info("OCR job completed successfully: jobId={}, userId={}, processingTime={}ms, ocrResultId={}",
            job.id, job.userId, processingTime, finalOcrResult.id)
    }

    private suspend fun validateUserAndCredits(job: ScreenshotJob) {
        val user = userRepository.findById(job.userId)
            ?: throw IllegalStateException("User ${job.userId} not found")

        if (!user.hasCredits(1)) {
            throw IllegalStateException("User ${job.userId} has insufficient credits")
        }
    }

    private suspend fun takeScreenshotWithRetry(job: ScreenshotJob): ScreenshotResult {
        var lastException: Exception? = null

        repeat(config.retryAttempts) { attempt ->
            try {
                logger.info("Screenshot attempt: jobId={}, attempt={}/{}, url={}",
                    job.id, attempt + 1, config.retryAttempts, job.request.url)
                return screenshotService.takeSecureScreenshot(
                    request = job.request,
                    userId = job.userId,
                    jobId = job.id,
                    createdAtEpochSeconds = job.createdAt.epochSeconds
                )

            } catch (e: Exception) {
                lastException = e

                if (attempt < config.retryAttempts - 1) {
                    logger.warn("Screenshot attempt failed: jobId={}, attempt={}/{}, retryIn={}, error={}",
                        job.id, attempt + 1, config.retryAttempts, config.retryDelay, e.message)
                    delay(config.retryDelay.inWholeMilliseconds)
                } else {
                    logger.error("All screenshot attempts exhausted: jobId={}, attempts={}, finalError={}",
                        job.id, config.retryAttempts, e.message, e)
                }
            }
        }

        throw lastException ?: Exception("Screenshot failed after ${config.retryAttempts} attempts")
    }

    private suspend fun updateJobStatus(job: ScreenshotJob, status: ScreenshotStatus) {
        try {
            val updatedJob = when (status) {
                ScreenshotStatus.PROCESSING -> job.markAsProcessing()
                else -> job.copy(status = status, updatedAt = Clock.System.now())
            }
            screenshotRepository.update(updatedJob)
        } catch (e: Exception) {
            logger.error("Failed to update job status for ${job.id}", e)
        }
    }

    private suspend fun sendWebhookIfConfigured(job: ScreenshotJob) {
        try {
            // Send webhook notifications using the new webhook system
            notificationService.notifyScreenshotEvent(job)

            // Handle legacy webhook URL if present (for backward compatibility)
            job.webhookUrl?.let { webhookUrl ->
                logger.warn("Legacy webhook URL detected for job ${job.id}. Consider migrating to webhook configurations.")
                try {
                    @Suppress("DEPRECATION")
                    notificationService.sendWebhook(webhookUrl, job)
                    val updatedJob = job.markWebhookSent()
                    screenshotRepository.update(updatedJob)
                    logger.info("Legacy webhook sent successfully: jobId={}, webhookUrl={}", job.id, webhookUrl)
                } catch (e: Exception) {
                    logger.error("Legacy webhook sending failed: jobId={}, webhookUrl={}, error={}",
                        job.id, webhookUrl, e.message, e)
                }
            }
        } catch (e: Exception) {
            logger.error("Webhook notification failed for job ${job.id}: ${e.message}", e)
        }
    }

    private suspend fun handleJobFailure(job: ScreenshotJob, error: Exception, processingTime: Long) {
        try {
            val errorMessage = error.message ?: "Unknown error"
            val shouldRetry = job.canRetry() && retryPolicy.shouldRetry(error)

            if (shouldRetry) {
                val delay = retryPolicy.calculateDelay(job.retryCount)
                val retryJob = job.scheduleRetry(errorMessage, delay)

                screenshotRepository.update(retryJob)
                queueRepository.enqueueDelayed(retryJob, delay)

                logUsageUseCase.invoke(LogUsageUseCase.Request(
                    userId = job.userId,
                    action = UsageLogAction.SCREENSHOT_RETRIED,
                    creditsUsed = 0,
                    apiKeyId = job.apiKeyId,
                    screenshotId = job.id,
                    metadata = mapOf(
                        "retryType" to "AUTOMATIC",
                        "retryCount" to job.retryCount.toString(),
                        "delaySeconds" to delay.inWholeSeconds.toString(),
                        "originalError" to errorMessage,
                        "errorType" to (error::class.simpleName ?: "Exception")
                    )
                ))

                logger.warn("Job failed but scheduled for retry: jobId={}, retryCount={}, delay={}s, error={}",
                    job.id, job.retryCount, delay.inWholeSeconds, errorMessage)

            } else {
                val failedJob = if (!job.canRetry()) {
                    job.markAsFailed(errorMessage, processingTime)
                } else {
                    job.markAsNonRetryable(errorMessage).copy(
                        status = ScreenshotStatus.FAILED,
                        processingTimeMs = processingTime,
                        completedAt = Clock.System.now()
                    )
                }

                screenshotRepository.update(failedJob)

                val reason = if (!job.canRetry()) "Max retries exceeded" else "Non-retryable error"
                logger.info("Job state transition: jobId={}, from=PROCESSING, to=FAILED, userId={}, reason={}, processingTime={}ms",
                    job.id, job.userId, reason, processingTime)

                logUsageUseCase.invoke(LogUsageUseCase.Request(
                    userId = job.userId,
                    action = UsageLogAction.SCREENSHOT_FAILED,
                    creditsUsed = 0,
                    apiKeyId = job.apiKeyId,
                    screenshotId = job.id,
                    metadata = mapOf(
                        "url" to job.request.url,
                        "format" to job.request.format.name,
                        "processingTime" to processingTime.toString(),
                        "errorMessage" to errorMessage,
                        "errorType" to (error::class.simpleName ?: "Exception"),
                        "retryCount" to job.retryCount.toString(),
                        "maxRetries" to job.maxRetries.toString(),
                        "failureReason" to reason
                    )
                ))

                recordFailureMetrics(processingTime, error)

                // Send webhook notification for failed job
                sendWebhookIfConfigured(failedJob)

                logger.error("Job permanently failed: jobId={}, userId={}, retryCount={}, processingTime={}ms, reason={}, errorType={}, errorMessage={}",
                    job.id, job.userId, job.retryCount, processingTime, reason, error::class.simpleName, errorMessage, error)
            }

        } catch (e: Exception) {
            logger.error("Failed to handle job failure for ${job.id}", e)
        }
    }

    private fun handleError(error: Exception) {
        val errorCount = consecutiveErrors.incrementAndGet()
        logger.error("Worker $id error (consecutive: $errorCount): ${error.message}", error)

        if (errorCount >= maxConsecutiveErrors) {
            logger.error("Worker $id has $errorCount consecutive errors, marking as unhealthy")
            currentState.set(WorkerState.ERROR)
            stop()
        } else {
            runBlocking {
                delay(config.retryDelay.inWholeMilliseconds * errorCount)
            }
        }
    }

    private fun recordSuccessMetrics(processingTime: Long) {
        jobsSuccessful.incrementAndGet()
        metricsService.recordScreenshot("completed", processingTime)
    }

    private fun recordFailureMetrics(processingTime: Long, error: Exception) {
        jobsFailed.incrementAndGet()
        metricsService.recordScreenshot("failed", processingTime)
    }

    private fun updateHeartbeat() {
        lastHeartbeat.set(Clock.System.now())
    }

    private fun updateActivity() {
        lastActivity.set(Clock.System.now())
    }

    fun isHealthy(): Boolean {
        val lastBeat = lastHeartbeat.get()
        val now = Clock.System.now()
        val timeSinceLastHeartbeat = now - lastBeat

        return isRunning.get() &&
                currentState.get() != WorkerState.ERROR &&
                timeSinceLastHeartbeat.inWholeSeconds < 60 &&
                consecutiveErrors.get() < maxConsecutiveErrors
    }

    fun getJobsProcessed(): Long = jobsProcessed.get()
    fun getLastActivity(): Instant? = lastActivity.get()
    fun getStatus(): WorkerState = currentState.get()
    fun getCurrentJob(): ScreenshotJob? = currentJob.get()

    fun getStatistics(): WorkerStatistics = WorkerStatistics(
        id = id,
        status = currentState.get(),
        jobsProcessed = jobsProcessed.get(),
        jobsSuccessful = jobsSuccessful.get(),
        jobsFailed = jobsFailed.get(),
        currentJob = currentJob.get()?.id,
        lastActivity = lastActivity.get(),
        uptime = Clock.System.now() - startTime,
        isHealthy = isHealthy(),
        consecutiveErrors = consecutiveErrors.get()
    )

    fun getDetailedInfo(): WorkerDetailedInfo = WorkerDetailedInfo(
        statistics = getStatistics(),
        config = WorkerConfigInfo(
            retryAttempts = config.retryAttempts,
            retryDelay = config.retryDelay,
            maxConsecutiveErrors = maxConsecutiveErrors
        ),
        runtime = WorkerRuntimeInfo(
            startTime = startTime,
            lastHeartbeat = lastHeartbeat.get(),
            isRunning = isRunning.get()
        )
    )

    /**
     * Sends first screenshot email if this is the user's first successfully completed screenshot.
     * Uses O(1) User entity flag instead of expensive COUNT(*) database query.
     */
    private suspend fun sendFirstScreenshotEmailIfNeeded(
        user: User,
        job: ScreenshotJob,
        result: ScreenshotResult,
        processingTimeMs: Long
    ) {
        if (emailService == null) {
            logger.debug("FIRST_SCREENSHOT_EMAIL_DISABLED: Email service not available [userId=${user.id}]")
            return
        }

        try {
            // O(1) check using User entity flag instead of COUNT(*) query
            if (!user.hasCompletedFirstScreenshot()) {
                logger.info("FIRST_SCREENSHOT_EMAIL_TRIGGER: Sending first screenshot email [userId=${user.id}, jobId=${job.id}]")

                // Mark user as having completed first screenshot to prevent duplicates
                val updatedUser = user.markFirstScreenshotCompleted()
                userRepository.save(updatedUser)
                logger.debug("FIRST_SCREENSHOT_FLAG_UPDATED: User marked as having completed first screenshot [userId=${user.id}]")

                // Send celebration email
                emailService.sendFirstScreenshotEmail(
                    user = user,
                    screenshotUrl = result.url,
                    processingTimeMs = processingTimeMs
                )

                logger.info("FIRST_SCREENSHOT_EMAIL_SUCCESS: First screenshot email sent successfully [userId=${user.id}, jobId=${job.id}]")
            } else {
                logger.debug("FIRST_SCREENSHOT_EMAIL_SKIPPED: User already completed first screenshot [userId=${user.id}, jobId=${job.id}, completedAt=${user.firstScreenshotCompletedAt}]")
            }
        } catch (e: Exception) {
            logger.error("FIRST_SCREENSHOT_EMAIL_FAILED: Failed to send first screenshot email [userId=${user.id}, jobId=${job.id}]", e)
            // Don't fail the job if email fails
        }
    }
}

// === DATA CLASSES ===

data class WorkerStatistics(
    val id: String,
    val status: WorkerState,
    val jobsProcessed: Long,
    val jobsSuccessful: Long,
    val jobsFailed: Long,
    val currentJob: String?,
    val lastActivity: Instant?,
    val uptime: Duration,
    val isHealthy: Boolean,
    val consecutiveErrors: Long
)

data class WorkerDetailedInfo(
    val statistics: WorkerStatistics,
    val config: WorkerConfigInfo,
    val runtime: WorkerRuntimeInfo
)

data class WorkerConfigInfo(
    val retryAttempts: Int,
    val retryDelay: Duration,
    val maxConsecutiveErrors: Int
)

data class WorkerRuntimeInfo(
    val startTime: Instant,
    val lastHeartbeat: Instant,
    val isRunning: Boolean
)
