package dev.screenshotapi.infrastructure.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

/**
 * Configures OpenAPI documentation and Swagger UI for the Screenshot API.
 * 
 * Available endpoints:
 * - /swagger - Interactive Swagger UI for testing API endpoints
 * - /openapi - Raw OpenAPI 3.0 specification in YAML format
 * - /docs - Alternative path to Swagger UI for discoverability
 * 
 * The documentation includes:
 * - Complete API reference with request/response schemas
 * - Authentication methods (API Key, JWT, API Key ID)
 * - Usage analytics and timeline endpoints
 * - Error response formats
 * - Rate limiting information
 */
fun Application.configureOpenAPI() {
    routing {
        // Serve comprehensive Swagger UI at /swagger
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml") {
            version = "4.15.5"
        }

        // Serve OpenAPI specification at /openapi for API clients
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
        
        // Also serve documentation at /docs for discoverability
        swaggerUI(path = "docs", swaggerFile = "openapi/documentation.yaml") {
            version = "4.15.5"
        }
    }
}

// Note: Data classes for OpenAPI documentation are now defined in the actual DTO files
// to maintain consistency between runtime DTOs and documentation schemas.
// See: src/main/kotlin/dev/screenshotapi/infrastructure/adapters/input/rest/dto/
