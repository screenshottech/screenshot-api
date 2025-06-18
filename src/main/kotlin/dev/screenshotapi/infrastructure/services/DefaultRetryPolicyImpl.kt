package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.services.RetryPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DefaultRetryPolicyImpl : RetryPolicy {
    
    override fun shouldRetry(error: Exception): Boolean {
        return when (error) {
            // Network-related errors - retryable
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.net.UnknownHostException,
            is java.io.IOException -> true
            
            // Client errors - non-retryable
            is IllegalArgumentException,
            is SecurityException,
            is IllegalStateException -> false
            
            // Default to retryable for unknown errors
            else -> true
        }
    }
    
    override fun calculateDelay(attemptNumber: Int): Duration {
        // Exponential backoff: 5s, 25s, 125s
        val baseDelay = 5.seconds
        val multiplier = 5
        return baseDelay * (multiplier * attemptNumber)
    }
    
    override fun getMaxRetries(): Int = 3
}