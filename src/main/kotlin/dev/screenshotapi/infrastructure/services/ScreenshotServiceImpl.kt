package dev.screenshotapi.infrastructure.services

import com.microsoft.playwright.*
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.ScreenshotType
import dev.screenshotapi.core.domain.entities.ScreenshotFormat
import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.domain.exceptions.ScreenshotException
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.core.services.ScreenshotService
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

class ScreenshotServiceImpl(
    private val storagePort: StorageOutputPort,
    private val config: ScreenshotConfig
) : ScreenshotService {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val playwright = Playwright.create()
    private val browserPool = ConcurrentLinkedQueue<Browser>()

    init {
        repeat(config.browserPoolSize) {
            try {
                val browser = createBrowser()
                browserPool.offer(browser)
                logger.debug("Created browser instance for pool")
            } catch (e: Exception) {
                logger.warn("Failed to create initial browser instance", e)
            }
        }

        logger.info("ScreenshotService initialized with ${browserPool.size} browsers in pool")
    }

    override suspend fun takeScreenshot(request: ScreenshotRequest): String = withContext(Dispatchers.IO) {
        validateRequest(request)

        val browser = borrowBrowser()

        try {
            withTimeout(config.maxTimeoutDuration.inWholeMilliseconds) {
                when (request.format) {
                    ScreenshotFormat.PDF -> takePdfScreenshot(browser, request)
                    else -> takeImageScreenshot(browser, request)
                }
            }
        } catch (e: Exception) {
            logger.error("Screenshot failed for URL: ${request.url}", e)
            throw when (e) {
                is TimeoutCancellationException ->
                    ScreenshotException.TimeoutException(request.url, config.maxTimeout)

                else -> ScreenshotException.BrowserException(request.url, e)
            }
        } finally {
            returnBrowser(browser)
        }
    }

    private suspend fun takeImageScreenshot(browser: Browser, request: ScreenshotRequest): String {
        val page = browser.newPage()

        try {

            configurePage(page, request)

            navigateToUrl(page, request.url)

            waitForPageReady(page, request)

            val screenshotOptions = Page.ScreenshotOptions()
                .setFullPage(request.fullPage)
                .setType(
                    when (request.format) {
                        ScreenshotFormat.PNG -> ScreenshotType.PNG
                        ScreenshotFormat.JPEG -> ScreenshotType.JPEG
                        else -> ScreenshotType.PNG
                    }
                )

            // Solo agregar quality para JPEG
            if (request.format == ScreenshotFormat.JPEG) {
                screenshotOptions.setQuality(request.quality)
            }

            val screenshotBytes = page.screenshot(screenshotOptions)

            // Validar tamaño del archivo
            if (screenshotBytes.size > config.maxFileSize) {
                throw ScreenshotException.FileTooLarge(
                    screenshotBytes.size.toLong(),
                    config.maxFileSize
                )
            }

            // Generar nombre de archivo y subir
            val filename = generateFilename(request)
            val contentType = getContentType(request.format)

            return storagePort.upload(screenshotBytes, filename, contentType)

        } finally {
            page.close()
        }
    }

    private suspend fun takePdfScreenshot(browser: Browser, request: ScreenshotRequest): String {
        if (!config.enablePdfGeneration) {
            throw ScreenshotException.UnsupportedFormat("PDF generation is disabled")
        }

        val page = browser.newPage()

        try {
            configurePage(page, request)
            navigateToUrl(page, request.url)
            waitForPageReady(page, request)

            val pdfBytes = page.pdf(
                Page.PdfOptions()
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setDisplayHeaderFooter(false)
            )

            if (pdfBytes.size > config.maxFileSize) {
                throw ScreenshotException.FileTooLarge(
                    pdfBytes.size.toLong(),
                    config.maxFileSize
                )
            }

            // Generar nombre de archivo y subir
            val filename = generateFilename(request, "pdf")

            return storagePort.upload(pdfBytes, filename, "application/pdf")

        } finally {
            page.close()
        }
    }

    private fun configurePage(page: Page, request: ScreenshotRequest) {
        // Configurar viewport
        page.setViewportSize(request.width, request.height)

        // Configurar timeouts
        page.setDefaultTimeout(config.pageLoadTimeoutDuration.inWholeMilliseconds.toDouble())

        // Configurar user agent
        page.setExtraHTTPHeaders(
            mapOf(
                "User-Agent" to config.userAgent
            )
        )

        // Configurar características del navegador
        if (!config.enableJavaScript) {
            page.addInitScript("() => { window.navigator.javaEnabled = () => false; }")
        }

        if (!config.enableImages) {
            page.route("**/*.{jpg,jpeg,png,gif,webp,svg}") { route ->
                route.abort()
            }
        }

        if (!config.enableCSS) {
            page.route("**/*.css") { route ->
                route.abort()
            }
        }
    }

    private suspend fun navigateToUrl(page: Page, url: String) {
        try {
            val response = page.navigate(url)

            if (response != null && !response.ok()) {
                throw ScreenshotException.UrlNotAccessible(url, response.status())
            }

            page.waitForLoadState(
                LoadState.NETWORKIDLE,
                Page.WaitForLoadStateOptions()
                    .setTimeout(config.networkIdleTimeoutDuration.inWholeMilliseconds.toDouble())
            )

        } catch (e: PlaywrightException) {
            logger.error("Failed to navigate to URL: $url", e)
            throw ScreenshotException.UrlNotAccessible(url)
        }
    }

    private suspend fun waitForPageReady(page: Page, request: ScreenshotRequest) {
        request.waitForSelector?.let { selector ->
            try {
                page.waitForSelector(
                    selector,
                    Page.WaitForSelectorOptions().setTimeout(10_000.0)
                )
            } catch (e: Exception) {
                logger.warn("Selector '$selector' not found within timeout, continuing...")
            }
        }

        request.waitTime?.let { waitTime ->
            if (waitTime > 0) {
                delay(waitTime)
            }
        }
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

    private fun borrowBrowser(): Browser {
        return browserPool.poll() ?: createBrowser()
    }

    private fun returnBrowser(browser: Browser) {
        if (browserPool.size < config.browserPoolSize && browser.isConnected) {
            browserPool.offer(browser)
        } else {
            try {
                browser.close()
            } catch (e: Exception) {
                logger.warn("Error closing browser", e)
            }
        }
    }

    private fun createBrowser(): Browser {
        return playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(config.getOptimalBrowserArgs())
                .setTimeout(config.browserLaunchTimeoutDuration.inWholeMilliseconds.toDouble())
        )
    }

    private fun generateFilename(request: ScreenshotRequest, extension: String? = null): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val urlHash = request.url.hashCode().toString().takeLast(8)
        val ext = extension ?: request.format.name.lowercase()
        val dimensions = "${request.width}x${request.height}"

        return "screenshots/${timestamp}_${urlHash}_${dimensions}.$ext"
    }

    private fun getContentType(format: ScreenshotFormat): String {
        return when (format) {
            ScreenshotFormat.PNG -> "image/png"
            ScreenshotFormat.JPEG -> "image/jpeg"
            ScreenshotFormat.PDF -> "application/pdf"
        }
    }

    fun cleanup() {
        try {
            browserPool.forEach { browser ->
                try {
                    browser.close()
                } catch (e: Exception) {
                    logger.warn("Error closing browser during cleanup", e)
                }
            }
            browserPool.clear()

            playwright.close()

            logger.info("ScreenshotService cleanup completed")
        } catch (e: Exception) {
            logger.error("Error during ScreenshotService cleanup", e)
        }
    }
}
