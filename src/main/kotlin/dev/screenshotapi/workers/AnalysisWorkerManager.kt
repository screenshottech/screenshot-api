package dev.screenshotapi.workers

import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import dev.screenshotapi.core.domain.repositories.AnalysisJobQueueRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.analysis.ProcessAnalysisUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.infrastructure.config.AnalysisConfig
import dev.screenshotapi.infrastructure.services.MetricsService
import dev.screenshotapi.infrastructure.services.NotificationService
import dev.screenshotapi.infrastructure.services.AnalysisMetricsCollector
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Analysis Worker Manager - Manages a pool of analysis workers
 * 
 * This manager handles:
 * - Worker lifecycle management
 * - Auto-scaling based on queue depth
 * - Health monitoring and recovery
 * - Graceful shutdown
 * - Metrics collection
 */
class AnalysisWorkerManager(
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
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val workers = ConcurrentHashMap<String, AnalysisWorker>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)

    // Configuration
    private val maxWorkers = config.analysisWorker.maxWorkers
    private val minWorkers = config.analysisWorker.minWorkers
    private val scalingEnabled = config.analysisWorker.autoScalingEnabled

    // Scaling thresholds
    private val scaleUpThreshold = 5 // Jobs per worker to trigger scale up
    private val scaleDownThreshold = 1 // Jobs per worker to trigger scale down
    private val scalingCheckInterval = 30.seconds

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting AnalysisWorkerManager with min=$minWorkers, max=$maxWorkers workers")

            // Initialize minimum workers
            repeat(minWorkers) {
                startWorker()
            }

            // Start auto-scaling monitor (only in production)
            if (scalingEnabled) {
                scope.launch {
                    scalingMonitor()
                }
            }

            // Start health monitor
            scope.launch {
                healthMonitor()
            }

            // Start metrics reporter
            scope.launch {
                metricsReporter()
            }

            logger.info("AnalysisWorkerManager started with ${workers.size} workers")
        } else {
            logger.warn("AnalysisWorkerManager is already running")
        }
    }

    fun shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Shutting down AnalysisWorkerManager...")

            runBlocking {
                try {
                    withTimeout(30.seconds) {
                        // Stop all workers gracefully
                        workers.values.forEach { worker ->
                            launch { worker.shutdown() }
                        }
                        
                        // Wait for all workers to stop
                        while (workers.values.any { it.isHealthy() }) {
                            delay(1000)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    logger.warn("Timeout waiting for workers to shutdown gracefully")
                }

                // Cancel the scope
                scope.cancel()
                workers.clear()
            }

            logger.info("AnalysisWorkerManager shutdown completed")
        }
    }

    private fun startWorker(): AnalysisWorker? {
        if (workers.size >= maxWorkers) {
            logger.warn("Cannot start worker: maximum workers ($maxWorkers) reached")
            return null
        }

        val workerId = "analysis-${UUID.randomUUID().toString().take(8)}"
        val worker = AnalysisWorker(
            id = workerId,
            analysisJobRepository = analysisJobRepository,
            analysisJobQueueRepository = analysisJobQueueRepository,
            userRepository = userRepository,
            processAnalysisUseCase = processAnalysisUseCase,
            logUsageUseCase = logUsageUseCase,
            notificationService = notificationService,
            metricsService = metricsService,
            metricsCollector = metricsCollector,
            config = config,
            analysisConfig = analysisConfig
        )

        workers[workerId] = worker

        scope.launch {
            try {
                worker.start()
            } catch (e: Exception) {
                logger.error("Worker $workerId failed", e)
                workers.remove(workerId)
            }
        }

        logger.info("Started analysis worker: $workerId (total: ${workers.size})")
        return worker
    }

    private fun stopWorker(workerId: String) {
        val worker = workers.remove(workerId)
        if (worker != null) {
            scope.launch {
                worker.shutdown()
                logger.info("Stopped analysis worker: $workerId (remaining: ${workers.size})")
            }
        }
    }

    private suspend fun scalingMonitor() {
        logger.info("Analysis worker auto-scaling monitor started")
        
        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                val queueDepth = getQueueDepth()
                val activeWorkers = workers.values.count { it.isHealthy() }
                
                if (activeWorkers > 0) {
                    val jobsPerWorker = queueDepth.toDouble() / activeWorkers
                    
                    when {
                        jobsPerWorker > scaleUpThreshold && activeWorkers < maxWorkers -> {
                            logger.info("Scaling up: queueDepth=$queueDepth, workers=$activeWorkers, ratio=$jobsPerWorker")
                            startWorker()
                        }
                        
                        jobsPerWorker < scaleDownThreshold && activeWorkers > minWorkers -> {
                            logger.info("Scaling down: queueDepth=$queueDepth, workers=$activeWorkers, ratio=$jobsPerWorker")
                            // Stop the oldest idle worker
                            val idleWorker = workers.values.find { 
                                it.getStats().state == WorkerState.IDLE 
                            }
                            idleWorker?.let { stopWorker(it.id) }
                        }
                    }
                }
                
                delay(scalingCheckInterval)
                
            } catch (e: Exception) {
                logger.error("Error in scaling monitor", e)
                delay(30.seconds)
            }
        }
    }

    private suspend fun healthMonitor() {
        logger.info("Analysis worker health monitor started")
        
        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                val unhealthyWorkers = workers.filter { (_, worker) -> !worker.isHealthy() }
                
                unhealthyWorkers.forEach { (workerId, worker) ->
                    logger.warn("Unhealthy worker detected: $workerId, restarting...")
                    stopWorker(workerId)
                    
                    // Start replacement worker
                    startWorker()
                }
                
                // Ensure minimum workers
                val healthyWorkers = workers.values.count { it.isHealthy() }
                if (healthyWorkers < minWorkers) {
                    val needed = minWorkers - healthyWorkers
                    logger.warn("Below minimum workers, starting $needed additional workers")
                    repeat(needed) {
                        startWorker()
                    }
                }
                
                delay(1.minutes)
                
            } catch (e: Exception) {
                logger.error("Error in health monitor", e)
                delay(30.seconds)
            }
        }
    }

    private suspend fun metricsReporter() {
        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                reportMetrics()
                delay(1.minutes)
            } catch (e: Exception) {
                logger.error("Error reporting metrics", e)
                delay(30.seconds)
            }
        }
    }

    private suspend fun getQueueDepth(): Int {
        return try {
            analysisJobRepository.countByUserId("", dev.screenshotapi.core.domain.entities.AnalysisStatus.QUEUED).toInt()
        } catch (e: Exception) {
            logger.error("Failed to get queue depth", e)
            0
        }
    }

    private fun reportMetrics() {
        val stats = getManagerStats()
        
        metricsService.recordAnalysisWorkerCount(stats.totalWorkers)
        metricsService.recordAnalysisWorkerHealthy(stats.healthyWorkers)
        metricsService.recordAnalysisQueueDepth(stats.queueDepth)
        
        // Report individual worker metrics
        workers.values.forEach { worker ->
            val workerStats = worker.getStats()
            metricsService.recordAnalysisWorkerStats(
                workerId = workerStats.id,
                processed = workerStats.jobsProcessed,
                successful = workerStats.jobsSuccessful,
                failed = workerStats.jobsFailed
            )
        }
    }

    fun getManagerStats(): AnalysisManagerStats {
        val workerStats = workers.values.map { it.getStats() }
        val queueDepth = runBlocking { getQueueDepth() }
        
        return AnalysisManagerStats(
            totalWorkers = workers.size,
            healthyWorkers = workerStats.count { it.isHealthy },
            activeWorkers = workerStats.count { it.state == WorkerState.PROCESSING },
            idleWorkers = workerStats.count { it.state == WorkerState.IDLE },
            queueDepth = queueDepth,
            totalJobsProcessed = workerStats.sumOf { it.jobsProcessed },
            totalJobsSuccessful = workerStats.sumOf { it.jobsSuccessful },
            totalJobsFailed = workerStats.sumOf { it.jobsFailed },
            workers = workerStats
        )
    }

    // Health check for the manager itself
    fun isHealthy(): Boolean {
        val healthyWorkers = workers.values.count { it.isHealthy() }
        return isRunning.get() && healthyWorkers >= minWorkers
    }

}

data class AnalysisManagerStats(
    val totalWorkers: Int,
    val healthyWorkers: Int,
    val activeWorkers: Int,
    val idleWorkers: Int,
    val queueDepth: Int,
    val totalJobsProcessed: Long,
    val totalJobsSuccessful: Long,
    val totalJobsFailed: Long,
    val workers: List<WorkerStats>
) {
    val successRate: Double
        get() = if (totalJobsProcessed > 0) {
            (totalJobsSuccessful.toDouble() / totalJobsProcessed.toDouble()) * 100
        } else 0.0
}