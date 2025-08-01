package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.entities.AnalysisStatus
import dev.screenshotapi.core.domain.entities.ErrorCategory
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Analysis Metrics Collector - Enhanced metrics for analysis operations
 * 
 * Provides detailed metrics for:
 * - Processing performance by analysis type
 * - Error tracking with categorization
 * - Token usage and cost tracking
 * - Worker performance metrics
 * - Queue depth and latency
 */
class AnalysisMetricsCollector(
    private val metricsService: MetricsService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    // Per-type metrics
    private val processingTimeByType = ConcurrentHashMap<AnalysisType, MutableList<Long>>()
    private val successRateByType = ConcurrentHashMap<AnalysisType, SuccessRateTracker>()
    private val tokenUsageByType = ConcurrentHashMap<AnalysisType, AtomicLong>()
    private val costByType = ConcurrentHashMap<AnalysisType, AtomicLong>()
    
    // Error tracking
    private val errorsByCategory = ConcurrentHashMap<ErrorCategory, AtomicLong>()
    private val retryableErrors = AtomicLong(0)
    private val nonRetryableErrors = AtomicLong(0)
    
    // Performance percentiles
    private val percentileWindows = ConcurrentHashMap<String, PercentileWindow>()
    
    // Queue metrics
    private val queueWaitTimes = mutableListOf<Long>()
    private val lastQueueDepthUpdate = AtomicLong(0)
    
    /**
     * Record analysis job start
     */
    fun recordJobStart(
        jobId: String,
        analysisType: AnalysisType,
        queuedAt: Instant
    ) {
        val queueWaitTime = (Clock.System.now() - queuedAt).inWholeMilliseconds
        synchronized(queueWaitTimes) {
            queueWaitTimes.add(queueWaitTime)
            // Keep only last 1000 samples
            if (queueWaitTimes.size > 1000) {
                queueWaitTimes.removeAt(0)
            }
        }
        
        metricsService.incrementCounter("analysis_jobs_started", mapOf(
            "type" to analysisType.name
        ))
        
        metricsService.recordHistogram("analysis_queue_wait_time", queueWaitTime)
        
        logger.debug("Analysis job started: id=$jobId, type=$analysisType, queueWait=${queueWaitTime}ms")
    }
    
    /**
     * Record analysis job completion
     */
    fun recordJobCompletion(
        jobId: String,
        analysisType: AnalysisType,
        processingTimeMs: Long,
        tokensUsed: Int,
        costUsd: Double,
        confidence: Double,
        status: AnalysisStatus
    ) {
        // Update processing time metrics
        processingTimeByType.computeIfAbsent(analysisType) { mutableListOf() }.add(processingTimeMs)
        
        // Update success rate
        successRateByType.computeIfAbsent(analysisType) { SuccessRateTracker() }
            .record(status == AnalysisStatus.COMPLETED)
        
        // Update token usage and cost
        tokenUsageByType.computeIfAbsent(analysisType) { AtomicLong(0) }.addAndGet(tokensUsed.toLong())
        costByType.computeIfAbsent(analysisType) { AtomicLong(0) }
            .addAndGet((costUsd * 1000000).toLong()) // Store as micro-dollars
        
        // Record detailed metrics
        val labels = mapOf(
            "type" to analysisType.name,
            "status" to status.name
        )
        
        metricsService.incrementCounter("analysis_jobs_completed", labels)
        metricsService.recordHistogram("analysis_processing_time_by_type", processingTimeMs, labels)
        metricsService.recordHistogram("analysis_tokens_used_by_type", tokensUsed.toLong(), labels)
        metricsService.recordHistogram("analysis_confidence_score", (confidence * 100).toLong(), labels)
        
        // Update percentile windows
        val windowKey = "processing_time_${analysisType.name}"
        percentileWindows.computeIfAbsent(windowKey) { PercentileWindow(windowSize = 1000) }
            .add(processingTimeMs)
        
        logger.info(
            "Analysis job completed: id=$jobId, type=$analysisType, status=$status, " +
            "processingTime=${processingTimeMs}ms, tokens=$tokensUsed, cost=$${"%.6f".format(costUsd)}"
        )
    }
    
    /**
     * Record analysis error
     */
    fun recordError(
        jobId: String,
        analysisType: AnalysisType,
        errorCategory: ErrorCategory,
        errorCode: String,
        retryable: Boolean,
        processingTimeMs: Long
    ) {
        // Update error counters
        errorsByCategory.computeIfAbsent(errorCategory) { AtomicLong(0) }.incrementAndGet()
        
        if (retryable) {
            retryableErrors.incrementAndGet()
        } else {
            nonRetryableErrors.incrementAndGet()
        }
        
        // Update success rate
        successRateByType.computeIfAbsent(analysisType) { SuccessRateTracker() }.record(false)
        
        // Record metrics
        val labels = mapOf(
            "type" to analysisType.name,
            "category" to errorCategory.name,
            "code" to errorCode,
            "retryable" to retryable.toString()
        )
        
        metricsService.incrementCounter("analysis_errors", labels)
        metricsService.recordHistogram("analysis_error_processing_time", processingTimeMs, labels)
        
        logger.warn(
            "Analysis error: id=$jobId, type=$analysisType, category=$errorCategory, " +
            "code=$errorCode, retryable=$retryable, processingTime=${processingTimeMs}ms"
        )
    }
    
    /**
     * Record retry attempt
     */
    fun recordRetryAttempt(
        jobId: String,
        analysisType: AnalysisType,
        attempt: Int,
        delayMs: Long
    ) {
        metricsService.incrementCounter("analysis_retry_attempts", mapOf(
            "type" to analysisType.name,
            "attempt" to attempt.toString()
        ))
        
        metricsService.recordHistogram("analysis_retry_delay", delayMs)
        
        logger.info("Analysis retry: id=$jobId, type=$analysisType, attempt=$attempt, delay=${delayMs}ms")
    }
    
    /**
     * Update queue depth
     */
    fun updateQueueDepth(depth: Int) {
        lastQueueDepthUpdate.set(Clock.System.now().toEpochMilliseconds())
        metricsService.setGauge("analysis_queue_depth_current", depth.toLong())
        
        // Calculate queue saturation (as percentage of max workers)
        val maxWorkers = 10 // TODO: Get from config
        val saturation = if (maxWorkers > 0) (depth * 100 / maxWorkers) else 0
        metricsService.setGauge("analysis_queue_saturation_percent", saturation.toLong())
    }
    
    /**
     * Get performance percentiles for a specific analysis type
     */
    fun getPerformancePercentiles(analysisType: AnalysisType): PerformancePercentiles {
        val windowKey = "processing_time_${analysisType.name}"
        val window = percentileWindows[windowKey] ?: return PerformancePercentiles()
        
        return PerformancePercentiles(
            p50 = window.getPercentile(50),
            p75 = window.getPercentile(75),
            p90 = window.getPercentile(90),
            p95 = window.getPercentile(95),
            p99 = window.getPercentile(99),
            mean = window.getMean(),
            count = window.getCount()
        )
    }
    
    /**
     * Get success rate for a specific analysis type
     */
    fun getSuccessRate(analysisType: AnalysisType): Double {
        return successRateByType[analysisType]?.getRate() ?: 0.0
    }
    
    /**
     * Export all metrics summary
     */
    fun exportMetricsSummary(): AnalysisMetricsSummary {
        val typeMetrics = AnalysisType.values().associate { type ->
            type to TypeMetrics(
                successRate = getSuccessRate(type),
                totalJobs = successRateByType[type]?.total?.get() ?: 0,
                totalTokens = tokenUsageByType[type]?.get() ?: 0,
                totalCostMicroDollars = costByType[type]?.get() ?: 0,
                performancePercentiles = getPerformancePercentiles(type)
            )
        }
        
        val errorMetrics = errorsByCategory.map { (category, count) ->
            category to count.get()
        }.toMap()
        
        val avgQueueWaitTime = synchronized(queueWaitTimes) {
            if (queueWaitTimes.isEmpty()) 0.0 else queueWaitTimes.average()
        }
        
        return AnalysisMetricsSummary(
            timestamp = Clock.System.now(),
            typeMetrics = typeMetrics,
            errorMetrics = errorMetrics,
            retryableErrors = retryableErrors.get(),
            nonRetryableErrors = nonRetryableErrors.get(),
            avgQueueWaitTimeMs = avgQueueWaitTime
        )
    }
    
    /**
     * Reset metrics (for testing or periodic cleanup)
     */
    fun reset() {
        processingTimeByType.clear()
        successRateByType.clear()
        tokenUsageByType.clear()
        costByType.clear()
        errorsByCategory.clear()
        retryableErrors.set(0)
        nonRetryableErrors.set(0)
        percentileWindows.clear()
        synchronized(queueWaitTimes) {
            queueWaitTimes.clear()
        }
    }
    
    // Helper classes
    
    private class SuccessRateTracker {
        private val successful = AtomicLong(0)
        val total = AtomicLong(0)
        
        fun record(success: Boolean) {
            total.incrementAndGet()
            if (success) successful.incrementAndGet()
        }
        
        fun getRate(): Double {
            val totalCount = total.get()
            return if (totalCount > 0) successful.get().toDouble() / totalCount else 0.0
        }
    }
    
    private class PercentileWindow(private val windowSize: Int = 1000) {
        private val values = mutableListOf<Long>()
        
        @Synchronized
        fun add(value: Long) {
            values.add(value)
            if (values.size > windowSize) {
                values.removeAt(0)
            }
        }
        
        @Synchronized
        fun getPercentile(percentile: Int): Long {
            if (values.isEmpty()) return 0
            val sorted = values.sorted()
            val index = (sorted.size * percentile / 100).coerceIn(0, sorted.size - 1)
            return sorted[index]
        }
        
        @Synchronized
        fun getMean(): Double {
            return if (values.isEmpty()) 0.0 else values.average()
        }
        
        @Synchronized
        fun getCount(): Int = values.size
    }
}

// Data classes for metrics export

data class AnalysisMetricsSummary(
    val timestamp: Instant,
    val typeMetrics: Map<AnalysisType, TypeMetrics>,
    val errorMetrics: Map<ErrorCategory, Long>,
    val retryableErrors: Long,
    val nonRetryableErrors: Long,
    val avgQueueWaitTimeMs: Double
)

data class TypeMetrics(
    val successRate: Double,
    val totalJobs: Long,
    val totalTokens: Long,
    val totalCostMicroDollars: Long,
    val performancePercentiles: PerformancePercentiles
)

data class PerformancePercentiles(
    val p50: Long = 0,
    val p75: Long = 0,
    val p90: Long = 0,
    val p95: Long = 0,
    val p99: Long = 0,
    val mean: Double = 0.0,
    val count: Int = 0
)

// Extension functions for MetricsService

fun MetricsService.recordHistogram(name: String, value: Long, labels: Map<String, String>) {
    val key = if (labels.isEmpty()) name else {
        "$name{${labels.entries.joinToString(",") { "${it.key}=${it.value}" }}}"
    }
    // For now, just log. In production, this would integrate with Prometheus/Micrometer
    LoggerFactory.getLogger(MetricsService::class.java).debug("Histogram $key: $value")
}