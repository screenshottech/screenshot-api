package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.core.domain.repositories.ActivityRepository
import dev.screenshotapi.core.domain.repositories.ApiKeyRepository
import dev.screenshotapi.core.domain.repositories.QueueRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.core.services.ScreenshotService
import dev.screenshotapi.core.usecases.admin.GetScreenshotStatsUseCase
import dev.screenshotapi.core.usecases.admin.GetSystemStatsUseCase
import dev.screenshotapi.core.usecases.admin.GetUserActivityUseCase
import dev.screenshotapi.core.usecases.admin.GetUserDetailsUseCase
import dev.screenshotapi.core.usecases.admin.ListUsersUseCase
import dev.screenshotapi.core.usecases.admin.UpdateUserStatusUseCase
import dev.screenshotapi.core.usecases.auth.AuthenticateUserUseCase
import dev.screenshotapi.core.usecases.auth.CreateApiKeyUseCase
import dev.screenshotapi.core.usecases.auth.DeleteApiKeyUseCase
import dev.screenshotapi.core.usecases.auth.GetUserProfileUseCase
import dev.screenshotapi.core.usecases.auth.GetUserUsageUseCase
import dev.screenshotapi.core.usecases.auth.ListApiKeysUseCase
import dev.screenshotapi.core.usecases.auth.RegisterUserUseCase
import dev.screenshotapi.core.usecases.auth.UpdateUserProfileUseCase
import dev.screenshotapi.core.usecases.auth.ValidateApiKeyUseCase
import dev.screenshotapi.core.usecases.billing.AddCreditsUseCase
import dev.screenshotapi.core.usecases.billing.CheckCreditsUseCase
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.core.usecases.billing.HandlePaymentUseCase
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
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import dev.screenshotapi.infrastructure.services.BrowserPoolManager
import dev.screenshotapi.infrastructure.services.MetricsService
import dev.screenshotapi.infrastructure.services.NotificationService
import dev.screenshotapi.infrastructure.services.ScreenshotServiceImpl
import dev.screenshotapi.workers.WorkerManager
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

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


fun configModule(config: AppConfig) = module {
    single { config }
    single { config.database }
    single { config.redis }
    single { config.storage }
    single { config.auth }
    single { config.screenshot }
}

fun repositoryModule(config: AppConfig) = module {
    single<UserRepository> { createUserRepository(config, get()) }
    single<ApiKeyRepository> { createApiKeyRepository(config, get()) }
    single<ScreenshotRepository> { createScreenshotRepository(config, get()) }
    single<QueueRepository> { createQueueRepository(config, getOrNull<StatefulRedisConnection<String, String>>()) }
    single<ActivityRepository> { createActivityRepository(config, get()) }
    single<StorageOutputPort> { StorageFactory.create(config.storage) }
    single<Database> { createDatabase(config) }
    if (!config.redis.useInMemory) {
        single<StatefulRedisConnection<String, String>> { createRedisConnection(config) }
    }
}

private fun createUserRepository(config: AppConfig, database: Database): UserRepository =
    if (config.database.useInMemory) InMemoryUserRepository() else PostgreSQLUserRepository(database)

private fun createApiKeyRepository(config: AppConfig, database: Database): ApiKeyRepository =
    if (config.database.useInMemory) InMemoryApiKeyRepository() else PostgreSQLApiKeyRepository(database)

private fun createScreenshotRepository(config: AppConfig, database: Database): ScreenshotRepository =
    if (config.database.useInMemory) InMemoryScreenshotRepository() else PostgreSQLScreenshotRepository(database)

private fun createQueueRepository(config: AppConfig, redisConnection: StatefulRedisConnection<String, String>?): QueueRepository =
    if (config.redis.useInMemory) InMemoryQueueAdapter() else RedisQueueAdapter(redisConnection!!)

private fun createActivityRepository(config: AppConfig, database: Database): ActivityRepository =
    if (config.database.useInMemory) InMemoryActivityRepository() else PostgreSQLActivityRepository(database)

private fun createDatabase(config: AppConfig): Database =
    if (!config.database.useInMemory) {
        Database.connect(
            url = config.database.url!!,
            driver = "org.postgresql.Driver",
            user = config.database.username!!,
            password = config.database.password!!
        )
    } else {
        error("Database not needed in in-memory mode")
    }

private fun createRedisConnection(config: AppConfig): StatefulRedisConnection<String, String> =
    if (!config.redis.useInMemory) {
        val client = RedisClient.create(config.redis.url!!)
        client.connect()
    } else {
        error("Redis not needed in in-memory mode")
    }

fun useCaseModule() = module {
    // Screenshot use cases - using KoinComponent injection
    single { TakeScreenshotUseCase() }
    single { GetScreenshotStatusUseCase() }
    single { ListScreenshotsUseCase() }

    // Auth use cases - mixed injection patterns
    single { ValidateApiKeyUseCase() }
    single { CreateApiKeyUseCase() }
    single { DeleteApiKeyUseCase(get<ApiKeyRepository>()) }
    single { ListApiKeysUseCase(get<ApiKeyRepository>(), get<UserRepository>()) }
    single { AuthenticateUserUseCase() }
    single { RegisterUserUseCase() }
    single { GetUserProfileUseCase() }
    single { GetUserUsageUseCase(get<UserRepository>(), get<ScreenshotRepository>()) }
    single { UpdateUserProfileUseCase(get<UserRepository>()) }

    // Billing use cases - mixed injection patterns
    single { CheckCreditsUseCase() }
    single { DeductCreditsUseCase() }
    single { AddCreditsUseCase(get<UserRepository>()) }
    single { HandlePaymentUseCase(get<UserRepository>(), get<AddCreditsUseCase>()) }

    // Admin use cases - mixed injection patterns
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
