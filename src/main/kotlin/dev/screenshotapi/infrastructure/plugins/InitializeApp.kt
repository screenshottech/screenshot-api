package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.infrastructure.config.initializeDatabase
import dev.screenshotapi.infrastructure.services.StatsAggregationScheduler
import dev.screenshotapi.infrastructure.services.ScreenshotServiceImpl
import dev.screenshotapi.workers.WebhookRetryWorker
import dev.screenshotapi.workers.WorkerManager
import dev.screenshotapi.workers.AnalysisWorkerManager
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

    log.info("Starting analysis worker manager...")
    get<AnalysisWorkerManager>().start()
    log.info("Analysis worker manager started")

    // Start stats aggregation scheduler
    log.info("Starting stats aggregation scheduler...")
    get<StatsAggregationScheduler>().start()
    log.info("Stats aggregation scheduler started")

    // Start webhook retry worker
    log.info("Starting webhook retry worker...")
    get<WebhookRetryWorker>().start()
    log.info("Webhook retry worker started")

    monitor.subscribe(ApplicationStopping) {
        try {
            get<WorkerManager>().shutdown()
            get<AnalysisWorkerManager>().shutdown()

            // Stop stats aggregation scheduler
            get<StatsAggregationScheduler>().stop()
            log.info("Stats aggregation scheduler stopped")

            // Stop webhook retry worker
            get<WebhookRetryWorker>().stop()
            log.info("Webhook retry worker stopped")

            (get<ScreenshotService>() as? ScreenshotServiceImpl)?.cleanup()

            log.info("Application shutdown completed")
        } catch (e: Exception) {
            log.error("Error during application shutdown", e)
        }
    }

    log.info("Application initialized successfully")
}

