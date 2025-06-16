package dev.screenshotapi.infrastructure.services

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.ScreenshotType
import dev.screenshotapi.core.domain.entities.ScreenshotFormat
import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.domain.entities.ScreenshotResult
import dev.screenshotapi.core.domain.exceptions.ScreenshotException
import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.slf4j.LoggerFactory
import kotlin.math.min

/**
 * Bulletproof screenshot service - no browser pool, fresh browser per request
 * More resource intensive but much more stable under high load
 */
class ScreenshotServiceImpl(
    private val storagePort: StorageOutputPort,
    private val config: ScreenshotConfig
) : ScreenshotService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // Very limited concurrency to prevent resource exhaustion
    private val concurrencySemaphore = Semaphore(min(config.maxConcurrentRequests, 10))

    override suspend fun takeScreenshot(request: ScreenshotRequest): ScreenshotResult = withContext(Dispatchers.IO) {
        validateRequest(request)

        // Limit concurrency
        concurrencySemaphore.withPermit {
            executeScreenshotWithIsolatedPlaywright(request)
        }
    }

    private suspend fun executeScreenshotWithIsolatedPlaywright(request: ScreenshotRequest): ScreenshotResult {
        // Each request gets completely isolated resources
        return withTimeout(config.maxTimeoutDuration.inWholeMilliseconds) {

            // Create fresh Playwright instance
            Playwright.create().use { playwright ->
                // Create fresh browser
                val browser = playwright.chromium().launch(
                    BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(getSimpleBrowserArgs())
                )

                browser.use {
                    // Create fresh page
                    val page = browser.newPage()

                    page.use {
                        // Configure and take screenshot
                        when (request.format) {
                            ScreenshotFormat.PDF -> takePdfScreenshot(page, request)
                            else -> takeImageScreenshot(page, request)
                        }
                    }
                }
            }
        }
    }

    private suspend fun takeImageScreenshot(page: Page, request: ScreenshotRequest): ScreenshotResult {
        // Simple page configuration
        page.setViewportSize(request.width, request.height)
        page.setDefaultTimeout(15000.0)

        // Navigate to URL
        val response = page.navigate(request.url)
        if (response?.ok() == false) {
            throw ScreenshotException.UrlNotAccessible(request.url, response.status())
        }

        // Wait for page to load
        page.waitForLoadState(LoadState.NETWORKIDLE,
            Page.WaitForLoadStateOptions().setTimeout(10000.0))

        // Optional wait time
        request.waitTime?.let { delay(minOf(it, 5000)) }

        // Take screenshot
        val screenshotBytes = page.screenshot(
            Page.ScreenshotOptions()
                .setFullPage(request.fullPage)
                .setType(when (request.format) {
                    ScreenshotFormat.PNG -> ScreenshotType.PNG
                    ScreenshotFormat.JPEG -> ScreenshotType.JPEG
                    ScreenshotFormat.WEBP -> ScreenshotType.PNG
                    else -> throw ScreenshotException.UnsupportedFormat(request.format.name)
                })
                .apply {
                    if (request.format == ScreenshotFormat.JPEG) {
                        setQuality(request.quality)
                    }
                }
        )

        // Convert to WebP if needed
        val finalBytes = if (request.format == ScreenshotFormat.WEBP) {
            convertToWebP(screenshotBytes, request.quality)
        } else {
            screenshotBytes
        }

        // Check file size
        if (finalBytes.size > config.maxFileSize) {
            throw ScreenshotException.FileTooLarge(finalBytes.size.toLong(), config.maxFileSize)
        }

        // Upload and return
        val filename = generateFilename(request)
        val contentType = getContentType(request.format)
        val url = storagePort.upload(finalBytes, filename, contentType)
        val fileSizeBytes = finalBytes.size.toLong()
        
        return ScreenshotResult(url, fileSizeBytes)
    }

    private suspend fun takePdfScreenshot(page: Page, request: ScreenshotRequest): ScreenshotResult {
        if (!config.enablePdfGeneration) {
            throw ScreenshotException.UnsupportedFormat("PDF generation is disabled")
        }

        // Simple page configuration for PDF
        page.setViewportSize(request.width, request.height)
        page.setDefaultTimeout(15000.0)

        // Navigate
        val response = page.navigate(request.url)
        if (response?.ok() == false) {
            throw ScreenshotException.UrlNotAccessible(request.url, response.status())
        }

        // Wait for page load
        page.waitForLoadState(LoadState.NETWORKIDLE,
            Page.WaitForLoadStateOptions().setTimeout(10000.0))

        // Optional wait time
        request.waitTime?.let { delay(minOf(it.toLong(), 5000)) }

        // Generate PDF
        val pdfBytes = page.pdf(
            Page.PdfOptions()
                .setFormat("A4")
                .setPrintBackground(true)
                .setDisplayHeaderFooter(false)
                .setPreferCSSPageSize(true)
        )

        // Check file size
        if (pdfBytes.size > config.maxFileSize) {
            throw ScreenshotException.FileTooLarge(pdfBytes.size.toLong(), config.maxFileSize)
        }

        // Upload and return
        val filename = generateFilename(request, "pdf")
        val url = storagePort.upload(pdfBytes, filename, "application/pdf")
        val fileSizeBytes = pdfBytes.size.toLong()
        
        return ScreenshotResult(url, fileSizeBytes)
    }

    private fun getSimpleBrowserArgs(): List<String> {
        return listOf(
            // Essential args only - keep it simple
            "--no-sandbox", // Disable sandbox for better compatibility
            "--disable-setuid-sandbox", // Disable setuid sandbox
            "--disable-dev-shm-usage", // Disable /dev/shm usage to avoid issues in Docker
            "--disable-gpu", // Disable GPU hardware acceleration
            "--headless", // Run in headless mode
            "--hide-scrollbars", // Hide scrollbars for cleaner screenshots
            "--mute-audio", // Mute audio to avoid sound issues
            "--no-first-run", // Skip first run experience
        )
    }

    private fun validateRequest(request: ScreenshotRequest) {
        if (!config.isValidUrl(request.url)) {
            throw ScreenshotException.InvalidUrl(request.url)
        }
        if (!config.isValidDimensions(request.width, request.height)) {
            throw ScreenshotException.UnsupportedFormat("Invalid dimensions: ${request.width}x${request.height}")
        }
        if (!config.isValidFormat(request.format.name)) {
            throw ScreenshotException.UnsupportedFormat(request.format.name)
        }
        if (!config.isValidQuality(request.quality)) {
            throw ScreenshotException.UnsupportedFormat("Invalid quality: ${request.quality}")
        }
        request.waitTime?.let { waitTime ->
            if (!config.isValidWaitTime(waitTime)) {
                throw ScreenshotException.UnsupportedFormat("Invalid wait time: ${waitTime}ms")
            }
        }
    }

    private fun generateFilename(request: ScreenshotRequest, extension: String? = null): String {
        val now = Clock.System.now()
        val instant = now.toLocalDateTime(TimeZone.UTC)
        val year = instant.year
        val month = instant.monthNumber.toString().padStart(2, '0')
        
        val timestamp = now.toEpochMilliseconds()
        val urlHash = request.url.hashCode().toString().takeLast(8)
        val ext = extension ?: request.format.name.lowercase()
        val dimensions = "${request.width}x${request.height}"
        
        return "screenshots/$year/$month/${timestamp}_${urlHash}_${dimensions}.$ext"
    }

    private fun getContentType(format: ScreenshotFormat): String {
        return when (format) {
            ScreenshotFormat.PNG -> "image/png"
            ScreenshotFormat.JPEG -> "image/jpeg"
            ScreenshotFormat.WEBP -> "image/webp"
            ScreenshotFormat.PDF -> "application/pdf"
        }
    }

    private fun convertToWebP(pngBytes: ByteArray, quality: Int): ByteArray {
        return try {
            if (pngBytes.isEmpty()) return pngBytes
            val image = Image.makeFromEncoded(pngBytes) ?: return pngBytes
            val qualityInt = quality.coerceIn(0, 100)
            val webpData = image.encodeToData(EncodedImageFormat.WEBP, qualityInt)
            webpData?.bytes ?: pngBytes
        } catch (e: Exception) {
            logger.warn("WebP conversion failed, using PNG", e)
            pngBytes
        }
    }

    // No cleanup needed - each request is completely isolated!
    fun cleanup() {
        logger.info("Simple screenshot service cleanup - nothing to clean up!")
    }
}

// Extension function for semaphore
private suspend fun <T> Semaphore.withPermit(block: suspend () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
}
