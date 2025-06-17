package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.infrastructure.config.Environment
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.cors.routing.*
import kotlin.time.Duration.Companion.hours


fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        // Headers for API access
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-API-Key")
        allowHeader("X-API-Key-ID")
        allowHeader("X-Webhook-URL")

        // Headers for monitoring and debugging
        allowHeader("X-Request-ID")
        allowHeader("User-Agent")

        val environment = Environment.current()
        if (environment.isLocal) {
            anyHost() // Allow all hosts in local development
            allowHost("localhost:3000") // Next.js default port
            allowHost("127.0.0.1:3000")
        } else {
            anyHost()
        }

        // Allow credentials for dashboard authentication
        allowCredentials = true

        // Cache preflight requests for better performance
        maxAgeDuration = 1.hours
    }
}
