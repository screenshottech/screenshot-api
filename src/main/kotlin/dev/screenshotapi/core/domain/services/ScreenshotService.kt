package dev.screenshotapi.core.domain.services

import dev.screenshotapi.core.domain.entities.ScreenshotRequest

interface ScreenshotService {
    suspend fun takeScreenshot(request: ScreenshotRequest): String
}
