package dev.screenshotapi.workers

import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.infrastructure.services.MetricsService
import dev.screenshotapi.infrastructure.services.NotificationService
import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class WorkerManager(
    private val queueRepository: QueueRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val userRepository: UserRepository,
    private val screenshotService: ScreenshotService,
    private val deductCreditsUseCase: DeductCreditsUseCase,
    private val logUsageUseCase: LogUsageUseCase,
    private val notificationService: NotificationService,
    private val metricsService: MetricsService,
    private val config: AppConfig
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val workers = ConcurrentHashMap<String, ScreenshotWorker>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)

    private val maxWorkers = if (config.environment.isLocal) 2 else config.screenshot.concurrentScreenshots
    private val minWorkers = if (config.environment.isLocal) 1 else 2
    private val scalingEnabled = !config.environment.isLocal

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting WorkerManager with min=$minWorkers, max=$maxWorkers workers")

            // Init min workers
            repeat(minWorkers) {
                startWorker()
            }

            // Init auto-scaling monitor (only in production)
            if (scalingEnabled) {
                scope.launch {
                    monitorAndScale()
                }
            }

            // Init health monitor
            scope.launch {
                healthMonitor()
            }

            logger.info("WorkerManager started with ${workers.size} workers")
        } else {
            logger.warn("WorkerManager is already running")
        }
    }

    fun shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Shutting down WorkerManager...")

            // Cancel coroutine scope
            scope.cancel()

            // Stop all workers gracefully
            val shutdownJobs = workers.values.map { worker ->
                scope.launch {
                    try {
                        worker.shutdown()
                    } catch (e: Exception) {
                        logger.error("Error shutting down worker ${worker.id}", e)
                    }
                }
            }

            // Wait for all workers to shutdown
            runBlocking {
                try {
                    withTimeout(30_000) {
                        shutdownJobs.joinAll()
                    }
                } catch (e: TimeoutCancellationException) {
                    logger.warn("Shutdown timeout reached, forcing worker termination")
                }
            }

            workers.clear()
            logger.info("WorkerManager shutdown completed")
        }
    }

    private fun startWorker(): String {
        val workerId = UUID.randomUUID().toString()

        val worker = ScreenshotWorker(
            id = workerId,
            queueRepository = queueRepository,
            screenshotRepository = screenshotRepository,
            userRepository = userRepository,
            screenshotService = screenshotService,
            deductCreditsUseCase = deductCreditsUseCase,
            logUsageUseCase = logUsageUseCase,
            notificationService = notificationService,
            metricsService = metricsService,
            config = config.screenshot
        )

        workers[workerId] = worker
        logger.info("Worker created: workerId={}, totalWorkers={}", workerId, workers.size)

        // Initialize worker in a separate coroutine
        scope.launch {
            try {
                logger.info("Starting worker: workerId={}", workerId)
                worker.start()
            } catch (e: Exception) {
                logger.error("Worker failed to start: workerId={}, error={}", workerId, e.message, e)
            } finally {
                workers.remove(workerId)
                logger.info("Worker removed from pool: workerId={}, remainingWorkers={}", workerId, workers.size)

                // If we're below the minimum and the manager is still running, start a new worker
                if (isRunning.get() && workers.size < minWorkers) {
                    logger.info("Worker count below minimum: current={}, min={}, starting replacement", 
                        workers.size, minWorkers)
                    delay(5000)
                    if (isRunning.get()) {
                        startWorker()
                    }
                }
            }
        }

        logger.info("Worker startup initiated: workerId={}, totalWorkers={}", workerId, workers.size)
        return workerId
    }

    private fun stopWorker(): Boolean {
        val worker = workers.values.firstOrNull()
        return if (worker != null) {
            scope.launch {
                worker.stop()
            }
            logger.info("Requested stop for worker ${worker.id}, remaining workers: ${workers.size - 1}")
            true
        } else {
            false
        }
    }

    private suspend fun monitorAndScale() {
        logger.info("Starting auto-scaling monitor")

        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                val queueSize = queueRepository.size()
                val activeWorkers = workers.size
                val queueMetrics = getQueueMetrics()

                val scalingDecision = calculateScalingDecision(queueSize, activeWorkers, queueMetrics)

                when (scalingDecision.action) {
                    ScalingAction.SCALE_UP -> {
                        if (activeWorkers < maxWorkers) {
                            startWorker()
                            logger.info("Scaled up: queue=$queueSize, workers=${workers.size}, reason=${scalingDecision.reason}")
                            metricsService.recordScaling("up", workers.size)
                        }
                    }

                    ScalingAction.SCALE_DOWN -> {
                        if (activeWorkers > minWorkers) {
                            stopWorker()
                            logger.info("Scaled down: queue=$queueSize, workers=${workers.size}, reason=${scalingDecision.reason}")
                            metricsService.recordScaling("down", workers.size)
                        }
                    }

                    ScalingAction.NO_ACTION -> {
                        // No hacer nada
                    }
                }

                metricsService.updateWorkerMetrics(workers.size, queueSize)

                delay(30_000)

            } catch (e: Exception) {
                logger.error("Error in auto-scaling monitor", e)
                delay(60_000)
            }
        }

        logger.info("Auto-scaling monitor stopped")
    }

    private suspend fun healthMonitor() {
        logger.info("Starting worker health monitor")

        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                val unhealthyWorkers = workers.values.filter { !it.isHealthy() }

                unhealthyWorkers.forEach { worker ->
                    logger.warn("Unhealthy worker detected: ${worker.id}, restarting...")

                    // Remove worker not healthy
                    workers.remove(worker.id)
                    worker.stop()

                    startWorker()

                    metricsService.recordWorkerRestart(worker.id, "unhealthy")
                }

                delay(60_000)

            } catch (e: Exception) {
                logger.error("Error in health monitor", e)
                delay(120_000)
            }
        }

        logger.info("Worker health monitor stopped")
    }

    private suspend fun getQueueMetrics(): QueueMetrics {
        return QueueMetrics(
            size = queueRepository.size(),
            averageWaitTime = calculateAverageWaitTime(),
            oldestJobAge = calculateOldestJobAge()
        )
    }

    private suspend fun calculateAverageWaitTime(): Long {
        // TODO: Implement this logic to calculate average wait time based on recently completed jobs
        return 30_000L
    }

    private suspend fun calculateOldestJobAge(): Long {
        // TODO: Implement this logic to calculate oldest job age
        return 0L
    }

    private fun calculateScalingDecision(
        queueSize: Long,
        activeWorkers: Int,
        metrics: QueueMetrics
    ): ScalingDecision {

        val jobsPerWorker = if (activeWorkers > 0) queueSize.toDouble() / activeWorkers else queueSize.toDouble()

        return when {
            // Scale up conditions
            queueSize > activeWorkers * 5 && activeWorkers < maxWorkers -> {
                ScalingDecision(ScalingAction.SCALE_UP, "High queue size: $queueSize jobs for $activeWorkers workers")
            }

            jobsPerWorker > 10 && activeWorkers < maxWorkers -> {
                ScalingDecision(ScalingAction.SCALE_UP, "High jobs per worker: $jobsPerWorker")
            }

            metrics.averageWaitTime > 60_000 && activeWorkers < maxWorkers -> {
                ScalingDecision(ScalingAction.SCALE_UP, "High average wait time: ${metrics.averageWaitTime}ms")
            }

            // Scale down conditions
            queueSize == 0L && activeWorkers > minWorkers -> {
                ScalingDecision(ScalingAction.SCALE_DOWN, "Empty queue")
            }

            jobsPerWorker < 1 && activeWorkers > minWorkers && metrics.averageWaitTime < 10_000 -> {
                ScalingDecision(ScalingAction.SCALE_DOWN, "Low jobs per worker: $jobsPerWorker")
            }

            else -> {
                ScalingDecision(ScalingAction.NO_ACTION, "Stable")
            }
        }
    }

    fun getStatus(): WorkerStatus {
        return WorkerStatus(
            activeWorkers = workers.size,
            minWorkers = minWorkers,
            maxWorkers = maxWorkers,
            workerIds = workers.keys.toList(),
            isRunning = isRunning.get(),
            scalingEnabled = scalingEnabled
        )
    }

    fun getDetailedStatus(): DetailedWorkerStatus {
        val workerStatuses = workers.values.map { worker ->
            WorkerInfo(
                id = worker.id,
                isHealthy = worker.isHealthy(),
                jobsProcessed = worker.getJobsProcessed(),
                lastActivity = worker.getLastActivity(),
                status = worker.getStatus()
            )
        }

        return DetailedWorkerStatus(
            overview = getStatus(),
            workers = workerStatuses,
            queueSize = runBlocking { queueRepository.size() },
            totalJobsProcessed = workerStatuses.sumOf { it.jobsProcessed }
        )
    }

    // Force scaling to a specific number of workers (min: minWorkers, max: maxWorkers)
    fun forceScale(targetWorkers: Int): Boolean {
        if (targetWorkers < minWorkers || targetWorkers > maxWorkers) {
            logger.warn("Cannot force scale to $targetWorkers (min: $minWorkers, max: $maxWorkers)")
            return false
        }

        val currentWorkers = workers.size

        when {
            targetWorkers > currentWorkers -> {
                repeat(targetWorkers - currentWorkers) {
                    startWorker()
                }
            }

            targetWorkers < currentWorkers -> {
                repeat(currentWorkers - targetWorkers) {
                    stopWorker()
                }
            }
        }

        logger.info("Forced scaling from $currentWorkers to $targetWorkers workers")
        return true
    }
}

// === DATA CLASSES ===

data class WorkerStatus(
    val activeWorkers: Int,
    val minWorkers: Int,
    val maxWorkers: Int,
    val workerIds: List<String>,
    val isRunning: Boolean = true,
    val scalingEnabled: Boolean = true
)

data class DetailedWorkerStatus(
    val overview: WorkerStatus,
    val workers: List<WorkerInfo>,
    val queueSize: Long,
    val totalJobsProcessed: Long
)

data class WorkerInfo(
    val id: String,
    val isHealthy: Boolean,
    val jobsProcessed: Long,
    val lastActivity: Instant?,
    val status: WorkerState
)

data class QueueMetrics(
    val size: Long,
    val averageWaitTime: Long,
    val oldestJobAge: Long
)

data class ScalingDecision(
    val action: ScalingAction,
    val reason: String
)

enum class ScalingAction {
    SCALE_UP, SCALE_DOWN, NO_ACTION
}

enum class WorkerState {
    IDLE, PROCESSING, STOPPING, STOPPED, ERROR
}
