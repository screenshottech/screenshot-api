package dev.screenshotapi.core.domain.services

import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.domain.entities.ScreenshotResult

interface ScreenshotService {
    /**
     * Take a screenshot with legacy filename format
     * @param request The screenshot request parameters
     * @return The screenshot result with generated URL
     */
    suspend fun takeScreenshot(request: ScreenshotRequest): ScreenshotResult
    
    /**
     * Take a screenshot with secure HMAC-based filename
     * @param request The screenshot request parameters
     * @param userId The user ID for security token generation
     * @param jobId The job ID for uniqueness
     * @param createdAtEpochSeconds The creation timestamp for deterministic generation
     * @return The screenshot result with secure URL
     */
    suspend fun takeSecureScreenshot(
        request: ScreenshotRequest,
        userId: String,
        jobId: String,
        createdAtEpochSeconds: Long
    ): ScreenshotResult
}
