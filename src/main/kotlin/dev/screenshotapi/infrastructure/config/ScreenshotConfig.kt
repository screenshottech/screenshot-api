package dev.screenshotapi.infrastructure.config

import dev.screenshotapi.core.domain.entities.ScreenshotFormat
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class ScreenshotConfig(
    val maxWidth: Int,
    val maxHeight: Int,
    val minWidth: Int,
    val minHeight: Int,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val maxWaitTime: Long,
    val defaultWaitTime: Long,
    val defaultTimeout: Long,
    val maxTimeout: Long,
    val browserPoolSize: Int,
    val maxBrowserInstances: Int,
    val browserLaunchTimeout: Long,
    val pageLoadTimeout: Long,
    val networkIdleTimeout: Long,
    val allowedDomains: List<String>?,
    val blockedDomains: List<String>,
    val supportedFormats: List<String>,
    val defaultFormat: String,
    val maxQuality: Int,
    val minQuality: Int,
    val defaultQuality: Int,
    val maxFileSize: Long,
    val enableFullPageScreenshots: Boolean,
    val enablePdfGeneration: Boolean,
    val enableMobileViewport: Boolean,
    val userAgent: String,
    val enableJavaScript: Boolean,
    val enableImages: Boolean,
    val enableCSS: Boolean,
    val retryAttempts: Int,
    val retryDelayMs: Long,
    val concurrentScreenshots: Int,
    val queueMaxSize: Long,
    val cleanupOldScreenshots: Boolean,
    val screenshotRetentionDays: Int,
    val selectorTimeoutDuration: Duration = 10.seconds,
    val maxConcurrentRequests: Int,
    val retrySchedulerEnabled: Boolean,
    val enableGracefulTimeoutFallback: Boolean,
    val enableStealthMode: Boolean,
    val stealthUserAgent: String?,
    val enableStealthJavaScript: Boolean
) {
    companion object {
        fun load(): ScreenshotConfig = ScreenshotConfig(
            maxWidth = System.getenv("SCREENSHOT_MAX_WIDTH")?.toInt()
                ?: 1920,
            maxHeight = System.getenv("SCREENSHOT_MAX_HEIGHT")?.toInt()
                ?: 1080,
            minWidth = System.getenv("SCREENSHOT_MIN_WIDTH")?.toInt()
                ?: 320,
            minHeight = System.getenv("SCREENSHOT_MIN_HEIGHT")?.toInt()
                ?: 240,
            defaultWidth = System.getenv("SCREENSHOT_DEFAULT_WIDTH")?.toInt()
                ?: 1920,
            defaultHeight = System.getenv("SCREENSHOT_DEFAULT_HEIGHT")?.toInt()
                ?: 1080,
            maxWaitTime = System.getenv("SCREENSHOT_MAX_WAIT_TIME")?.toLong()
                ?: 10_000L,
            defaultWaitTime = System.getenv("SCREENSHOT_DEFAULT_WAIT_TIME")?.toLong()
                ?: 1_000L,
            defaultTimeout = System.getenv("SCREENSHOT_DEFAULT_TIMEOUT")?.toLong()
                ?: 30_000L,
            maxTimeout = System.getenv("SCREENSHOT_MAX_TIMEOUT")?.toLong()
                ?: 60_000L,
            browserPoolSize = System.getenv("BROWSER_POOL_SIZE")?.toInt()
                ?: 3,
            maxBrowserInstances = System.getenv("MAX_BROWSER_INSTANCES")?.toInt()
                ?: 10,
            browserLaunchTimeout = System.getenv("BROWSER_LAUNCH_TIMEOUT")?.toLong()
                ?: 60_000L,
            pageLoadTimeout = System.getenv("PAGE_LOAD_TIMEOUT")?.toLong()
                ?: 45_000L,
            networkIdleTimeout = System.getenv("NETWORK_IDLE_TIMEOUT")?.toLong()
                ?: 45_000L,
            allowedDomains = System.getenv("ALLOWED_DOMAINS")?.split(",")?.map { it.trim() },
            blockedDomains = System.getenv("BLOCKED_DOMAINS")?.split(",")?.map { it.trim() }
                ?: listOf("localhost", "127.0.0.1", "0.0.0.0", "internal", "private"),
            supportedFormats = System.getenv("SUPPORTED_FORMATS")?.split(",")?.map { it.trim() }
                ?: ScreenshotFormat.getSupportedFormats(),
            defaultFormat = System.getenv("DEFAULT_FORMAT")?.uppercase()
                ?: ScreenshotFormat.PNG.name,
            maxQuality = System.getenv("SCREENSHOT_MAX_QUALITY")?.toInt()
                ?: 100,
            minQuality = System.getenv("SCREENSHOT_MIN_QUALITY")?.toInt()
                ?: 1,
            defaultQuality = System.getenv("SCREENSHOT_DEFAULT_QUALITY")?.toInt()
                ?: 80,
            maxFileSize = System.getenv("MAX_FILE_SIZE")?.toLong()
                ?: (10 * 1024 * 1024L), // 10MB
            enableFullPageScreenshots = System.getenv("ENABLE_FULL_PAGE")?.toBoolean()
                ?: true,
            enablePdfGeneration = System.getenv("ENABLE_PDF_GENERATION")?.toBoolean()
                ?: true,
            enableMobileViewport = System.getenv("ENABLE_MOBILE_VIEWPORT")?.toBoolean()
                ?: true,
            userAgent = System.getenv("USER_AGENT")
                ?: "ScreenshotAPI/1.0 (Playwright)",
            enableJavaScript = System.getenv("ENABLE_JAVASCRIPT")?.toBoolean()
                ?: true,
            enableImages = System.getenv("ENABLE_IMAGES")?.toBoolean()
                ?: true,
            enableCSS = System.getenv("ENABLE_CSS")?.toBoolean()
                ?: true,
            retryAttempts = System.getenv("RETRY_ATTEMPTS")?.toInt()
                ?: 3,
            retryDelayMs = System.getenv("RETRY_DELAY_MS")?.toLong()
                ?: 1_000L,
            concurrentScreenshots = System.getenv("CONCURRENT_SCREENSHOTS")?.toInt()
                ?: 5,
            queueMaxSize = System.getenv("QUEUE_MAX_SIZE")?.toLong()
                ?: 1000L,
            cleanupOldScreenshots = System.getenv("CLEANUP_OLD_SCREENSHOTS")?.toBoolean()
                ?: true,
            screenshotRetentionDays = System.getenv("SCREENSHOT_RETENTION_DAYS")?.toInt()
                ?: 30,
            maxConcurrentRequests = System.getenv("MAX_CONCURRENT_REQUESTS")?.toInt()
                ?: 10,
            retrySchedulerEnabled = System.getenv("RETRY_SCHEDULER_ENABLED")?.toBoolean()
                ?: true,
            enableGracefulTimeoutFallback = System.getenv("ENABLE_GRACEFUL_TIMEOUT_FALLBACK")?.toBoolean()
                ?: true,
            enableStealthMode = System.getenv("ENABLE_STEALTH_MODE")?.toBoolean()
                ?: true,
            stealthUserAgent = System.getenv("STEALTH_USER_AGENT"),
            enableStealthJavaScript = System.getenv("ENABLE_STEALTH_JAVASCRIPT")?.toBoolean()
                ?: true
        )
    }

    // Computed properties
    val maxWaitDuration: Duration get() = maxWaitTime.milliseconds
    val defaultWaitDuration: Duration get() = defaultWaitTime.milliseconds
    val defaultTimeoutDuration: Duration get() = defaultTimeout.milliseconds
    val maxTimeoutDuration: Duration get() = maxTimeout.milliseconds
    val browserLaunchTimeoutDuration: Duration get() = browserLaunchTimeout.milliseconds
    val pageLoadTimeoutDuration: Duration get() = pageLoadTimeout.milliseconds
    val networkIdleTimeoutDuration: Duration get() = networkIdleTimeout.milliseconds
    val retryDelay: Duration get() = retryDelayMs.milliseconds

    // Validation methods
    fun isValidDimensions(width: Int, height: Int): Boolean {
        return width in minWidth..maxWidth && height in minHeight..maxHeight
    }

    fun isValidFormat(format: String): Boolean {
        return supportedFormats.contains(format.uppercase())
    }

    fun isValidQuality(quality: Int): Boolean {
        return quality in minQuality..maxQuality
    }

    fun isValidWaitTime(waitTime: Long): Boolean {
        return waitTime in 0..maxWaitTime
    }

    fun isValidTimeout(timeout: Long): Boolean {
        return timeout in 1000..maxTimeout
    }

    fun isDomainAllowed(domain: String): Boolean {
        if (allowedDomains == null) {
            return !isDomainBlocked(domain)
        }

        return allowedDomains.any { allowedDomain ->
            domain.equals(allowedDomain, ignoreCase = true) ||
                    domain.endsWith(".$allowedDomain", ignoreCase = true)
        } && !isDomainBlocked(domain)
    }

    fun isDomainBlocked(domain: String): Boolean {
        return blockedDomains.any { blockedDomain ->
            domain.equals(blockedDomain, ignoreCase = true) ||
                    domain.endsWith(".$blockedDomain", ignoreCase = true) ||
                    domain.contains(blockedDomain, ignoreCase = true)
        }
    }

    fun isValidUrl(url: String): Boolean {
        try {
            val uri = URI(url)

            if (uri.scheme !in listOf("http", "https")) {
                return false
            }

            val host = uri.host ?: return false

            if (!isDomainAllowed(host)) {
                return false
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun getOptimalBrowserArgs(): List<String> {
        return if (enableStealthMode) {
            getStealthBrowserArgs()
        } else {
            getStandardBrowserArgs()
        }
    }

    fun getStealthBrowserArgs(): List<String> {
        return buildList {
            // Core stealth args
            add("--headless=new") // New headless mode - harder to detect
            add("--no-sandbox")
            add("--disable-setuid-sandbox")
            add("--disable-dev-shm-usage")
            
            // Anti-detection specific
            add("--disable-blink-features=AutomationControlled")
            add("--disable-features=VizDisplayCompositor")
            add("--disable-extensions-except=")
            add("--disable-plugins-discovery")
            add("--disable-default-apps")
            
            // Simulate real browser
            add("--enable-features=NetworkService,NetworkServiceLogging")
            add("--disable-background-timer-throttling")
            add("--disable-backgrounding-occluded-windows")
            add("--disable-renderer-backgrounding")
            add("--disable-features=TranslateUI")
            add("--disable-component-extensions-with-background-pages")
            
            // Performance in container
            add("--disable-gpu")
            add("--hide-scrollbars")
            add("--mute-audio")
            add("--no-first-run")
            add("--disable-sync")
            add("--disable-background-networking")
            
            // Memory optimization
            add("--memory-pressure-off")
            add("--max_old_space_size=4096")

            // Custom user agent
            val userAgentToUse = getEffectiveUserAgent()
            add("--user-agent=$userAgentToUse")

            if (!enableImages) {
                add("--blink-settings=imagesEnabled=false")
            }

            if (!enableJavaScript) {
                add("--disable-javascript")
            }
        }
    }

    fun getStandardBrowserArgs(): List<String> {
        return buildList {
            add("--headless") // Standard headless mode
            add("--no-sandbox")
            add("--disable-setuid-sandbox")
            add("--disable-dev-shm-usage")
            add("--disable-gpu")
            add("--disable-background-timer-throttling")
            add("--disable-backgrounding-occluded-windows")
            add("--disable-renderer-backgrounding")

            if (!enableImages) {
                add("--blink-settings=imagesEnabled=false")
            }

            if (!enableJavaScript) {
                add("--disable-javascript")
            }

            // Memory optimization
            add("--memory-pressure-off")
            add("--max_old_space_size=4096")
        }
    }

    fun getEffectiveUserAgent(): String {
        return stealthUserAgent ?: 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
    }

    fun getStealthJavaScript(): String {
        return if (enableStealthJavaScript) {
            """
            // Hide webdriver property
            Object.defineProperty(navigator, 'webdriver', {
                get: () => undefined,
            });
            
            // Hide automation characteristics
            window.navigator.chrome = {
                runtime: {},
            };
            
            // Mock plugins to simulate real browser
            Object.defineProperty(navigator, 'plugins', {
                get: () => [1, 2, 3, 4, 5],
            });
            
            // Mock languages
            Object.defineProperty(navigator, 'languages', {
                get: () => ['en-US', 'en'],
            });
            
            // Permissions API mock
            const originalQuery = window.navigator.permissions.query;
            window.navigator.permissions.query = (parameters) => (
                parameters.name === 'notifications' ?
                    Promise.resolve({ state: Notification.permission }) :
                    originalQuery(parameters)
            );
            
            // WebGL vendor info
            const getParameter = WebGLRenderingContext.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(parameter) {
                if (parameter === 37445) {
                    return 'Intel Inc.';
                }
                if (parameter === 37446) {
                    return 'Intel Iris OpenGL Engine';
                }
                return getParameter(parameter);
            };
            """.trimIndent()
        } else {
            ""
        }
    }

    fun getMobileViewportConfig(): ViewportConfig? {
        return if (enableMobileViewport) {
            ViewportConfig(
                width = 375,
                height = 667,
                deviceScaleFactor = 2.0,
                isMobile = true,
                hasTouch = true
            )
        } else null
    }
}

data class ViewportConfig(
    val width: Int,
    val height: Int,
    val deviceScaleFactor: Double = 1.0,
    val isMobile: Boolean = false,
    val hasTouch: Boolean = false
)
