package dev.screenshotapi.core.domain.exceptions

sealed class ScreenshotException(message: String, cause: Throwable? = null) : BusinessException(message, cause) {
    class InvalidUrl(val url: String) : ScreenshotException("Invalid URL: $url")
    class UrlNotAccessible(val url: String, val statusCode: Int? = null) :
        ScreenshotException("URL not accessible: $url ${statusCode?.let { "(HTTP $it)" } ?: ""}")

    class TimeoutException(val url: String, val timeoutMs: Long) :
        ScreenshotException("Screenshot timeout for $url after ${timeoutMs}ms")

    class BrowserException(val url: String, cause: Throwable) :
        ScreenshotException("Browser error for $url: ${cause.message}", cause)

    class UnsupportedFormat(val format: String) : ScreenshotException("Unsupported screenshotapi format: $format")
    class FileTooLarge(val sizeBytes: Long, val maxSizeBytes: Long) :
        ScreenshotException("Screenshot file too large: ${sizeBytes}bytes (max: ${maxSizeBytes}bytes)")
}

