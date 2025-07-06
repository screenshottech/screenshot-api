package dev.screenshotapi.infrastructure.config

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class WebhookConfig(
    val maxRetryAttempts: Int,
    val maxTestRetryAttempts: Int,
    val testRateLimitMinutes: Int,
    val retryDelayMinutes: List<Int>,
    val testRetryDelaySeconds: List<Int>,
    val timeoutSeconds: Long
) {
    companion object {
        fun load() = WebhookConfig(
            maxRetryAttempts = System.getenv("WEBHOOK_MAX_RETRY_ATTEMPTS")?.toInt() ?: 3,
            maxTestRetryAttempts = System.getenv("WEBHOOK_MAX_TEST_RETRY_ATTEMPTS")?.toInt() ?: 1,
            testRateLimitMinutes = System.getenv("WEBHOOK_TEST_RATE_LIMIT_MINUTES")?.toInt() ?: 1,
            retryDelayMinutes = System.getenv("WEBHOOK_RETRY_DELAY_MINUTES")?.split(",")?.map { it.toInt() } 
                ?: listOf(1, 5, 15, 30, 60),
            testRetryDelaySeconds = System.getenv("WEBHOOK_TEST_RETRY_DELAY_SECONDS")?.split(",")?.map { it.toInt() }
                ?: listOf(30),
            timeoutSeconds = System.getenv("WEBHOOK_TIMEOUT_SECONDS")?.toLong() ?: 30L
        )
    }
    
    fun getRetryDelays() = retryDelayMinutes.map { it.minutes }
    fun getTestRetryDelays() = testRetryDelaySeconds.map { it.seconds }
    fun getTestRateLimit() = testRateLimitMinutes.minutes
}