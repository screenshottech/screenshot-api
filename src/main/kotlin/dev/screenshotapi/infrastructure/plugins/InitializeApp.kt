package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.infrastructure.config.initializeDatabase
import dev.screenshotapi.infrastructure.services.ScreenshotServiceImpl
import dev.screenshotapi.workers.WorkerManager
import io.ktor.server.application.*
import org.koin.ktor.ext.get

fun Application.initializeApplication() {
    val appConfig = AppConfig.load()

    if (!appConfig.database.useInMemory) {
        initializeDatabase()
    }

    log.info("Starting worker manager...")
    get<WorkerManager>().start()
    log.info("Worker manager started")

    monitor.subscribe(ApplicationStopping) {
        try {
            get<WorkerManager>().shutdown()

            (get<ScreenshotService>() as? ScreenshotServiceImpl)?.cleanup()

            log.info("Application shutdown completed")
        } catch (e: Exception) {
            log.error("Error during application shutdown", e)
        }
    }

    log.info("Application initialized successfully")
}

