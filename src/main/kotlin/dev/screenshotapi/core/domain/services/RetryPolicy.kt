package dev.screenshotapi.core.domain.services

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Domain service for determining retry behavior.
 * Pure domain logic without infrastructure dependencies.
 */
interface RetryPolicy {
    /**
     * Determines if an error is retryable
     */
    fun shouldRetry(error: Exception): Boolean
    
    /**
     * Calculates delay before next retry using exponential backoff
     */
    fun calculateDelay(attemptNumber: Int): Duration
    
    /**
     * Gets the maximum number of retries allowed
     */
    fun getMaxRetries(): Int
}

/**
 * Default retry policy implementation with exponential backoff
 */
class DefaultRetryPolicy : RetryPolicy {
    companion object {
        private const val BASE_DELAY_SECONDS = 5
        private const val BACKOFF_MULTIPLIER = 5
        private const val MAX_RETRIES = 3
    }
    
    override fun shouldRetry(error: Exception): Boolean {
        // Check if it's explicitly non-retryable
        if (error is NonRetryableException) {
            return false
        }
        
        // Check by exception type
        return when (error) {
            // Retryable errors - temporary failures
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.io.IOException,
            is java.util.concurrent.TimeoutException -> true
            
            // Non-retryable errors - permanent failures
            is IllegalArgumentException,
            is SecurityException,
            is UnsupportedOperationException -> false
            
            // For custom domain exceptions, check the class name
            else -> when (error::class.simpleName) {
                // Retryable domain exceptions
                "TimeoutException",
                "NetworkException",
                "PlaywrightException",
                "BrowserCrashedException" -> true
                
                // Non-retryable domain exceptions
                "InvalidUrlException",
                "InsufficientCreditsException",
                "AuthenticationException",
                "AuthorizationException",
                "ResourceNotFoundException",
                "ValidationException" -> false
                
                // Default to retryable for unknown errors
                else -> true
            }
        }
    }
    
    override fun calculateDelay(attemptNumber: Int): Duration {
        // Exponential backoff: 5s, 25s, 125s
        val delaySeconds = BASE_DELAY_SECONDS * Math.pow(BACKOFF_MULTIPLIER.toDouble(), attemptNumber.toDouble()).toLong()
        return delaySeconds.seconds
    }
    
    override fun getMaxRetries(): Int = MAX_RETRIES
}

/**
 * Marker interface for exceptions that should never be retried
 */
interface NonRetryableException