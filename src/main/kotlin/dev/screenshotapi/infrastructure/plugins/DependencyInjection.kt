package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.core.domain.repositories.*
import dev.screenshotapi.core.domain.services.RateLimitingService
import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.core.ports.output.HashingPort
import dev.screenshotapi.core.ports.output.PaymentGatewayPort
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.core.ports.output.UsageTrackingPort
import dev.screenshotapi.core.usecases.admin.*
import dev.screenshotapi.core.usecases.auth.*
import dev.screenshotapi.core.usecases.billing.*
import dev.screenshotapi.core.usecases.logging.GetUsageLogsUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.usecases.screenshot.BulkGetScreenshotStatusUseCase
import dev.screenshotapi.core.usecases.screenshot.GetScreenshotStatusUseCase
import dev.screenshotapi.core.usecases.screenshot.ListScreenshotsUseCase
import dev.screenshotapi.core.usecases.screenshot.TakeScreenshotUseCase
import dev.screenshotapi.infrastructure.adapters.input.rest.*
import dev.screenshotapi.infrastructure.adapters.output.cache.CacheFactory
import dev.screenshotapi.infrastructure.adapters.output.payment.StripePaymentGatewayAdapter
import dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory.*
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.*
import dev.screenshotapi.infrastructure.adapters.output.queue.inmemory.InMemoryQueueAdapter
import dev.screenshotapi.infrastructure.adapters.output.queue.redis.RedisQueueAdapter
import dev.screenshotapi.infrastructure.adapters.output.security.BCryptHashingAdapter
import dev.screenshotapi.infrastructure.adapters.output.storage.StorageFactory
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
import dev.screenshotapi.infrastructure.auth.JwtAuthProvider
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import dev.screenshotapi.infrastructure.config.StripeConfig
import dev.screenshotapi.infrastructure.services.*
import dev.screenshotapi.workers.WorkerManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.json.Json
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
    single { config.billing }
    single { config.billing.stripe }
}

fun repositoryModule(config: AppConfig) = module {
    single<UserRepository> { createUserRepository(config, getOrNull()) }
    single<ApiKeyRepository> { createApiKeyRepository(config, getOrNull()) }
    single<ScreenshotRepository> { createScreenshotRepository(config, getOrNull()) }
    single<QueueRepository> { createQueueRepository(config, getOrNull<StatefulRedisConnection<String, String>>()) }
    single<ActivityRepository> { createActivityRepository(config, getOrNull()) }
    single<PlanRepository> { createPlanRepository(config, getOrNull()) }
    single<SubscriptionRepository> { createSubscriptionRepository(config, getOrNull()) }
    single<UsageRepository> { createUsageRepository(config, getOrNull()) }
    single<UsageLogRepository> { createUsageLogRepository(config, getOrNull()) }
    single<StorageOutputPort> { StorageFactory.create(config.storage) }
    single<HashingPort> { BCryptHashingAdapter() }
    single<PaymentGatewayPort> { StripePaymentGatewayAdapter(get<StripeConfig>(), get<PlanRepository>()) }
    if (!config.database.useInMemory) {
        single<Database> { createDatabase(config) }
    }
    if (!config.redis.useInMemory) {
        single<StatefulRedisConnection<String, String>> { createRedisConnection(config) }
    }
}

private fun createUserRepository(config: AppConfig, database: Database?): UserRepository =
    if (config.database.useInMemory) InMemoryUserRepository() else PostgreSQLUserRepository(database!!)

private fun createApiKeyRepository(config: AppConfig, database: Database?): ApiKeyRepository =
    if (config.database.useInMemory) InMemoryApiKeyRepository() else PostgreSQLApiKeyRepository(database!!)

private fun createScreenshotRepository(config: AppConfig, database: Database?): ScreenshotRepository =
    if (config.database.useInMemory) InMemoryScreenshotRepository() else PostgreSQLScreenshotRepository(database!!)

private fun createQueueRepository(config: AppConfig, redisConnection: StatefulRedisConnection<String, String>?): QueueRepository =
    if (config.redis.useInMemory) InMemoryQueueAdapter() else RedisQueueAdapter(redisConnection!!)

private fun createActivityRepository(config: AppConfig, database: Database?): ActivityRepository =
    if (config.database.useInMemory) InMemoryActivityRepository() else PostgreSQLActivityRepository(database!!)

private fun createPlanRepository(config: AppConfig, database: Database?): PlanRepository =
    if (config.database.useInMemory) InMemoryPlanRepository() else PostgreSQLPlanRepository(database!!)

private fun createSubscriptionRepository(config: AppConfig, database: Database?): SubscriptionRepository =
    if (config.database.useInMemory) InMemorySubscriptionRepository() else PostgreSQLSubscriptionRepository(database!!)

private fun createUsageRepository(config: AppConfig, database: Database?): UsageRepository =
    if (config.database.useInMemory) InMemoryUsageRepository() else PostgreSQLUsageRepository(database!!)

private fun createUsageLogRepository(config: AppConfig, database: Database?): UsageLogRepository =
    if (config.database.useInMemory) InMemoryUsageLogRepository() else PostgreSQLUsageLogRepository()

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
    // Screenshot use cases - constructor injection
    single { TakeScreenshotUseCase(get(), get()) }
    single { GetScreenshotStatusUseCase(get()) }
    single { BulkGetScreenshotStatusUseCase(get()) }
    single { ListScreenshotsUseCase(get()) }

    // Auth use cases - mixed injection patterns
    single { ValidateApiKeyUseCase(get(), get(), get<LogUsageUseCase>()) }
    single { ValidateApiKeyOwnershipUseCase(get<ApiKeyRepository>()) }
    single { CreateApiKeyUseCase(get<ApiKeyRepository>(), get<UserRepository>(), get<HashingPort>()) }
    single { UpdateApiKeyUseCase(get<ApiKeyRepository>()) }
    single { DeleteApiKeyUseCase(get<ApiKeyRepository>()) }
    single { ListApiKeysUseCase(get<ApiKeyRepository>(), get<UserRepository>()) }
    single { AuthenticateUserUseCase(get<UserRepository>()) }
    single { RegisterUserUseCase(get<UserRepository>(), get<PlanRepository>()) }
    single { GetUserProfileUseCase(get<UserRepository>()) }
    single { GetUserUsageUseCase(get<UserRepository>(), get<ScreenshotRepository>()) }
    single { GetUserUsageTimelineUseCase(get<UsageRepository>(), get<UserRepository>()) }
    single { UpdateUserProfileUseCase(get<UserRepository>()) }

    // Logging use cases
    single { LogUsageUseCase(get<UsageLogRepository>()) }
    single { GetUsageLogsUseCase(get<UsageLogRepository>()) }

    // Billing use cases - mixed injection patterns
    single { CheckCreditsUseCase() }
    single { DeductCreditsUseCase(get<UserRepository>(), get<LogUsageUseCase>()) }
    single { AddCreditsUseCase(get<UserRepository>()) }
    single { HandlePaymentUseCase(get<UserRepository>(), get<AddCreditsUseCase>()) }
    single { GetAvailablePlansUseCase(get<PlanRepository>()) }
    single { GetUserSubscriptionUseCase(get<UserRepository>(), get<SubscriptionRepository>(), get<PlanRepository>()) }
    single { CreateCheckoutSessionUseCase(get<UserRepository>(), get<PlanRepository>(), get<PaymentGatewayPort>()) }
    single { CreateBillingPortalSessionUseCase(get<UserRepository>(), get<SubscriptionRepository>(), get<PaymentGatewayPort>()) }
    single { ProvisionSubscriptionCreditsUseCase(get<SubscriptionRepository>(), get<UserRepository>(), get<PlanRepository>(), get<AddCreditsUseCase>(), get<LogUsageUseCase>()) }
    single { HandleSubscriptionWebhookUseCase(get<PaymentGatewayPort>(), get<SubscriptionRepository>(), get<UserRepository>(), get<ProvisionSubscriptionCreditsUseCase>()) }

    // Admin use cases - mixed injection patterns
    single { ListUsersUseCase(get<UserRepository>(), get<ScreenshotRepository>(), get<PlanRepository>()) }
    single { ListSubscriptionsUseCase(get<SubscriptionRepository>(), get<UserRepository>(), get<PlanRepository>(), get<GetUserUsageUseCase>()) }
    single<GetSubscriptionDetailsUseCase> { GetSubscriptionDetailsUseCase(get(), get(), get(), get()) }
    single { GetUserDetailsUseCase(get<UserRepository>(), get<PlanRepository>(), get<ScreenshotRepository>(), get<ApiKeyRepository>(), get<GetUserActivityUseCase>()) }
    single { UpdateUserStatusUseCase() }
    single { GetUserActivityUseCase(get<UsageLogRepository>(), get<UserRepository>()) }
    single { GetSystemStatsUseCase() }
    single { GetScreenshotStatsUseCase() }
    single { ProvisionSubscriptionCreditsAdminUseCase(get<SubscriptionRepository>(), get<UserRepository>(), get<PlanRepository>(), get<AddCreditsUseCase>(), get<LogUsageUseCase>()) }
    single { SynchronizeUserPlanAdminUseCase(get<UserRepository>(), get<SubscriptionRepository>(), get<PlanRepository>(), get<AddCreditsUseCase>(), get<LogUsageUseCase>()) }
}

fun serviceModule() = module {
    single<ScreenshotService> { ScreenshotServiceImpl(get(), get()) }
    single { BrowserPoolManager(get<ScreenshotConfig>()) }
    single { NotificationService() }
    single { MetricsService() }
    single<UsageTrackingPort> {
        UsageTrackingServiceImpl(
            userRepository = get(),
            usageRepository = get(),
            shortTermCache = CacheFactory.createRateLimitCache(get(), get()),
            monthlyCache = CacheFactory.createUsageCache(get(), get())
        )
    }
    single<RateLimitingService> { RateLimitingServiceImpl(get(), get(), get()) }
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }
    single {
        AuthProviderFactory(
            userRepository = get(),
            planRepository = get(),
            httpClient = get(),
            authConfig = get()
        )
    }
    single {
        JwtAuthProvider(
            userRepository = get(),
            jwtSecret = get<dev.screenshotapi.infrastructure.config.AuthConfig>().jwtSecret,
            jwtIssuer = get<dev.screenshotapi.infrastructure.config.AuthConfig>().jwtIssuer,
            jwtAudience = get<dev.screenshotapi.infrastructure.config.AuthConfig>().jwtAudience
        )
    }
    single {
        WorkerManager(
            queueRepository = get(),
            screenshotRepository = get(),
            userRepository = get(),
            screenshotService = get(),
            deductCreditsUseCase = get(),
            logUsageUseCase = get(),
            notificationService = get(),
            metricsService = get(),
            config = get()
        )
    }
}

fun controllerModule() = module {
    single { ScreenshotController() }
    single { AuthController() }
    single { BillingController() }
    single { AdminController() }
    single { HealthController() }
}
