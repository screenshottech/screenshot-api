package dev.screenshotapi.core.domain.entities

/**
 * Domain entity representing the result of a screenshot operation
 */
data class ScreenshotResult(
    val url: String,
    val fileSizeBytes: Long
)