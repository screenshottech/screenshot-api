package dev.screenshotapi.core.domain.entities

/**
 * Type of retry for a screenshot job
 */
enum class RetryType {
    /**
     * Automatic retry triggered by the system after a failure
     */
    AUTOMATIC,
    
    /**
     * Manual retry triggered by the user through the API
     */
    MANUAL
}