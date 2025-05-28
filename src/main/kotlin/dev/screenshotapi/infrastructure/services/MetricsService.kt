package dev.screenshotapi.infrastructure.services

import org.slf4j.LoggerFactory

class MetricsService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun recordScreenshot(status: String, processingTime: Long) {
        // TODO: Registrar en sistema de métricas real (Prometheus, etc.)
        logger.debug("Screenshot recorded: status=$status, time=${processingTime}ms")
    }

    fun recordScaling(direction: String, newWorkerCount: Int) {
        logger.info("Worker scaling: direction=$direction, workers=$newWorkerCount")
    }

    fun updateWorkerMetrics(workerCount: Int, queueSize: Long) {
        logger.debug("Worker metrics: workers=$workerCount, queue=$queueSize")
    }

    fun recordWorkerRestart(workerId: String, reason: String) {
        logger.warn("Worker restart: id=$workerId, reason=$reason")
    }
}
