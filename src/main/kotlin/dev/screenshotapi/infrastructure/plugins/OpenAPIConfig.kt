package dev.screenshotapi.infrastructure.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureOpenAPI() {
    routing {
        // Serve Swagger UI at /swagger
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml") {
            version = "4.15.5"
        }

        // Serve OpenAPI specification at /openapi
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
    }
}

// Data classes for OpenAPI documentation
data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)

data class ScreenshotRequest(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val fullPage: Boolean = false,
    val format: String = "png",
    val quality: Int = 90,
    val delay: Int = 0
)

data class ScreenshotResponse(
    val jobId: String,
    val status: String,
    val url: String? = null,
    val timestamp: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val expiresIn: Long
)

data class ApiKeyResponse(
    val id: String,
    val name: String,
    val prefix: String,
    val key: String, // Only shown once on creation
    val createdAt: String,
    val lastUsedAt: String?
)
