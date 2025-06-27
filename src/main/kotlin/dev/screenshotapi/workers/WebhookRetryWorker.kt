package dev.screenshotapi.workers

import dev.screenshotapi.core.usecases.webhook.SendWebhookUseCase
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.minutes

/**
 * Worker that processes failed webhook deliveries for retry
 */
class WebhookRetryWorker(
    private val sendWebhookUseCase: SendWebhookUseCase,
    private val workerConfig: WebhookRetryWorkerConfig = WebhookRetryWorkerConfig()
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val isRunning = AtomicBoolean(false)
    private val retriesProcessed = AtomicLong(0)
    private val successfulRetries = AtomicLong(0)
    private val failedRetries = AtomicLong(0)
    
    private var workerJob: Job? = null

    data class WebhookRetryWorkerConfig(
        val pollInterval: kotlin.time.Duration = 2.minutes,
        val batchSize: Int = 50,
        val maxConsecutiveErrors: Int = 5
    )

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting webhook retry worker with config: $workerConfig")
            
            workerJob = CoroutineScope(Dispatchers.IO).launch {
                workLoop()
            }
        } else {
            logger.warn("Webhook retry worker is already running")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping webhook retry worker")
            
            workerJob?.cancel()
            workerJob = null
            
            logger.info("Webhook retry worker stopped. Stats - Total: ${retriesProcessed.get()}, " +
                    "Successful: ${successfulRetries.get()}, Failed: ${failedRetries.get()}")
        }
    }

    private suspend fun workLoop() {
        var consecutiveErrors = 0
        
        while (isRunning.get()) {
            try {
                val processedCount = processFailedDeliveries()
                
                if (processedCount > 0) {
                    logger.debug("Processed $processedCount webhook retry deliveries")
                    consecutiveErrors = 0
                } else {
                    // No work to do, wait for the poll interval
                    delay(workerConfig.pollInterval.inWholeMilliseconds)
                }
                
            } catch (e: Exception) {
                consecutiveErrors++
                logger.error("Error in webhook retry worker (consecutive: $consecutiveErrors): ${e.message}", e)
                
                if (consecutiveErrors >= workerConfig.maxConsecutiveErrors) {
                    logger.error("Webhook retry worker has $consecutiveErrors consecutive errors, stopping")
                    stop()
                    break
                }
                
                // Exponential backoff on errors
                val backoffDelay = minOf(
                    workerConfig.pollInterval.inWholeMilliseconds * consecutiveErrors,
                    30_000 // Max 30 seconds
                )
                delay(backoffDelay)
            }
        }
    }

    private suspend fun processFailedDeliveries(): Int {
        try {
            val now = Clock.System.now()
            val deliveries = sendWebhookUseCase.retryFailedDeliveries(workerConfig.batchSize)
            
            var successCount = 0
            var failureCount = 0
            
            for (delivery in deliveries) {
                retriesProcessed.incrementAndGet()
                
                try {
                    // The sendWebhookUseCase.retryFailedDeliveries already handles the retry logic
                    // We just need to track the results
                    if (delivery.status.name == "DELIVERED") {
                        successCount++
                        successfulRetries.incrementAndGet()
                    } else {
                        failureCount++
                        failedRetries.incrementAndGet()
                    }
                } catch (e: Exception) {
                    failureCount++
                    failedRetries.incrementAndGet()
                    logger.error("Failed to retry webhook delivery ${delivery.id}: ${e.message}", e)
                }
            }
            
            if (deliveries.isNotEmpty()) {
                logger.info("Webhook retry batch completed: ${deliveries.size} deliveries processed, " +
                        "$successCount successful, $failureCount failed")
            }
            
            return deliveries.size
            
        } catch (e: Exception) {
            logger.error("Error processing webhook retry batch: ${e.message}", e)
            throw e
        }
    }

    fun getStats(): Map<String, Long> {
        return mapOf(
            "isRunning" to if (isRunning.get()) 1L else 0L,
            "retriesProcessed" to retriesProcessed.get(),
            "successfulRetries" to successfulRetries.get(),
            "failedRetries" to failedRetries.get()
        )
    }
}