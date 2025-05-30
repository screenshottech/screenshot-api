package dev.screenshotapi.infrastructure

import dev.screenshotapi.infrastructure.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureDependencyInjection()
    configureSecurity()
    configureSerialization()
    configureAdministration()
    configureHTTP()
    configureMonitoring()
    configureRateLimit()
    configureRouting()
    configureExceptionHandling()
    initializeApplication()
}

