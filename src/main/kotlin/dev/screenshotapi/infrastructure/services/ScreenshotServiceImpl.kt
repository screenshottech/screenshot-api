package dev.screenshotapi.infrastructure.services

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.ScreenshotType
import dev.screenshotapi.core.domain.entities.ScreenshotFormat
import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.domain.entities.ScreenshotResult
import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.ScreenshotException
import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.core.ports.output.UrlSecurityPort
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
    private val config: ScreenshotConfig,
    private val urlSecurityPort: UrlSecurityPort,
    private val screenshotTokenService: ScreenshotTokenService? = null
) : ScreenshotService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // Very limited concurrency to prevent resource exhaustion
    private val concurrencySemaphore = Semaphore(min(config.maxConcurrentRequests, 10))

    override suspend fun takeScreenshot(request: ScreenshotRequest): ScreenshotResult = withContext(Dispatchers.IO) {
        takeScreenshotInternal(request, null, null, null)
    }

    override suspend fun takeSecureScreenshot(
        request: ScreenshotRequest,
        userId: String,
        jobId: String,
        createdAtEpochSeconds: Long
    ): ScreenshotResult = withContext(Dispatchers.IO) {
        takeScreenshotInternal(request, userId, jobId, createdAtEpochSeconds)
    }

    private suspend fun takeScreenshotInternal(
        request: ScreenshotRequest,
        userId: String? = null,
        jobId: String? = null,
        createdAtEpochSeconds: Long? = null
    ): ScreenshotResult = withContext(Dispatchers.IO) {
        // Step 1: Validate request format
        validateRequest(request)

        // Step 2: SSRF Protection - Validate URL security
        val urlValidation = urlSecurityPort.validateUrl(request.url)
        if (!urlValidation.isValid) {
            logger.warn("SSRF protection blocked URL: ${request.url} - ${urlValidation.reason}")
            throw ScreenshotException.InvalidUrl("URL blocked for security reasons: ${urlValidation.reason}")
        }

        logger.debug("URL security validation passed: ${request.url} -> ${urlValidation.resolvedIp}")

        // Step 3: Limit concurrency
        concurrencySemaphore.withPermit {
            executeScreenshotWithIsolatedPlaywright(request, userId, jobId, createdAtEpochSeconds)
        }
    }

    private suspend fun executeScreenshotWithIsolatedPlaywright(
        request: ScreenshotRequest,
        userId: String? = null,
        jobId: String? = null,
        createdAtEpochSeconds: Long? = null
    ): ScreenshotResult {
        // Each request gets completely isolated resources
        return withTimeout(config.maxTimeoutDuration.inWholeMilliseconds) {

            // Create fresh Playwright instance
            Playwright.create().use { playwright ->
                // Create fresh browser
                val browser = playwright.chromium().launch(
                    BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(config.getOptimalBrowserArgs())
                )

                browser.use {
                    // Create fresh page
                    val page = browser.newPage()

                    page.use {
                        // Configure stealth mode if enabled
                        if (config.enableStealthMode) {
                            configureStealthMode(page)
                        }
                        
                        // Configure and take screenshot
                        when (request.format) {
                            ScreenshotFormat.PDF -> takePdfScreenshot(page, request, userId, jobId, createdAtEpochSeconds)
                            else -> takeImageScreenshot(page, request, userId, jobId, createdAtEpochSeconds)
                        }
                    }
                }
            }
        }
    }

    private suspend fun takeImageScreenshot(
        page: Page,
        request: ScreenshotRequest,
        userId: String? = null,
        jobId: String? = null,
        createdAtEpochSeconds: Long? = null
    ): ScreenshotResult {
        // Simple page configuration
        page.setViewportSize(request.width, request.height)
        page.setDefaultTimeout(config.pageLoadTimeout.toDouble())

        // Navigate to URL
        val response = page.navigate(request.url)
        if (response?.ok() == false) {
            throw ScreenshotException.UrlNotAccessible(request.url, response.status())
        }

        // Wait for page to load with graceful fallback strategy
        waitForPageLoad(page, request.url)

        // Optional wait time
        request.waitTime?.let { delay(minOf(it, 5000)) }

        // Take screenshot first (performance optimization)
        logger.debug("Taking screenshot for request: ${request.url}")
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

        // Extract metadata AFTER screenshot is taken (performance optimization)
        val metadata = if (request.includeMetadata) {
            logger.debug("Extracting page metadata after screenshot for: ${request.url}")
            extractPageMetadata(page)
        } else {
            null
        }

        // Upload and return
        val filename = generateFilename(request, userId, jobId, createdAtEpochSeconds, null)
        val contentType = getContentType(request.format)
        val url = storagePort.upload(finalBytes, filename, contentType)
        val fileSizeBytes = finalBytes.size.toLong()

        return ScreenshotResult(url, fileSizeBytes, metadata)
    }

    private suspend fun takePdfScreenshot(
        page: Page,
        request: ScreenshotRequest,
        userId: String? = null,
        jobId: String? = null,
        createdAtEpochSeconds: Long? = null
    ): ScreenshotResult {
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

        // Wait for page to load with graceful fallback strategy
        waitForPageLoad(page, request.url)

        // Optional wait time
        request.waitTime?.let { delay(minOf(it.toLong(), 5000)) }

        // Generate PDF first (performance optimization)
        logger.debug("Generating PDF for request: ${request.url}")
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

        // Extract metadata AFTER PDF is generated (performance optimization)
        val metadata = if (request.includeMetadata) {
            logger.debug("Extracting page metadata after PDF generation for: ${request.url}")
            extractPageMetadata(page)
        } else {
            null
        }

        // Upload and return
        val filename = generateFilename(request, userId, jobId, createdAtEpochSeconds, "pdf")
        val url = storagePort.upload(pdfBytes, filename, "application/pdf")
        val fileSizeBytes = pdfBytes.size.toLong()

        return ScreenshotResult(url, fileSizeBytes, metadata)
    }

    private suspend fun configureStealthMode(page: Page) {
        try {
            // Inject stealth JavaScript
            val stealthScript = config.getStealthJavaScript()
            if (stealthScript.isNotEmpty()) {
                page.addInitScript(stealthScript)
            }
            
            // Configure additional stealth headers
            page.setExtraHTTPHeaders(mapOf(
                "Accept-Language" to "en-US,en;q=0.9",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                "Accept-Encoding" to "gzip, deflate, br",
                "DNT" to "1",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "none"
            ))
            
            logger.debug("Stealth mode configured successfully")
        } catch (e: Exception) {
            logger.warn("Failed to configure stealth mode: ${e.message}", e)
            // Continue without stealth mode - not critical for operation
        }
    }

    private suspend fun extractPageMetadata(page: Page): PageMetadata? {
        return try {
            val extractedAt = Clock.System.now()
            logger.debug("Starting metadata extraction with 10s timeout")
            
            // Add timeout protection for metadata extraction (10 seconds max)
            return withTimeout(10_000L) {
            
            // Extract all metadata using JavaScript evaluation
            val metadataRaw = page.evaluate("""
                () => {
                    // SEO Data
                    const title = document.title || null;
                    const metaDescription = document.querySelector('meta[name="description"]')?.content || null;
                    const metaKeywords = document.querySelector('meta[name="keywords"]')?.content || null;
                    const canonicalUrl = document.querySelector('link[rel="canonical"]')?.href || null;
                    const robotsMeta = document.querySelector('meta[name="robots"]')?.content || null;
                    
                    // Headings
                    const h1 = Array.from(document.querySelectorAll('h1')).map(h => h.textContent?.trim()).filter(Boolean);
                    const h2 = Array.from(document.querySelectorAll('h2')).map(h => h.textContent?.trim()).filter(Boolean);
                    const h3 = Array.from(document.querySelectorAll('h3')).map(h => h.textContent?.trim()).filter(Boolean);
                    const h4 = Array.from(document.querySelectorAll('h4')).map(h => h.textContent?.trim()).filter(Boolean);
                    const h5 = Array.from(document.querySelectorAll('h5')).map(h => h.textContent?.trim()).filter(Boolean);
                    const h6 = Array.from(document.querySelectorAll('h6')).map(h => h.textContent?.trim()).filter(Boolean);
                    
                    // Images and links
                    const images = Array.from(document.querySelectorAll('img'));
                    const imageAlts = images.map(img => img.alt).filter(Boolean);
                    const imageCount = images.length;
                    
                    const links = Array.from(document.querySelectorAll('a[href]'));
                    const currentDomain = window.location.hostname;
                    const internalLinks = links.filter(a => {
                        try {
                            const href = a.href;
                            return href.includes(currentDomain) || href.startsWith('/') || !href.includes('://');
                        } catch (e) { return false; }
                    }).length;
                    const externalLinks = links.length - internalLinks;
                    
                    // Schema markup
                    const schemaScripts = Array.from(document.querySelectorAll('script[type="application/ld+json"]'));
                    const schemaMarkup = schemaScripts.map(script => {
                        try {
                            const data = JSON.parse(script.textContent || '');
                            return data['@type'] || 'Unknown';
                        } catch (e) { return 'Invalid'; }
                    });
                    
                    // Content analysis
                    const bodyText = document.body?.textContent || '';
                    const wordCount = bodyText.trim().split(/\s+/).filter(word => word.length > 0).length;
                    const textLength = bodyText.length;
                    const language = document.documentElement.lang || null;
                    
                    // Open Graph
                    const ogTitle = document.querySelector('meta[property="og:title"]')?.content || null;
                    const ogDescription = document.querySelector('meta[property="og:description"]')?.content || null;
                    const ogImage = document.querySelector('meta[property="og:image"]')?.content || null;
                    const ogUrl = document.querySelector('meta[property="og:url"]')?.content || null;
                    const ogType = document.querySelector('meta[property="og:type"]')?.content || null;
                    const ogSiteName = document.querySelector('meta[property="og:site_name"]')?.content || null;
                    
                    // Twitter Card
                    const twitterCard = document.querySelector('meta[name="twitter:card"]')?.content || null;
                    const twitterTitle = document.querySelector('meta[name="twitter:title"]')?.content || null;
                    const twitterDescription = document.querySelector('meta[name="twitter:description"]')?.content || null;
                    const twitterImage = document.querySelector('meta[name="twitter:image"]')?.content || null;
                    const twitterSite = document.querySelector('meta[name="twitter:site"]')?.content || null;
                    const twitterCreator = document.querySelector('meta[name="twitter:creator"]')?.content || null;
                    
                    // Technical data
                    const viewport = document.querySelector('meta[name="viewport"]')?.content || null;
                    const charset = document.querySelector('meta[charset]')?.getAttribute('charset') || 
                                  document.querySelector('meta[http-equiv="content-type"]')?.content || null;
                    const generator = document.querySelector('meta[name="generator"]')?.content || null;
                    
                    // Performance timing
                    const performanceTiming = performance.timing;
                    const loadTime = performanceTiming.loadEventEnd - performanceTiming.navigationStart;
                    const domContentLoaded = performanceTiming.domContentLoadedEventEnd - performanceTiming.navigationStart;
                    
                    // Resource counts
                    const scripts = document.querySelectorAll('script').length;
                    const stylesheets = document.querySelectorAll('link[rel="stylesheet"]').length;
                    const totalResources = images.length + scripts + stylesheets;
                    
                    return {
                        seo: {
                            title, metaDescription, metaKeywords, canonicalUrl, robotsMeta,
                            headings: { h1, h2, h3, h4, h5, h6 },
                            imageAlts, internalLinks, externalLinks, schemaMarkup
                        },
                        performance: {
                            loadTimeMs: loadTime > 0 ? loadTime : null,
                            domContentLoadedMs: domContentLoaded > 0 ? domContentLoaded : null,
                            resourceCount: {
                                total: totalResources,
                                images: imageCount,
                                scripts: scripts,
                                stylesheets: stylesheets,
                                fonts: 0, // Hard to detect reliably
                                others: Math.max(0, totalResources - imageCount - scripts - stylesheets)
                            }
                        },
                        content: {
                            wordCount, textLength, language,
                            headingCount: h1.length + h2.length + h3.length + h4.length + h5.length + h6.length,
                            imageCount, linkCount: links.length,
                            hasContactInfo: bodyText.toLowerCase().includes('contact') || 
                                          bodyText.toLowerCase().includes('email') ||
                                          bodyText.toLowerCase().includes('phone')
                        },
                        social: {
                            openGraph: { title: ogTitle, description: ogDescription, image: ogImage, url: ogUrl, type: ogType, siteName: ogSiteName },
                            twitterCard: { card: twitterCard, title: twitterTitle, description: twitterDescription, image: twitterImage, site: twitterSite, creator: twitterCreator },
                            facebookData: { appId: null, admins: [], pages: [] }
                        },
                        technical: {
                            hasStructuredData: schemaMarkup.length > 0,
                            schemaTypes: schemaMarkup,
                            hasRobotsTxt: false, // Would need separate request
                            hasSitemap: false, // Would need separate request
                            viewport, charset, contentType: document.contentType || null, generator
                        }
                    };
                }
            """.trimIndent())
            
            // Convert the raw data to our domain entities
            @Suppress("UNCHECKED_CAST")
            val data = metadataRaw as Map<String, Any?>
            
            val seoData = data["seo"] as Map<String, Any?>
            val performanceData = data["performance"] as Map<String, Any?>
            val contentData = data["content"] as Map<String, Any?>
            val socialData = data["social"] as Map<String, Any?>
            val technicalData = data["technical"] as Map<String, Any?>
            
            // Build headings structure
            val headingsRaw = seoData["headings"] as Map<String, Any?>
            val headings = HeadingStructure(
                h1 = (headingsRaw["h1"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                h2 = (headingsRaw["h2"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                h3 = (headingsRaw["h3"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                h4 = (headingsRaw["h4"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                h5 = (headingsRaw["h5"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                h6 = (headingsRaw["h6"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
            
            // Build resource count
            val resourceCountRaw = performanceData["resourceCount"] as Map<String, Any?>
            val resourceCount = ResourceCount(
                total = (resourceCountRaw["total"] as? Number)?.toInt() ?: 0,
                images = (resourceCountRaw["images"] as? Number)?.toInt() ?: 0,
                scripts = (resourceCountRaw["scripts"] as? Number)?.toInt() ?: 0,
                stylesheets = (resourceCountRaw["stylesheets"] as? Number)?.toInt() ?: 0,
                fonts = (resourceCountRaw["fonts"] as? Number)?.toInt() ?: 0,
                others = (resourceCountRaw["others"] as? Number)?.toInt() ?: 0
            )
            
            // Build social media data
            val openGraphRaw = (socialData["openGraph"] as? Map<String, Any?>) ?: emptyMap()
            val twitterCardRaw = (socialData["twitterCard"] as? Map<String, Any?>) ?: emptyMap()
            val facebookRaw = (socialData["facebookData"] as? Map<String, Any?>) ?: emptyMap()
            
            val pageMetadata = PageMetadata(
                seo = SeoData(
                    title = seoData["title"] as? String,
                    metaDescription = seoData["metaDescription"] as? String,
                    metaKeywords = seoData["metaKeywords"] as? String,
                    canonicalUrl = seoData["canonicalUrl"] as? String,
                    robotsMeta = seoData["robotsMeta"] as? String,
                    headings = headings,
                    imageAlts = (seoData["imageAlts"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    internalLinks = (seoData["internalLinks"] as? Number)?.toInt() ?: 0,
                    externalLinks = (seoData["externalLinks"] as? Number)?.toInt() ?: 0,
                    schemaMarkup = (seoData["schemaMarkup"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                ),
                performance = PerformanceData(
                    loadTimeMs = (performanceData["loadTimeMs"] as? Number)?.toLong(),
                    domContentLoadedMs = (performanceData["domContentLoadedMs"] as? Number)?.toLong(),
                    resourceCount = resourceCount,
                    pageSize = PageSize(
                        totalBytes = 0L, // Would need detailed resource analysis
                        htmlBytes = 0L,
                        cssBytes = 0L,
                        jsBytes = 0L,
                        imageBytes = 0L,
                        fontBytes = 0L,
                        otherBytes = 0L
                    ),
                    httpStatus = 200 // We'll get this from response
                ),
                content = ContentData(
                    wordCount = (contentData["wordCount"] as? Number)?.toInt() ?: 0,
                    textLength = (contentData["textLength"] as? Number)?.toInt() ?: 0,
                    language = contentData["language"] as? String,
                    headingCount = (contentData["headingCount"] as? Number)?.toInt() ?: 0,
                    imageCount = (contentData["imageCount"] as? Number)?.toInt() ?: 0,
                    linkCount = (contentData["linkCount"] as? Number)?.toInt() ?: 0,
                    hasContactInfo = (contentData["hasContactInfo"] as? Boolean) ?: false
                ),
                social = SocialMediaData(
                    openGraph = OpenGraphData(
                        title = openGraphRaw["title"] as? String,
                        description = openGraphRaw["description"] as? String,
                        image = openGraphRaw["image"] as? String,
                        url = openGraphRaw["url"] as? String,
                        type = openGraphRaw["type"] as? String,
                        siteName = openGraphRaw["siteName"] as? String
                    ),
                    twitterCard = TwitterCardData(
                        card = twitterCardRaw["card"] as? String,
                        title = twitterCardRaw["title"] as? String,
                        description = twitterCardRaw["description"] as? String,
                        image = twitterCardRaw["image"] as? String,
                        site = twitterCardRaw["site"] as? String,
                        creator = twitterCardRaw["creator"] as? String
                    ),
                    facebookData = FacebookData(
                        appId = facebookRaw["appId"] as? String,
                        admins = (facebookRaw["admins"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        pages = (facebookRaw["pages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    )
                ),
                technical = TechnicalData(
                    hasStructuredData = (technicalData["hasStructuredData"] as? Boolean) ?: false,
                    schemaTypes = (technicalData["schemaTypes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    hasRobotsTxt = (technicalData["hasRobotsTxt"] as? Boolean) ?: false,
                    hasSitemap = (technicalData["hasSitemap"] as? Boolean) ?: false,
                    viewport = technicalData["viewport"] as? String,
                    charset = technicalData["charset"] as? String,
                    contentType = technicalData["contentType"] as? String,
                    generator = technicalData["generator"] as? String
                ),
                extractedAt = extractedAt
            )
            
            logger.debug("Metadata extraction completed successfully")
            pageMetadata
            }
            
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.warn("Metadata extraction timed out after 10 seconds for URL: ${page.url()}")
            null // Return null if extraction times out - screenshot still succeeds
        } catch (e: Exception) {
            logger.warn("Failed to extract page metadata: ${e.message}", e)
            null // Return null if extraction fails - screenshot still succeeds
        }
    }

    // Legacy browser args - kept as fallback in case stealth mode fails
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

    private fun generateFilename(
        request: ScreenshotRequest,
        userId: String? = null,
        jobId: String? = null,
        createdAtEpochSeconds: Long? = null,
        extension: String? = null
    ): String {
        // Use secure token-based filename if all secure parameters and token service are available
        if (userId != null && jobId != null && createdAtEpochSeconds != null && screenshotTokenService != null) {
            return screenshotTokenService.generateSecureFilename(
                userId = userId,
                jobId = jobId,
                createdAtEpochSeconds = createdAtEpochSeconds,
                request = request,
                extension = extension
            )
        }

        // Fall back to legacy filename format
        val now = Clock.System.now()
        val instant = now.toLocalDateTime(TimeZone.UTC)
        val year = instant.year
        val month = instant.monthNumber.toString().padStart(2, '0')

        val timestamp = now.toEpochMilliseconds()
        val urlHash = request.url.hashCode().toString().takeLast(8)
        val ext = extension ?: request.format.name.lowercase()
        val dimensions = "${request.width}x${request.height}"

        logger.debug("Using legacy filename format for request without secure parameters")
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

    /**
     * Waits for page to load with graceful fallback strategy.
     * First attempts network idle, then falls back to basic load state if enabled.
     */
    private suspend fun waitForPageLoad(page: Page, url: String) {
        try {
            // Primary strategy: Wait for network idle (best quality)
            page.waitForLoadState(
                LoadState.NETWORKIDLE,
                Page.WaitForLoadStateOptions().setTimeout(config.networkIdleTimeout.toDouble())
            )
        } catch (networkIdleException: Exception) {
            if (config.enableGracefulTimeoutFallback) {
                logger.warn("Network idle timeout for $url, attempting graceful fallback")
                handleGracefulFallback(page, url, networkIdleException)
            } else {
                logger.error("Network idle timeout for $url (fallback disabled)")
                throw networkIdleException
            }
        }
    }

    /**
     * Handles graceful fallback when network idle times out.
     * Attempts basic load state as a last resort.
     */
    private suspend fun handleGracefulFallback(page: Page, url: String, originalException: Exception) {
        try {
            // Fallback strategy: Basic load state (acceptable quality)
            page.waitForLoadState(
                LoadState.LOAD,
                Page.WaitForLoadStateOptions().setTimeout(15_000.0) // Fixed 15s for fallback
            )
            logger.info("Graceful fallback successful for $url")
        } catch (fallbackException: Exception) {
            logger.error("Complete page load failure for $url: both network idle and basic load failed")
            // Return the original network idle exception for better debugging
            throw ScreenshotException.UrlNotAccessible(url, 408)
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
