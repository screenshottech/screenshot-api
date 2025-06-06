package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.infrastructure.adapters.input.rest.*
import dev.screenshotapi.infrastructure.adapters.input.rest.multiProviderAuthRoutes
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.io.File

fun Application.configureRouting() {
    val screenshotController by inject<ScreenshotController>()
    val authController by inject<AuthController>()
    val adminController by inject<AdminController>()
    val healthController by inject<HealthController>()
    val authProviderFactory by inject<AuthProviderFactory>()

    routing {
        // Health checks
        get("/health") { healthController.health(call) }
        get("/ready") { healthController.ready(call) }
        get("/metrics") { healthController.metrics(call) }

        // Test endpoint for screenshot generation
        get("/test-screenshot") { healthController.testScreenshot(call) }

        // Static file serving for screenshots
        staticFiles("/files", File("./screenshots"))

        // API routes
        route("/api/v1") {

            // Public routes
            post("/auth/login") { authController.login(call) }
            post("/auth/register") { authController.register(call) }
            
            // Multi-provider auth routes
            multiProviderAuthRoutes(authProviderFactory)

            // Screenshot endpoints with multiple authentication support
            // Supports both: JWT + X-API-Key header, or standalone API key
            authenticate("api-key", "jwt", optional = true) {
                // Screenshot creation endpoint with rate limiting
                rateLimit(RateLimitName("screenshots")) {
                    post("/screenshots") { screenshotController.takeScreenshot(call) }
                }
                
                // Screenshot status by job ID
                get("/screenshots/{jobId}") { screenshotController.getScreenshotStatus(call) }
                
                // Screenshot listing (requires authentication)
                get("/screenshots") { screenshotController.listScreenshots(call) }

                route("/admin") {
                    get("/users") { adminController.listUsers(call) }
                    get("/users/{userId}") { adminController.getUser(call) }
                    get("/stats") { adminController.getStats(call) }
                }

                route("/user") {
                    get("/profile") { authController.getProfile(call) }
                    put("/profile") { authController.updateProfile(call) }

                    get("/api-keys") { authController.listApiKeys(call) }
                    post("/api-keys") { authController.createApiKey(call) }
                    delete("/api-keys/{keyId}") { authController.deleteApiKey(call) }

                    get("/usage") { authController.getUsage(call) }
                }
            }
        }
    }

}

