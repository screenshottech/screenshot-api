package dev.screenshotapi.infrastructure

import dev.screenshotapi.core.domain.repositories.*
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.core.services.ScreenshotService
import dev.screenshotapi.core.usecases.admin.*
import dev.screenshotapi.core.usecases.auth.*
import dev.screenshotapi.core.usecases.billing.CheckCreditsUseCase
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.core.usecases.screenshot.GetScreenshotStatusUseCase
import dev.screenshotapi.core.usecases.screenshot.ListScreenshotsUseCase
import dev.screenshotapi.core.usecases.screenshot.TakeScreenshotUseCase
import dev.screenshotapi.infrastructure.adapters.input.rest.AdminController
import dev.screenshotapi.infrastructure.adapters.input.rest.AuthController
import dev.screenshotapi.infrastructure.adapters.input.rest.HealthController
import dev.screenshotapi.infrastructure.adapters.input.rest.ScreenshotController
import dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory.InMemoryActivityRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory.InMemoryApiKeyRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory.InMemoryScreenshotRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory.InMemoryUserRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.PostgreSQLActivityRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.PostgreSQLApiKeyRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.PostgreSQLScreenshotRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.PostgreSQLUserRepository
import dev.screenshotapi.infrastructure.adapters.output.queue.inmemory.InMemoryQueueAdapter
import dev.screenshotapi.infrastructure.adapters.output.queue.redis.RedisQueueAdapter
import dev.screenshotapi.infrastructure.adapters.output.storage.StorageFactory
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.infrastructure.config.Environment
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import dev.screenshotapi.infrastructure.config.initializeDatabase
import dev.screenshotapi.infrastructure.plugins.*
import dev.screenshotapi.infrastructure.services.BrowserPoolManager
import dev.screenshotapi.infrastructure.services.MetricsService
import dev.screenshotapi.infrastructure.services.NotificationService
import dev.screenshotapi.infrastructure.services.ScreenshotServiceImpl
import dev.screenshotapi.workers.WorkerManager
import io.github.flaxoos.ktor.server.plugins.ratelimiter.RateLimiting
import io.github.flaxoos.ktor.server.plugins.ratelimiter.implementations.TokenBucket
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.seconds

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

fun Application.configureDependencyInjection() {
    val appConfig = AppConfig.load()

    install(Koin) {
        slf4jLogger()
        modules(
            configModule(appConfig),
            repositoryModule(appConfig),
            useCaseModule(),
            serviceModule(),
            controllerModule()
        )
    }
}


/*fun Application.configureMonitoring() {
    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .build()
    }
}*/

fun Application.configureRateLimit() {
    install(RateLimiting) {
        rateLimiter {
            type = TokenBucket::class
            capacity = 100
            rate = 60.seconds
        }
    }
}

fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Webhook-URL")


        val environment = Environment.current()
        if (environment.isLocal) {
            anyHost()
        } else {
            allowHost("yourdomain.com")
            allowHost("app.yourdomain.com")
        }

        allowCredentials = true
    }
}

fun configModule(config: AppConfig) = module {
    single { config }
    single { config.database }
    single { config.redis }
    single { config.storage }
    single { config.auth }
    single { config.screenshot }
}

fun repositoryModule(config: AppConfig) = module {
    single<UserRepository> {
        if (config.database.useInMemory) {
            InMemoryUserRepository()
        } else {
            PostgreSQLUserRepository(get())
        }
    }

    single<ApiKeyRepository> {
        if (config.database.useInMemory) {
            InMemoryApiKeyRepository()
        } else {
            PostgreSQLApiKeyRepository(get())
        }
    }

    single<ScreenshotRepository> {
        if (config.database.useInMemory) {
            InMemoryScreenshotRepository()
        } else {
            PostgreSQLScreenshotRepository(get())
        }
    }

    single<QueueRepository> {
        if (config.redis.useInMemory) {
            InMemoryQueueAdapter()
        } else {
            RedisQueueAdapter(get())
        }
    }

    single<ActivityRepository> {
        if (config.database.useInMemory) {
            InMemoryActivityRepository()
        } else {
            PostgreSQLActivityRepository(get())
        }
    }

    single<StorageOutputPort> {
        StorageFactory.create(config.storage)
    }

    single<Database> {
        if (!config.database.useInMemory) {
            Database.connect(
                url = config.database.url!!,
                driver = "org.postgresql.Driver",
                user = config.database.username!!,
                password = config.database.password!!
            )
        } else {
            throw IllegalStateException("Database not needed in in-memory mode")
        }
    }

    // Redis connection (only if not in-memory)
    single<StatefulRedisConnection<String, String>> {
        if (!config.redis.useInMemory) {
            val client = RedisClient.create(config.redis.url!!)
            client.connect()
        } else {
            throw IllegalStateException("Redis not needed in in-memory mode")
        }
    }
}

fun useCaseModule() = module {
    // Screenshot use cases - simplified implementations
    single { TakeScreenshotUseCase() }
    single { GetScreenshotStatusUseCase() }
    single { ListScreenshotsUseCase() }

    // Auth use cases - simplified implementations
    single { ValidateApiKeyUseCase() }
    single { CreateApiKeyUseCase() }
    single { AuthenticateUserUseCase() }
    single { RegisterUserUseCase() }
    single { GetUserProfileUseCase() }

    // Billing use cases - simplified implementations
    single { CheckCreditsUseCase() }
    single { DeductCreditsUseCase() }

    // Admin use cases - simplified implementations
    single { ListUsersUseCase() }
    single { GetUserDetailsUseCase() }
    single { UpdateUserStatusUseCase() }
    single { GetUserActivityUseCase() }
    single { GetSystemStatsUseCase() }
    single { GetScreenshotStatsUseCase() }
}

fun serviceModule() = module {
    single<ScreenshotService> { ScreenshotServiceImpl(get(), get()) }
    single { BrowserPoolManager(get<ScreenshotConfig>()) }
    single { NotificationService() }
    single { MetricsService() }
    single {
        WorkerManager(
            queueRepository = get(),
            screenshotRepository = get(),
            userRepository = get(),
            screenshotService = get(),
            deductCreditsUseCase = get(),
            notificationService = get(),
            metricsService = get(),
            config = get()
        )
    }
}

fun controllerModule() = module {
    single { ScreenshotController() }
    single { AuthController() }
    single { AdminController() }
    single { HealthController() }
}
