package dev.screenshotapi.infrastructure

import dev.screenshotapi.infrastructure.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureDependencyInjection()
    configureSecurity()
    configureHTTP()
    configureSerialization()
    configureAdministration()
    configureCORS()
    configureMonitoring()
    configureOpenAPI()
    configureRateLimit()
    configureRouting()
    configureExceptionHandling()
    initializeApplication()
}

