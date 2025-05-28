package dev.screenshotapi.workers

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.services.ScreenshotService
import dev.screenshotapi.core.usecases.billing.DeductCreditsRequest
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import dev.screenshotapi.infrastructure.services.MetricsService
import dev.screenshotapi.infrastructure.services.NotificationService
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
    private val notificationService: NotificationService,
    private val metricsService: MetricsService,
    private val config: ScreenshotConfig
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

        // Marcar para parar
        stop()

        // Esperar a que termine el trabajo actual (con timeout)
        val currentJob = currentJob.get()
        if (currentJob != null) {
            logger.info("Worker $id waiting for current job ${currentJob.id} to complete...")

            var waitTime = 0L
            val maxWaitTime = 30_000L // 30 segundos

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
                    // No hay trabajos, entrar en modo idle
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
            withTimeout(5000) { // 5 segundos timeout para dequeue
                queueRepository.dequeue()
            }
        } catch (e: TimeoutCancellationException) {
            null // No hay trabajos disponibles
        }
    }

    private suspend fun processJob(job: ScreenshotJob) {
        val startTime = System.currentTimeMillis()
        currentJob.set(job)
        currentState.set(WorkerState.PROCESSING)
        updateActivity()

        logger.info("Processing job ${job.id} for user ${job.userId}")

        try {
            // 1. Actualizar estado a "processing"
            updateJobStatus(job, ScreenshotStatus.PROCESSING)

            // 2. Validar usuario y créditos
            validateUserAndCredits(job)

            // 3. Tomar screenshotapi con reintentos
            val resultUrl = takeScreenshotWithRetry(job)
            val processingTime = System.currentTimeMillis() - startTime

            // 4. Actualizar estado a "completed"
            val completedJob = job.copy(
                status = ScreenshotStatus.COMPLETED,
                resultUrl = resultUrl,
                processingTimeMs = processingTime,
                completedAt = Clock.System.now()
            )
            screenshotRepository.update(completedJob)

            // 5. Deducir créditos
            deductCreditsUseCase(DeductCreditsRequest(job.userId, 1))

            // 6. Enviar webhook si está configurado
            sendWebhookIfConfigured(completedJob)

            // 7. Actualizar métricas
            recordSuccessMetrics(processingTime)

            logger.info("Job ${job.id} completed successfully in ${processingTime}ms")

        } catch (e: Exception) {
            handleJobFailure(job, e, System.currentTimeMillis() - startTime)
        } finally {
            currentJob.set(null)
            jobsProcessed.incrementAndGet()
        }
    }

    private suspend fun validateUserAndCredits(job: ScreenshotJob) {
        val user = userRepository.findById(job.userId)
            ?: throw IllegalStateException("User ${job.userId} not found")

        if (!user.hasCredits(1)) {
            throw IllegalStateException("User ${job.userId} has insufficient credits")
        }
    }

    private suspend fun takeScreenshotWithRetry(job: ScreenshotJob): String {
        var lastException: Exception? = null

        repeat(config.retryAttempts) { attempt ->
            try {
                logger.debug("Screenshot attempt ${attempt + 1}/${config.retryAttempts} for job ${job.id}")
                return screenshotService.takeScreenshot(job.request)

            } catch (e: Exception) {
                lastException = e

                if (attempt < config.retryAttempts - 1) {
                    logger.warn("Screenshot attempt ${attempt + 1} failed for job ${job.id}, retrying in ${config.retryDelay}: ${e.message}")
                    delay(config.retryDelay.inWholeMilliseconds)
                } else {
                    logger.error("All screenshotapi attempts failed for job ${job.id}", e)
                }
            }
        }

        throw lastException ?: Exception("Screenshot failed after ${config.retryAttempts} attempts")
    }

    private suspend fun updateJobStatus(job: ScreenshotJob, status: ScreenshotStatus) {
        try {
            val updatedJob = job.copy(
                status = status,
                updatedAt = Clock.System.now()
            )
            screenshotRepository.update(updatedJob)
        } catch (e: Exception) {
            logger.error("Failed to update job status for ${job.id}", e)
            // No re-lanzar la excepción para no interrumpir el procesamiento
        }
    }

    private suspend fun sendWebhookIfConfigured(job: ScreenshotJob) {
        job.webhookUrl?.let { webhookUrl ->
            try {
                notificationService.sendWebhook(webhookUrl, job)

                // Marcar webhook como enviado
                val updatedJob = job.copy(webhookSent = true)
                screenshotRepository.update(updatedJob)

            } catch (e: Exception) {
                logger.error("Failed to send webhook for job ${job.id}", e)
                // No fallar el trabajo por un webhook fallido
            }
        }
    }

    private suspend fun handleJobFailure(job: ScreenshotJob, error: Exception, processingTime: Long) {
        try {
            val failedJob = job.copy(
                status = ScreenshotStatus.FAILED,
                errorMessage = error.message ?: "Unknown error",
                processingTimeMs = processingTime,
                completedAt = Clock.System.now()
            )

            screenshotRepository.update(failedJob)
            recordFailureMetrics(processingTime, error)

            logger.error("Job ${job.id} failed after ${processingTime}ms: ${error.message}", error)

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
            // Esperar más tiempo antes del siguiente intento
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

    // === PUBLIC STATUS METHODS ===

    fun isHealthy(): Boolean {
        val now = Clock.System.now()
        val lastBeat = lastHeartbeat.get()
        val timeSinceHeartbeat = now - lastBeat

        return when {
            !isRunning.get() -> false
            currentState.get() == WorkerState.ERROR -> false
            consecutiveErrors.get() >= maxConsecutiveErrors -> false
            timeSinceHeartbeat.inWholeSeconds > 120 -> false // 2 minutos sin heartbeat
            else -> true
        }
    }

    fun getJobsProcessed(): Long = jobsProcessed.get()

    fun getLastActivity(): Instant? = lastActivity.get()

    fun getStatus(): WorkerState = currentState.get()

    fun getCurrentJob(): ScreenshotJob? = currentJob.get()

    fun getStatistics(): WorkerStatistics {
        val now = Clock.System.now()
        val uptime = now - startTime
        val processed = jobsProcessed.get()
        val successful = jobsSuccessful.get()
        val failed = jobsFailed.get()

        return WorkerStatistics(
            id = id,
            state = currentState.get(),
            isHealthy = isHealthy(),
            uptime = uptime,
            jobsProcessed = processed,
            jobsSuccessful = successful,
            jobsFailed = failed,
            successRate = if (processed > 0) (successful.toDouble() / processed.toDouble()) else 0.0,
            consecutiveErrors = consecutiveErrors.get(),
            currentJob = currentJob.get()?.id,
            lastActivity = lastActivity.get(),
            lastHeartbeat = lastHeartbeat.get()
        )
    }

    fun getDetailedInfo(): WorkerDetailedInfo {
        return WorkerDetailedInfo(
            statistics = getStatistics(),
            config = WorkerConfigInfo(
                retryAttempts = config.retryAttempts,
                retryDelay = config.retryDelay.inWholeMilliseconds,
                maxTimeout = config.maxTimeoutDuration.inWholeMilliseconds
            ),
            runtime = WorkerRuntimeInfo(
                threadName = Thread.currentThread().name,
                isRunning = isRunning.get(),
                startTime = startTime
            )
        )
    }
}

// === DATA CLASSES ===

data class WorkerStatistics(
    val id: String,
    val state: WorkerState,
    val isHealthy: Boolean,
    val uptime: Duration,
    val jobsProcessed: Long,
    val jobsSuccessful: Long,
    val jobsFailed: Long,
    val successRate: Double,
    val consecutiveErrors: Long,
    val currentJob: String?,
    val lastActivity: Instant?,
    val lastHeartbeat: Instant
)

data class WorkerDetailedInfo(
    val statistics: WorkerStatistics,
    val config: WorkerConfigInfo,
    val runtime: WorkerRuntimeInfo
)

data class WorkerConfigInfo(
    val retryAttempts: Int,
    val retryDelay: Long,
    val maxTimeout: Long
)

data class WorkerRuntimeInfo(
    val threadName: String,
    val isRunning: Boolean,
    val startTime: Instant
)
