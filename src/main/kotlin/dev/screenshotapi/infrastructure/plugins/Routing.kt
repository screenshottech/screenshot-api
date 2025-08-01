package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.infrastructure.adapters.input.rest.*
import dev.screenshotapi.infrastructure.auth.AuthCombinations
import dev.screenshotapi.infrastructure.adapters.input.rest.BillingController
import dev.screenshotapi.infrastructure.adapters.input.rest.webhookRoutes
import dev.screenshotapi.infrastructure.adapters.input.rest.configureOcrRoutes
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
import dev.screenshotapi.infrastructure.config.AppConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.io.File

fun Application.configureRouting() {
    val screenshotController by inject<ScreenshotController>()
    val analysisController by inject<AnalysisController>()
    val authController by inject<AuthController>()
    val billingController by inject<BillingController>()
    val adminController by inject<AdminController>()
    val healthController by inject<HealthController>()
    val authProviderFactory by inject<AuthProviderFactory>()
    val appConfig by inject<AppConfig>()

    routing {
        // Health checks (using Cohort at /health)
        get("/status") { healthController.health(call) }  // Moved from /health to /status
        get("/ready") { healthController.ready(call) }
        get("/metrics") { healthController.metrics(call) }

        // Test endpoint for screenshot generation
        get("/test-screenshot") { healthController.testScreenshot(call) }

        // Static file serving for screenshots (uses same path as storage)
        staticFiles("/files", File(appConfig.storage.localPath))

        // API routes
        route("/api/v1") {

            // Public routes
            post("/auth/login") { authController.login(call) }
            post("/auth/register") { authController.register(call) }

            // Multi-provider auth routes
            multiProviderAuthRoutes(authProviderFactory)

            // Billing and subscription routes
            route("/billing") {
                // Public endpoint - no authentication required
                get("/plans") { billingController.getPlans(call) }

                // Authenticated endpoints - require JWT for user management
                authenticate(*AuthCombinations.USER_MANAGEMENT) {
                    post("/checkout") { billingController.createCheckout(call) }
                    get("/subscription") { billingController.getSubscription(call) }
                    post("/portal") { billingController.createBillingPortal(call) }
                }

                // Webhook endpoint - no authentication (signature verification in controller)
                post("/webhook") { billingController.handleWebhook(call) }
            }

            // Screenshot operations - Hybrid authentication (JWT OR API Key OR X-API-Key)
            authenticate(*AuthCombinations.OPERATIONS) {
                // Screenshot creation endpoint with rate limiting
                rateLimit(RateLimitName("screenshots")) {
                    post("/screenshots") { screenshotController.takeScreenshot(call) }
                }

                // Manual retry endpoint for failed/stuck jobs
                post("/screenshots/{jobId}/retry") { screenshotController.retryScreenshot(call) }

                // Screenshot status by job ID
                get("/screenshots/{jobId}") { screenshotController.getScreenshotStatus(call) }

                // Screenshot listing (shows screenshots for authenticated user)
                get("/screenshots") { screenshotController.listScreenshots(call) }

                // Bulk screenshot status endpoint for efficient polling
                post("/screenshots/status/bulk") { screenshotController.getBulkScreenshotStatus(call) }
                
                // Analysis endpoint uses dedicated analysis rate limiting (AI operations are more resource intensive)
                rateLimit(RateLimitName("analysis")) {
                    post("/screenshots/{jobId}/analyze") { analysisController.createAnalysis(call) }
                }
                
                get("/screenshots/{jobId}/analyses") { analysisController.getScreenshotAnalyses(call) }
            }
            
            // Analysis operations - Hybrid authentication (JWT OR API Key OR X-API-Key)
            authenticate(*AuthCombinations.OPERATIONS) {
                route("/analysis") {
                    // Get analysis status and results
                    get("/{analysisJobId}") { analysisController.getAnalysisStatus(call) }
                    
                    // List user's analyses with pagination and filtering
                    get { analysisController.listAnalyses(call) }
                }
            }

            // OCR operations - API Key authentication
            configureOcrRoutes()

            // User management endpoints - JWT authentication only
            authenticate(*AuthCombinations.USER_MANAGEMENT) {
                route("/user") {
                    get("/profile") { authController.getProfile(call) }
                    put("/profile") { authController.updateProfile(call) }

                    get("/api-keys") { authController.listApiKeys(call) }
                    post("/api-keys") { authController.createApiKey(call) }
                    patch("/api-keys/{keyId}") { authController.updateApiKey(call) }
                    delete("/api-keys/{keyId}") { authController.deleteApiKey(call) }

                    get("/usage") { authController.getUsage(call) }
                    get("/usage/timeline") { authController.getUsageTimeline(call) }
                    get("/email-logs") { authController.getEmailLogs(call) }
                }
            }

            // Webhook management - Hybrid authentication (JWT OR API Key OR X-API-Key)
            authenticate(*AuthCombinations.OPERATIONS) {
                webhookRoutes()
            }

            // Admin endpoints - JWT authentication only
            authenticate(*AuthCombinations.USER_MANAGEMENT) {
                route("/admin") {
                    get("/users") { adminController.listUsers(call) }
                    get("/users/{userId}") { adminController.getUser(call) }
                    get("/stats") { adminController.getStats(call) }

                    // Subscription management
                    get("/subscriptions") { adminController.listSubscriptions(call) }
                    get("/subscriptions/{subscriptionId}") { adminController.getSubscriptionDetails(call) }
                    post("/subscriptions/{subscriptionId}/provision-credits") { adminController.provisionSubscriptionCredits(call) }
                    post("/users/{userId}/synchronize-plan") { adminController.synchronizeUserPlan(call) }
                }
            }
        }
    }

}

