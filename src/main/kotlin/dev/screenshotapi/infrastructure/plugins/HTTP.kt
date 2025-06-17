package dev.screenshotapi.infrastructure.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP() {
    // Security Headers Configuration
    install(DefaultHeaders) {
        // === MINIMAL SAFE HEADERS (WON'T BREAK ANYTHING) ===

        // Prevent MIME type sniffing - SAFE ✅
        header("X-Content-Type-Options", "nosniff")

        // XSS Protection (legacy browsers) - SAFE ✅
        header("X-XSS-Protection", "1; mode=block")

        // Referrer Policy - SAFE ✅
        header("Referrer-Policy", "strict-origin-when-cross-origin")

        // Custom API headers - INFORMATIONAL ✅
        header("X-API-Version", "1.0")
        header("X-Powered-By", "ScreenshotAPI")

        // === HEADERS TO ENABLE LATER (COMMENTED FOR TESTING) ===

        // TODO: Enable when you don't use iframes
        // header("X-Frame-Options", "SAMEORIGIN")  // Use SAMEORIGIN instead of DENY

        // TODO: Enable when you have HTTPS with SSL certificate
        // if (Environment.current().isProduction) {
        //     header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        // }

        // TODO: Content Security Policy - Very restrictive, enable last
        // header("Content-Security-Policy", "default-src *; script-src * 'unsafe-inline'; style-src * 'unsafe-inline';")

        // TODO: Permissions Policy - Enable if you don't use these features
        // header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    }
}
