package dev.screenshotapi.core.domain.entities

import dev.screenshotapi.core.domain.exceptions.ScreenshotException

enum class ScreenshotFormat {
    PNG, JPEG, WEBP, PDF;

    companion object {
        fun getSupportedFormats(): List<String> = values().map { it.name }
        fun fromString(format: String): ScreenshotFormat = try {
            valueOf(format.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ScreenshotException.UnsupportedFormat(format)
        }
    }
}

data class ScreenshotRequest(
    val url: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val fullPage: Boolean = false,
    val waitTime: Long? = null,
    val waitForSelector: String? = null,
    val quality: Int = 80,
    val format: ScreenshotFormat = ScreenshotFormat.PNG,
    val includeMetadata: Boolean = false
) {
    init {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(width in 320..1920) { "Width must be between 320 and 1920" }
        require(height in 240..1080) { "Height must be between 240 and 1080" }
        require(quality in 1..100) { "Quality must be between 1 and 100" }
        require(waitTime == null || waitTime in 0..10_000) { "Wait time must be between 0 and 10 seconds" }
    }

    fun isValidUrl(): Boolean = try {
        val uri = java.net.URI(url)
        uri.scheme in listOf("http", "https") && uri.host != null
    } catch (e: Exception) {
        false
    }
}
