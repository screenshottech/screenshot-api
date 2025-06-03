package dev.screenshotapi.infrastructure.adapters.input.rest


import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.workers.WorkerManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HealthController : KoinComponent {
    private val config: AppConfig by inject()
    private val workerManager: WorkerManager by inject()

    suspend fun health(call: ApplicationCall) {
        val healthStatus = HealthResponse(
            service = "Screenshot API",
            version = "1.0.0",
            status = "OK",
            timestamp = Clock.System.now().toString(),
            environment = config.environment.name,
            features = mapOf(
                "inMemoryDatabase" to config.database.useInMemory,
                "inMemoryQueue" to config.redis.useInMemory,
                "localStorage" to config.storage.useLocal
            )
        )

        call.respond(HttpStatusCode.OK, healthStatus)
    }

    suspend fun ready(call: ApplicationCall) {
        try {

            val isReady = checkReadiness()

            if (isReady.ready) {
                call.respond(HttpStatusCode.OK, isReady)
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, isReady)
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ReadinessResponse(
                    ready = false,
                    timestamp = Clock.System.now().toString(),
                    checks = mapOf("error" to (e.message ?: "Unknown error"))
                )
            )
        }
    }

    suspend fun metrics(call: ApplicationCall) {
        val workerStatus = workerManager.getStatus()

        val metrics = MetricsResponse(
            timestamp = Clock.System.now().toString(),
            workers = WorkerMetrics(
                active = workerStatus.activeWorkers,
                min = workerStatus.minWorkers,
                max = workerStatus.maxWorkers
            ),
            system = SystemMetrics(
                memory = getMemoryUsage(),
                uptime = getUptime()
            )
        )

        call.respond(HttpStatusCode.OK, metrics)
    }

    suspend fun testScreenshot(call: ApplicationCall) {
        try {
            val screenshotService: ScreenshotService by inject()

            val request = dev.screenshotapi.core.domain.entities.ScreenshotRequest(
                url = "https://carboit.com",
                width = 1200,
                height = 800,
                fullPage = true,
                format = dev.screenshotapi.core.domain.entities.ScreenshotFormat.PNG
            )

            val resultUrl = screenshotService.takeScreenshot(request)

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "status" to "success",
                    "message" to "Screenshot taken successfully",
                    "url" to resultUrl
                )
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "status" to "error",
                    "message" to "Failed to take screenshot: ${e.message}",
                    "stackTrace" to e.stackTraceToString()
                )
            )
        }
    }

    private suspend fun checkReadiness(): ReadinessResponse {
        val checks = mutableMapOf<String, String>()
        var allReady = true

        // Check database (if not in-memory)
        if (!config.database.useInMemory) {
            try {
                // TODO: Add actual database check
                checks["database"] = "OK"
            } catch (e: Exception) {
                checks["database"] = "FAILED: ${e.message}"
                allReady = false
            }
        }

        // Check redis (if not in-memory)
        if (!config.redis.useInMemory) {
            try {
                // TODO: Add actual redis check
                checks["redis"] = "OK"
            } catch (e: Exception) {
                checks["redis"] = "FAILED: ${e.message}"
                allReady = false
            }
        }

        // Check workers
        val workerStatus = workerManager.getStatus()
        checks["workers"] = "${workerStatus.activeWorkers}/${workerStatus.maxWorkers}"
        if (workerStatus.activeWorkers == 0) {
            allReady = false
        }

        return ReadinessResponse(
            ready = allReady,
            timestamp = Clock.System.now().toString(),
            checks = checks
        )
    }

    private fun getMemoryUsage(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()

        return MemoryInfo(
            used = usedMemory,
            free = freeMemory,
            total = totalMemory,
            max = maxMemory,
            usagePercent = (usedMemory * 100.0 / maxMemory).toInt()
        )
    }

    private fun getUptime(): Long {
        return System.currentTimeMillis() - startTime
    }

    companion object {
        private val startTime = System.currentTimeMillis()
    }
}

@Serializable
data class HealthResponse(
    val service: String,
    val version: String,
    val status: String,
    val timestamp: String,
    val environment: String,
    val features: Map<String, Boolean>
)

@Serializable
data class ReadinessResponse(
    val ready: Boolean,
    val timestamp: String,
    val checks: Map<String, String>
)

@Serializable
data class MetricsResponse(
    val timestamp: String,
    val workers: WorkerMetrics,
    val system: SystemMetrics
)

@Serializable
data class WorkerMetrics(
    val active: Int,
    val min: Int,
    val max: Int
)

@Serializable
data class SystemMetrics(
    val memory: MemoryInfo,
    val uptime: Long
)

@Serializable
data class MemoryInfo(
    val used: Long,
    val free: Long,
    val total: Long,
    val max: Long,
    val usagePercent: Int
)
