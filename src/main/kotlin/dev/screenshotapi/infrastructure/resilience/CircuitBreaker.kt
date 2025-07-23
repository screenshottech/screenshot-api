package dev.screenshotapi.infrastructure.resilience

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Circuit Breaker implementation for fault tolerance
 * 
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Circuit is open, requests fail fast
 * - HALF_OPEN: Testing if service has recovered
 */
class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private val logger = LoggerFactory.getLogger("CircuitBreaker-$name")
    private val mutex = Mutex()
    
    @Volatile
    private var state: CircuitBreakerState = CircuitBreakerState.CLOSED
    
    @Volatile
    private var failureCount = 0
    
    @Volatile
    private var successCount = 0
    
    @Volatile
    private var lastFailureTime: Instant? = null
    
    @Volatile
    private var nextAttemptTime: Instant? = null
    
    /**
     * Execute a suspending function with circuit breaker protection
     */
    suspend fun <T> execute(operation: suspend () -> T): T {
        mutex.withLock {
            when (state) {
                CircuitBreakerState.CLOSED -> {
                    return executeInClosedState(operation)
                }
                
                CircuitBreakerState.OPEN -> {
                    val now = Clock.System.now()
                    val nextAttempt = nextAttemptTime
                    
                    if (nextAttempt != null && now >= nextAttempt) {
                        logger.info("Circuit breaker $name transitioning to HALF_OPEN for test")
                        state = CircuitBreakerState.HALF_OPEN
                        return executeInHalfOpenState(operation)
                    } else {
                        val timeUntilRetry = nextAttempt?.let { (it - now).inWholeSeconds } ?: 0
                        logger.debug("Circuit breaker $name is OPEN, failing fast (retry in ${timeUntilRetry}s)")
                        throw CircuitBreakerOpenException("Circuit breaker $name is OPEN")
                    }
                }
                
                CircuitBreakerState.HALF_OPEN -> {
                    return executeInHalfOpenState(operation)
                }
            }
        }
    }
    
    private suspend fun <T> executeInClosedState(operation: suspend () -> T): T {
        return try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure(e)
            throw e
        }
    }
    
    private suspend fun <T> executeInHalfOpenState(operation: suspend () -> T): T {
        return try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure(e)
            throw e
        }
    }
    
    private fun onSuccess() {
        when (state) {
            CircuitBreakerState.CLOSED -> {
                failureCount = 0
            }
            
            CircuitBreakerState.HALF_OPEN -> {
                successCount++
                if (successCount >= config.successThreshold) {
                    logger.info("Circuit breaker $name transitioning to CLOSED after $successCount successes")
                    state = CircuitBreakerState.CLOSED
                    failureCount = 0
                    successCount = 0
                    lastFailureTime = null
                    nextAttemptTime = null
                }
            }
            
            CircuitBreakerState.OPEN -> {
                // Should not happen, but reset if it does
                logger.warn("Circuit breaker $name received success in OPEN state, resetting")
                state = CircuitBreakerState.CLOSED
                failureCount = 0
                successCount = 0
                lastFailureTime = null
                nextAttemptTime = null
            }
        }
    }
    
    private fun onFailure(exception: Exception) {
        lastFailureTime = Clock.System.now()
        
        when (state) {
            CircuitBreakerState.CLOSED -> {
                failureCount++
                
                if (isFailureThresholdReached()) {
                    logger.warn("Circuit breaker $name opening due to $failureCount failures")
                    openCircuit()
                } else {
                    logger.debug("Circuit breaker $name failure count: $failureCount/${config.failureThreshold}")
                }
            }
            
            CircuitBreakerState.HALF_OPEN -> {
                logger.warn("Circuit breaker $name returning to OPEN state after failure in HALF_OPEN")
                openCircuit()
            }
            
            CircuitBreakerState.OPEN -> {
                // Already open, just update failure time
                logger.debug("Circuit breaker $name already OPEN, updating failure time")
            }
        }
        
        // Record failure details for analysis
        recordFailure(exception)
    }
    
    private fun isFailureThresholdReached(): Boolean {
        return failureCount >= config.failureThreshold
    }
    
    private fun openCircuit() {
        state = CircuitBreakerState.OPEN
        successCount = 0
        nextAttemptTime = Clock.System.now() + config.waitDurationInOpenState
        
        logger.warn(
            "Circuit breaker $name OPENED. Next attempt at: $nextAttemptTime " +
            "(in ${config.waitDurationInOpenState.inWholeSeconds}s)"
        )
    }
    
    private fun recordFailure(exception: Exception) {
        logger.debug("Circuit breaker $name failure: ${exception::class.simpleName}: ${exception.message}")
    }
    
    /**
     * Get current circuit breaker state and metrics
     */
    fun getState(): CircuitBreakerState = state
    
    fun getMetrics(): CircuitBreakerMetrics {
        return CircuitBreakerMetrics(
            name = name,
            state = state,
            failureCount = failureCount,
            successCount = successCount,
            lastFailureTime = lastFailureTime,
            nextAttemptTime = nextAttemptTime
        )
    }
    
    /**
     * Manually reset the circuit breaker (for testing or admin operations)
     */
    suspend fun reset() {
        mutex.withLock {
            logger.info("Circuit breaker $name manually reset")
            state = CircuitBreakerState.CLOSED
            failureCount = 0
            successCount = 0
            lastFailureTime = null
            nextAttemptTime = null
        }
    }
    
    /**
     * Force the circuit breaker to open (for testing or emergency operations)
     */
    suspend fun forceOpen() {
        mutex.withLock {
            logger.warn("Circuit breaker $name manually forced OPEN")
            openCircuit()
        }
    }
}

/**
 * Circuit Breaker States
 */
enum class CircuitBreakerState {
    CLOSED,     // Normal operation
    OPEN,       // Failing fast
    HALF_OPEN   // Testing recovery
}

/**
 * Circuit Breaker Configuration
 */
data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,                        // Failures before opening
    val successThreshold: Int = 3,                        // Successes before closing from half-open
    val waitDurationInOpenState: Duration = 60.seconds,   // How long to wait before trying half-open
    val slowCallDurationThreshold: Duration = 30.seconds  // Calls slower than this are considered failures
)

/**
 * Circuit Breaker Metrics
 */
data class CircuitBreakerMetrics(
    val name: String,
    val state: CircuitBreakerState,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Instant?,
    val nextAttemptTime: Instant?
)

/**
 * Exception thrown when circuit breaker is open
 */
class CircuitBreakerOpenException(message: String) : Exception(message)

/**
 * Circuit Breaker Builder for easy configuration
 */
class CircuitBreakerBuilder(private val name: String) {
    private var failureThreshold = 5
    private var successThreshold = 3
    private var waitDuration = 60.seconds
    private var slowCallThreshold = 30.seconds
    
    fun failureThreshold(threshold: Int) = apply { this.failureThreshold = threshold }
    fun successThreshold(threshold: Int) = apply { this.successThreshold = threshold }
    fun waitDuration(duration: Duration) = apply { this.waitDuration = duration }
    fun slowCallThreshold(duration: Duration) = apply { this.slowCallThreshold = duration }
    
    fun build(): CircuitBreaker {
        val config = CircuitBreakerConfig(
            failureThreshold = failureThreshold,
            successThreshold = successThreshold,
            waitDurationInOpenState = waitDuration,
            slowCallDurationThreshold = slowCallThreshold
        )
        return CircuitBreaker(name, config)
    }
}

/**
 * Extension function for easy circuit breaker creation
 */
fun circuitBreaker(name: String, configure: CircuitBreakerBuilder.() -> Unit = {}): CircuitBreaker {
    return CircuitBreakerBuilder(name).apply(configure).build()
}