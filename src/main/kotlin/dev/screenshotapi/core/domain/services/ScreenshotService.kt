package dev.screenshotapi.core.domain.services

import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.domain.entities.ScreenshotResult

interface ScreenshotService {
    suspend fun takeScreenshot(request: ScreenshotRequest): ScreenshotResult
}
