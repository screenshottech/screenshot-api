package dev.screenshotapi.infrastructure.services

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class MetricsService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val gauges = ConcurrentHashMap<String, AtomicLong>()

    fun recordScreenshot(status: String, processingTime: Long) {
        incrementCounter("screenshots_total", mapOf("status" to status))
        recordHistogram("screenshot_processing_time", processingTime)
        logger.debug("Screenshot recorded: status=$status, time=${processingTime}ms")
    }

    fun recordScaling(direction: String, newWorkerCount: Int) {
        setGauge("workers_active", newWorkerCount.toLong())
        incrementCounter("worker_scaling_events", mapOf("direction" to direction))
        logger.info("Worker scaling: direction=$direction, workers=$newWorkerCount")
    }

    fun updateWorkerMetrics(workerCount: Int, queueSize: Long) {
        setGauge("workers_active", workerCount.toLong())
        setGauge("queue_size", queueSize)
        logger.debug("Worker metrics: workers=$workerCount, queue=$queueSize")
    }

    fun recordWorkerRestart(workerId: String, reason: String) {
        incrementCounter("worker_restarts_total", mapOf("reason" to reason))
        logger.warn("Worker restart: id=$workerId, reason=$reason")
    }
    
    // Rate limiting metrics
    fun recordRateLimitHit(userId: String, limitType: String, planType: String) {
        incrementCounter("rate_limit_hits_total", mapOf(
            "limit_type" to limitType,
            "plan" to planType
        ))
        logger.info("Rate limit hit: user=$userId, type=$limitType, plan=$planType")
    }
    
    fun recordRateLimitCheck(userId: String, allowed: Boolean, planType: String) {
        incrementCounter("rate_limit_checks_total", mapOf(
            "allowed" to allowed.toString(),
            "plan" to planType
        ))
        
        if (!allowed) {
            incrementCounter("rate_limit_denials_total", mapOf("plan" to planType))
        }
    }
    
    fun recordDailyQuotaUsage(userId: String, used: Int, limit: Int, planType: String) {
        setGauge("daily_quota_usage", used.toLong(), mapOf(
            "plan" to planType
        ))
        
        val utilizationPercent = if (limit > 0) (used * 100 / limit) else 0
        setGauge("daily_quota_utilization_percent", utilizationPercent.toLong(), mapOf(
            "plan" to planType
        ))
        
        logger.debug("Daily quota usage: user=$userId, used=$used/$limit ($utilizationPercent%), plan=$planType")
    }
    
    fun recordConcurrentRequests(userId: String, count: Int, limit: Int, planType: String) {
        setGauge("concurrent_requests", count.toLong(), mapOf(
            "plan" to planType
        ))
        
        if (count >= limit) {
            incrementCounter("concurrent_limit_hits_total", mapOf("plan" to planType))
            logger.warn("Concurrent limit hit: user=$userId, count=$count/$limit, plan=$planType")
        }
    }
    
    fun recordApiKeyLookup(userId: String, success: Boolean, cached: Boolean) {
        incrementCounter("api_key_lookups_total", mapOf(
            "success" to success.toString(),
            "cached" to cached.toString()
        ))
    }
    
    fun recordPlanCacheHit(userId: String, hit: Boolean) {
        incrementCounter("plan_cache_lookups_total", mapOf("hit" to hit.toString()))
    }
    
    // Generic metrics methods
    private fun incrementCounter(name: String, labels: Map<String, String> = emptyMap()) {
        val key = buildMetricKey(name, labels)
        counters.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }
    
    private fun setGauge(name: String, value: Long, labels: Map<String, String> = emptyMap()) {
        val key = buildMetricKey(name, labels)
        gauges[key] = AtomicLong(value)
    }
    
    private fun recordHistogram(name: String, value: Long) {
        // For now, just log the histogram value
        // In a real implementation, this would feed into a proper metrics system
        logger.debug("Histogram $name: $value")
    }
    
    private fun buildMetricKey(name: String, labels: Map<String, String>): String {
        if (labels.isEmpty()) return name
        val labelString = labels.entries.joinToString(",") { "${it.key}=${it.value}" }
        return "$name{$labelString}"
    }
    
    // Expose metrics for health checks or Prometheus scraping
    fun getCounters(): Map<String, Long> = counters.mapValues { it.value.get() }
    fun getGauges(): Map<String, Long> = gauges.mapValues { it.value.get() }
}
