package dev.screenshotapi.infrastructure.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureAdministration() {
    // Administration routes and configuration
    routing {
        route("/") {
            // Administration routes go here
        }
    }
}
