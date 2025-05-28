package dev.screenshotapi.infrastructure.services

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class BrowserPoolManager(
    private val config: ScreenshotConfig
) {
    private val logger = LoggerFactory.getLogger(BrowserPoolManager::class.java)

    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private val contexts = ConcurrentLinkedQueue<BrowserContext>()
    private val activeContexts = AtomicInteger(0)
    private val mutex = Mutex()

    private val maxContexts = config.browserPoolSize
    private val minContexts = 2

    suspend fun initialize() {
        mutex.withLock {
            if (playwright != null) return

            try {
                playwright = Playwright.create()
                browser = playwright!!.chromium().launch(
                    BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(
                            listOf(
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--disable-extensions",
                                "--disable-plugins",
                                "--disable-images", // Disable image loading for faster performance
                                "--disable-javascript" // Can be enabled per context if needed
                            )
                        )
                )

                // Pre-create minimum contexts
                repeat(minContexts) {
                    contexts.offer(createContext())
                }

                logger.info("Browser pool initialized with $minContexts contexts")
            } catch (e: Exception) {
                logger.error("Failed to initialize browser pool", e)
                throw e
            }
        }
    }

    suspend fun acquireContext(): BrowserContext {
        mutex.withLock {
            // Try to get existing context
            contexts.poll()?.let { context ->
                activeContexts.incrementAndGet()
                return context
            }

            // Create new context if under limit
            if (activeContexts.get() < maxContexts) {
                val context = createContext()
                activeContexts.incrementAndGet()
                return context
            }

            // If we're at the limit, create a temporary context
            logger.warn("Browser pool at capacity, creating temporary context")
            return createContext()
        }
    }

    suspend fun releaseContext(context: BrowserContext) {
        try {
            // Clear all pages in the context
            context.pages().forEach { page ->
                try {
                    page.close()
                } catch (e: Exception) {
                    logger.warn("Error closing page", e)
                }
            }

            // If we have too many contexts, close this one
            if (contexts.size >= minContexts) {
                context.close()
                activeContexts.decrementAndGet()
            } else {
                // Return to pool
                contexts.offer(context)
                activeContexts.decrementAndGet()
            }
        } catch (e: Exception) {
            logger.error("Error releasing browser context", e)
            activeContexts.decrementAndGet()
        }
    }

    private fun createContext(): BrowserContext {
        val browser = this.browser ?: throw IllegalStateException("Browser not initialized")

        return browser.newContext(
            Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .setJavaScriptEnabled(true)
                .setIgnoreHTTPSErrors(true)
        )
    }

    suspend fun cleanup() {
        mutex.withLock {
            logger.info("Cleaning up browser pool...")

            // Close all contexts
            while (contexts.isNotEmpty()) {
                contexts.poll()?.close()
            }

            // Close browser
            browser?.close()

            // Close playwright
            playwright?.close()

            playwright = null
            browser = null
            activeContexts.set(0)

            logger.info("Browser pool cleanup completed")
        }
    }

    fun getStats(): BrowserPoolStats {
        return BrowserPoolStats(
            totalContexts = activeContexts.get(),
            availableContexts = contexts.size,
            maxContexts = maxContexts,
            minContexts = minContexts
        )
    }
}

data class BrowserPoolStats(
    val totalContexts: Int,
    val availableContexts: Int,
    val maxContexts: Int,
    val minContexts: Int
)
