package dev.screenshotapi.workers

import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.usecases.screenshot.ProcessFailedRetryableJobsUseCase
import dev.screenshotapi.core.usecases.screenshot.ProcessStuckJobsUseCase
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Scheduled background processor for handling job retry logic.
 * Runs periodic tasks to:
 * 1. Process stuck jobs and recover them
 * 2. Process failed jobs that are ready for automatic retry
 * 3. Move delayed retry jobs to the main queue when ready
 */
class JobRetryScheduler(
    private val processStuckJobsUseCase: ProcessStuckJobsUseCase,
    private val processFailedRetryableJobsUseCase: ProcessFailedRetryableJobsUseCase,
    private val queueRepository: QueueRepository,
    private val stuckJobsInterval: Duration = 5.minutes, // Check for stuck jobs every 5 minutes
    private val retryJobsInterval: Duration = 30.seconds, // Check for retryable jobs every 30 seconds
    private val delayedJobsInterval: Duration = 10.seconds // Check for delayed jobs every 10 seconds
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val isRunning = AtomicBoolean(false)
    private var schedulerJob: Job? = null

    // Metrics
    private val stuckJobsProcessed = AtomicLong(0)
    private val retryJobsProcessed = AtomicLong(0)
    private val delayedJobsProcessed = AtomicLong(0)
    private val lastStuckJobsRun = AtomicLong(0)
    private val lastRetryJobsRun = AtomicLong(0)
    private val lastDelayedJobsRun = AtomicLong(0)

    suspend fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Job Retry Scheduler starting...")

            schedulerJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                try {
                    runScheduledTasks()
                } catch (e: CancellationException) {
                    logger.info("Job Retry Scheduler was cancelled")
                } catch (e: Exception) {
                    logger.error("Job Retry Scheduler encountered fatal error", e)
                } finally {
                    isRunning.set(false)
                    logger.info("Job Retry Scheduler stopped")
                }
            }
        } else {
            logger.warn("Job Retry Scheduler is already running")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Job Retry Scheduler stopping...")
            schedulerJob?.cancel()
        }
    }

    suspend fun shutdown() {
        logger.info("Job Retry Scheduler shutting down gracefully...")
        stop()
        schedulerJob?.join()
        logger.info("Job Retry Scheduler shutdown completed")
    }

    private suspend fun runScheduledTasks() {
        var lastStuckJobsCheck = Clock.System.now()
        var lastRetryJobsCheck = Clock.System.now()
        var lastDelayedJobsCheck = Clock.System.now()

        while (isRunning.get() && currentCoroutineContext().isActive) {
            try {
                val now = Clock.System.now()

                // 1. Process stuck jobs (every stuckJobsInterval)
                if (now - lastStuckJobsCheck >= stuckJobsInterval) {
                    processStuckJobs()
                    lastStuckJobsCheck = now
                }

                // 2. Process failed retryable jobs (every retryJobsInterval)
                if (now - lastRetryJobsCheck >= retryJobsInterval) {
                    processRetryableJobs()
                    lastRetryJobsCheck = now
                }

                // 3. Process delayed retry jobs (every delayedJobsInterval)
                if (now - lastDelayedJobsCheck >= delayedJobsInterval) {
                    processDelayedJobs()
                    lastDelayedJobsCheck = now
                }

                // Sleep for the shortest interval to maintain responsiveness
                val sleepDuration = minOf(delayedJobsInterval, retryJobsInterval, stuckJobsInterval)
                delay(sleepDuration.inWholeMilliseconds / 4) // Check 4x more frequently for responsiveness

            } catch (e: CancellationException) {
                logger.debug("Scheduled tasks cancelled")
                break
            } catch (e: Exception) {
                logger.error("Error in scheduled task execution", e)
                delay(10.seconds.inWholeMilliseconds) // Back off on error
            }
        }
    }

    private suspend fun processStuckJobs() {
        try {
            val startTime = System.currentTimeMillis()
            lastStuckJobsRun.set(startTime)

            logger.debug("Processing stuck jobs...")
            val result = processStuckJobsUseCase()

            if (result.processedJobs > 0) {
                stuckJobsProcessed.addAndGet(result.processedJobs.toLong())
                logger.info("Processed ${result.processedJobs} stuck jobs: retried=${result.retriedJobs}, failed=${result.failedJobs}")
            } else {
                logger.debug("No stuck jobs found")
            }

            val duration = System.currentTimeMillis() - startTime
            logger.debug("Stuck jobs processing completed in ${duration}ms")

        } catch (e: Exception) {
            logger.error("Error processing stuck jobs", e)
        }
    }

    private suspend fun processRetryableJobs() {
        try {
            val startTime = System.currentTimeMillis()
            lastRetryJobsRun.set(startTime)

            logger.debug("Processing failed retryable jobs...")
            val result = processFailedRetryableJobsUseCase()

            if (result.processedJobs > 0) {
                retryJobsProcessed.addAndGet(result.processedJobs.toLong())
                logger.info("Processed ${result.processedJobs} failed retryable jobs: retried=${result.retriedJobs}, marked non-retryable=${result.markedNonRetryableJobs}, skipped=${result.skippedJobs}")
            } else {
                logger.debug("No failed retryable jobs found")
            }

            val duration = System.currentTimeMillis() - startTime
            logger.debug("Failed retryable jobs processing completed in ${duration}ms")

        } catch (e: Exception) {
            logger.error("Error processing failed retryable jobs", e)
        }
    }

    private suspend fun processDelayedJobs() {
        try {
            val startTime = System.currentTimeMillis()
            lastDelayedJobsRun.set(startTime)

            logger.debug("Processing delayed retry jobs...")
            val readyJobs = queueRepository.dequeueReadyRetries()

            if (readyJobs.isNotEmpty()) {
                delayedJobsProcessed.addAndGet(readyJobs.size.toLong())
                logger.info("Moved ${readyJobs.size} delayed jobs to main queue")
            } else {
                logger.debug("No delayed jobs ready for processing")
            }

            val duration = System.currentTimeMillis() - startTime
            logger.debug("Delayed jobs processing completed in ${duration}ms")

        } catch (e: Exception) {
            logger.error("Error processing delayed jobs", e)
        }
    }

    fun getStatistics(): JobRetrySchedulerStatistics = JobRetrySchedulerStatistics(
        isRunning = isRunning.get(),
        stuckJobsProcessed = stuckJobsProcessed.get(),
        retryJobsProcessed = retryJobsProcessed.get(),
        delayedJobsProcessed = delayedJobsProcessed.get(),
        lastStuckJobsRun = if (lastStuckJobsRun.get() > 0) Instant.fromEpochMilliseconds(lastStuckJobsRun.get()) else null,
        lastRetryJobsRun = if (lastRetryJobsRun.get() > 0) Instant.fromEpochMilliseconds(lastRetryJobsRun.get()) else null,
        lastDelayedJobsRun = if (lastDelayedJobsRun.get() > 0) Instant.fromEpochMilliseconds(lastDelayedJobsRun.get()) else null,
        intervalConfig = IntervalConfig(
            stuckJobsInterval = stuckJobsInterval,
            retryJobsInterval = retryJobsInterval,
            delayedJobsInterval = delayedJobsInterval
        )
    )
}

data class JobRetrySchedulerStatistics(
    val isRunning: Boolean,
    val stuckJobsProcessed: Long,
    val retryJobsProcessed: Long,
    val delayedJobsProcessed: Long,
    val lastStuckJobsRun: kotlinx.datetime.Instant?,
    val lastRetryJobsRun: kotlinx.datetime.Instant?,
    val lastDelayedJobsRun: kotlinx.datetime.Instant?,
    val intervalConfig: IntervalConfig
)

data class IntervalConfig(
    val stuckJobsInterval: Duration,
    val retryJobsInterval: Duration,
    val delayedJobsInterval: Duration
)
