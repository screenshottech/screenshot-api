package dev.screenshotapi.infrastructure.plugins


import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.cpu.ProcessCpuHealthCheck
import com.sksamuel.cohort.memory.FreememHealthCheck
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

fun Application.configureMonitoring() {

    val healthchecks = HealthCheckRegistry(Dispatchers.Default) {
        register(FreememHealthCheck.mb(250), 10.seconds, 10.seconds)
        register(ProcessCpuHealthCheck(0.8), 10.seconds, 10.seconds)
    }

    install(Cohort) {

        // enable an endpoint to display operating system name and version
        operatingSystem = true

        // enable runtime JVM information such as vm options and vendor name
        jvmInfo = true

        // show current system properties
        sysprops = true

        // enable an endpoint to dump the heap in hprof format
        heapDump = true

        // enable an endpoint to dump threads
        threadDump = true

        // set to true to return the detailed status of the healthcheck response
        verboseHealthCheckResponse = true

        // enable healthchecks for kubernetes
        healthcheck("/health", healthchecks)
    }

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry

        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .build()
    }

    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
    install(CallLogging) {
        callIdMdc("call-id")
    }
    routing {
        get("/metrics-micrometer") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}
