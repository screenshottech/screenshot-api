package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.infrastructure.config.Environment
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*


fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-API-Key")
        allowHeader("X-API-Key-ID")
        allowHeader("X-Webhook-URL")


        val environment = Environment.current()
        if (environment.isLocal) {
            anyHost() // Allow all hosts in local development
            allowHost("localhost:3000") // Next.js default port
            allowHost("127.0.0.1:3000")
        } else {
            // Production CORS configuration for public API
            anyHost() // Allow all hosts for public API access
        }

        allowCredentials = true
    }
}
