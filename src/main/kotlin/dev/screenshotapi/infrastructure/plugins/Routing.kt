package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.infrastructure.adapters.input.rest.AdminController
import dev.screenshotapi.infrastructure.adapters.input.rest.AuthController
import dev.screenshotapi.infrastructure.adapters.input.rest.HealthController
import dev.screenshotapi.infrastructure.adapters.input.rest.ScreenshotController
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.io.File

fun Application.configureRouting() {
    val screenshotController by inject<ScreenshotController>()
    val authController by inject<AuthController>()
    val adminController by inject<AdminController>()
    val healthController by inject<HealthController>()

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

            // Protected routes (API Key authentication)
            authenticate("api-key") {
                post("/screenshot") { screenshotController.takeScreenshot(call) }
                get("/screenshot/{jobId}") { screenshotController.getScreenshotStatus(call) }
                get("/screenshots") { screenshotController.listScreenshots(call) }
            }

            // Admin routes (JWT authentication)
            authenticate("jwt") {
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
