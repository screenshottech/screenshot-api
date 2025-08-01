package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.core.domain.repositories.*
import dev.screenshotapi.core.domain.services.EmailProvider
import dev.screenshotapi.core.domain.services.RateLimitingService
import dev.screenshotapi.core.domain.services.RetryPolicy
import dev.screenshotapi.core.domain.services.ScreenshotService
import dev.screenshotapi.core.domain.services.OcrService
import dev.screenshotapi.core.ports.output.*
import dev.screenshotapi.core.usecases.admin.*
import dev.screenshotapi.core.usecases.auth.*
import dev.screenshotapi.core.usecases.billing.*
import dev.screenshotapi.core.usecases.logging.GetUsageLogsUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.usecases.screenshot.*
import dev.screenshotapi.core.usecases.stats.AggregateStatsUseCase
import dev.screenshotapi.core.usecases.stats.UpdateDailyStatsUseCase
import dev.screenshotapi.core.usecases.webhook.*
import dev.screenshotapi.core.usecases.email.*
import dev.screenshotapi.core.usecases.ocr.*
import dev.screenshotapi.core.usecases.analysis.*
import dev.screenshotapi.infrastructure.adapters.input.rest.*
import dev.screenshotapi.infrastructure.adapters.output.TokenGenerationAdapter
import dev.screenshotapi.infrastructure.adapters.output.cache.CacheFactory
import dev.screenshotapi.infrastructure.adapters.output.payment.StripePaymentGatewayAdapter
import dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory.*
import dev.screenshotapi.infrastructure.adapters.output.email.MockEmailProvider
import dev.screenshotapi.infrastructure.adapters.output.email.AwsSesEmailProvider
import dev.screenshotapi.infrastructure.adapters.output.email.GmailEmailProvider
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.*
import dev.screenshotapi.infrastructure.adapters.output.ocr.PaddleOcrService
import dev.screenshotapi.infrastructure.adapters.output.ocr.AwsBedrockOcrService
import dev.screenshotapi.core.services.AnalysisResultFormatter
import dev.screenshotapi.infrastructure.adapters.output.queue.inmemory.InMemoryQueueAdapter
import dev.screenshotapi.infrastructure.adapters.output.queue.inmemory.InMemoryAnalysisJobQueueAdapter
import dev.screenshotapi.infrastructure.adapters.output.queue.redis.RedisQueueAdapter
import dev.screenshotapi.infrastructure.adapters.output.queue.redis.RedisAnalysisJobQueueAdapter
import dev.screenshotapi.infrastructure.adapters.output.security.BCryptHashingAdapter
import dev.screenshotapi.infrastructure.adapters.output.security.HmacAdapter
import dev.screenshotapi.infrastructure.adapters.output.security.UrlSecurityAdapter
import dev.screenshotapi.infrastructure.adapters.output.storage.StorageFactory
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
import dev.screenshotapi.infrastructure.auth.JwtAuthProvider
import dev.screenshotapi.infrastructure.config.AppConfig
import dev.screenshotapi.infrastructure.config.AuthConfig
import dev.screenshotapi.infrastructure.config.ScreenshotConfig
import dev.screenshotapi.infrastructure.config.StripeConfig
import dev.screenshotapi.infrastructure.config.WebhookConfig
import dev.screenshotapi.infrastructure.config.OcrConfig
import dev.screenshotapi.infrastructure.config.BedrockConfig
import dev.screenshotapi.infrastructure.config.BedrockFeatureFlags
import dev.screenshotapi.infrastructure.config.loadBedrockConfig
import dev.screenshotapi.infrastructure.config.loadBedrockFeatureFlags
import dev.screenshotapi.infrastructure.config.AnalysisConfig
import dev.screenshotapi.infrastructure.config.AnalysisTypeConfig
import dev.screenshotapi.infrastructure.config.loadAnalysisConfig
import dev.screenshotapi.infrastructure.config.loadAnalysisTypeConfig
import dev.screenshotapi.infrastructure.services.*
import dev.screenshotapi.infrastructure.config.EmailConfig
import dev.screenshotapi.workers.JobRetryScheduler
import org.slf4j.LoggerFactory
import dev.screenshotapi.workers.WebhookRetryWorker
import dev.screenshotapi.workers.WorkerManager
import dev.screenshotapi.workers.AnalysisWorkerManager
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
    single { config.webhook }
    single { config.ocr }
    single { config.email }
    single { config.analysis }
    single<BedrockConfig> { loadBedrockConfig() }
    single<BedrockFeatureFlags> { loadBedrockFeatureFlags() }
    single<AnalysisConfig> { loadAnalysisConfig() }
    single<AnalysisTypeConfig> { loadAnalysisTypeConfig() }
    single { AnalysisResultFormatter() }
    single { AnalysisRetryService(get<AnalysisConfig>()) }
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
    single<DailyStatsRepository> { createDailyStatsRepository(config, getOrNull()) }
    single<WebhookConfigurationRepository> { createWebhookConfigurationRepository(config, getOrNull()) }
    single<WebhookDeliveryRepository> { createWebhookDeliveryRepository(config, getOrNull()) }
    single<EmailLogRepository> { createEmailLogRepository(config, getOrNull()) }
    single<OcrResultRepository> { createOcrResultRepository(config, getOrNull()) }
    single<AnalysisJobRepository> { createAnalysisJobRepository(config, getOrNull()) }
    single<AnalysisJobQueueRepository> { createAnalysisJobQueueRepository(config, getOrNull<StatefulRedisConnection<String, String>>()) }
    single<StorageOutputPort> { StorageFactory.create(config.storage) }
    single<HashingPort> { BCryptHashingAdapter() }
    single<HmacPort> { HmacAdapter(get()) }
    single<UrlSecurityPort> { UrlSecurityAdapter() }
    single<PaymentGatewayPort> { StripePaymentGatewayAdapter(get<StripeConfig>(), get<PlanRepository>()) }

    single<TokenGenerationPort> { TokenGenerationAdapter(get<ScreenshotTokenService>()) }
    if (!config.database.useInMemory) {
        single<Database> { createDatabase(config) }
    }
    if (!config.redis.useInMemory) {
        single<StatefulRedisConnection<String, String>> { createRedisConnection(config) }
    }
    single {
        StatsAggregationScheduler(
            aggregateStatsUseCase = get<AggregateStatsUseCase>(),
            dailyStatsRepository = get<DailyStatsRepository>()
        )
    }

    single {
        WebhookRetryWorker(
            sendWebhookUseCase = get<SendWebhookUseCase>()
        )
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
    if (config.database.useInMemory) InMemoryUsageLogRepository() else PostgreSQLUsageLogRepository(database!!)

private fun createDailyStatsRepository(config: AppConfig, database: Database?): DailyStatsRepository =
    if (config.database.useInMemory) InMemoryDailyStatsRepository() else PostgreSQLDailyStatsRepository(database!!)

private fun createWebhookConfigurationRepository(config: AppConfig, database: Database?): WebhookConfigurationRepository =
    if (config.database.useInMemory) InMemoryWebhookConfigurationRepository() else PostgreSQLWebhookConfigurationRepository(database!!)

private fun createWebhookDeliveryRepository(config: AppConfig, database: Database?): WebhookDeliveryRepository =
    if (config.database.useInMemory) InMemoryWebhookDeliveryRepository() else PostgreSQLWebhookDeliveryRepository(database!!)

private fun createEmailLogRepository(config: AppConfig, database: Database?): EmailLogRepository =
    if (config.database.useInMemory) InMemoryEmailLogRepository() else PostgreSQLEmailLogRepository(database!!)

private fun createEmailProvider(config: EmailConfig): EmailProvider {
    return when (config.provider.lowercase()) {
        "aws_ses" -> AwsSesEmailProvider(config)
        "gmail" -> GmailEmailProvider(config)
        "mock" -> MockEmailProvider()
        else -> MockEmailProvider()
    }
}

private fun createOcrResultRepository(config: AppConfig, database: Database?): OcrResultRepository =
    if (config.database.useInMemory) InMemoryOcrResultRepository() else PostgreSQLOcrResultRepository(database!!)

private fun createAnalysisJobRepository(config: AppConfig, database: Database?): AnalysisJobRepository =
    if (config.database.useInMemory) InMemoryAnalysisJobRepository() else PostgreSQLAnalysisJobRepository(database!!)

/**
 * Create OCR service based on configuration and feature flags
 * Primary service: AWS Bedrock with Claude 3 Haiku
 * Note: PaddleOCR classes preserved but not used by default due to reliability issues
 */
private fun createOcrService(
    bedrockConfig: BedrockConfig,
    featureFlags: BedrockFeatureFlags,
    ocrConfig: OcrConfig
): OcrService {
    val logger = LoggerFactory.getLogger("DependencyInjection")
    logger.info("Creating Bedrock-only OCR service (PaddleOCR fallback disabled for reliability)")
    logger.info("Bedrock enabled: ${bedrockConfig.enabled}, OCR enabled: ${featureFlags.enableBedrockOcr}")
    
    // Create Bedrock service as primary and only OCR engine
    return AwsBedrockOcrService(
        config = bedrockConfig,
        featureFlags = featureFlags
    )
    
    // Note: PaddleOCR service creation preserved for future microservice use:
    /*
    val paddleOcrService = PaddleOcrService(
        pythonPath = ocrConfig.paddleOcr.pythonPath,
        paddleOcrPath = ocrConfig.paddleOcr.paddleOcrPath,
        workingDirectory = ocrConfig.paddleOcr.workingDirectory,
        timeoutSeconds = ocrConfig.paddleOcr.timeoutSeconds,
        maxConcurrentJobs = ocrConfig.paddleOcr.maxConcurrentJobs,
        memoryOptimization = ocrConfig.paddleOcr.memoryOptimization
    )
    */
}

private fun createDatabase(config: AppConfig): Database =
    if (!config.database.useInMemory) {
        val hikariConfig = com.zaxxer.hikari.HikariConfig().apply {
            jdbcUrl = config.database.url!!
            driverClassName = config.database.driver
            username = config.database.username!!
            password = config.database.password!!
            maximumPoolSize = config.database.maxPoolSize
            minimumIdle = 5
            idleTimeout = 600000 // 10 minutes
            connectionTimeout = 30000 // 30 seconds
            maxLifetime = 1800000 // 30 minutes
            isAutoCommit = false
        }
        val dataSource = com.zaxxer.hikari.HikariDataSource(hikariConfig)
        Database.connect(dataSource)
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
    single { TakeScreenshotUseCase(get(), get(), get<TokenGenerationPort>()) }
    single { GetScreenshotStatusUseCase(get()) }
    single { BulkGetScreenshotStatusUseCase(get()) }
    single { ListScreenshotsUseCase(get()) }
    single { ValidateScreenshotTokenUseCase(get(), get<TokenGenerationPort>()) }

    // Retry use cases
    single { ManualRetryScreenshotUseCase(get(), get(), get(), get()) }
    single { ProcessStuckJobsUseCase(get(), get(), get(), get()) }
    single { ProcessFailedRetryableJobsUseCase(get(), get(), get(), get()) }

    // Auth use cases - mixed injection patterns
    single { ValidateApiKeyUseCase(get(), get(), get<LogUsageUseCase>(), get<HashingPort>()) }
    single { ValidateApiKeyOwnershipUseCase(get<ApiKeyRepository>()) }
    single { CreateApiKeyUseCase(get<ApiKeyRepository>(), get<UserRepository>(), get<HashingPort>()) }
    single { UpdateApiKeyUseCase(get<ApiKeyRepository>()) }
    single { DeleteApiKeyUseCase(get<ApiKeyRepository>()) }
    single { ListApiKeysUseCase(get<ApiKeyRepository>(), get<UserRepository>()) }
    single { AuthenticateUserUseCase(get<UserRepository>()) }
    single { RegisterUserUseCase(get<UserRepository>(), get<PlanRepository>(), getOrNull<SendWelcomeEmailUseCase>()) }
    single { GetUserProfileUseCase(get<UserRepository>()) }
    single { GetUserUsageUseCase(get<UserRepository>(), get<ScreenshotRepository>(), get<PlanRepository>()) }
    single { GetUserUsageTimelineUseCase(get<UsageRepository>(), get<UserRepository>()) }
    single { UpdateUserProfileUseCase(get<UserRepository>()) }

    // Stats use cases
    single { UpdateDailyStatsUseCase(get<DailyStatsRepository>()) }
    single { AggregateStatsUseCase(get<DailyStatsRepository>()) }

    // Logging use cases
    single { LogUsageUseCase(get<UsageLogRepository>(), get<UpdateDailyStatsUseCase>()) }
    single { GetUsageLogsUseCase(get<UsageLogRepository>()) }

    // Billing use cases - mixed injection patterns
    single { CheckCreditsUseCase(get()) }
    single { DeductCreditsUseCase(get<UserRepository>(), get<LogUsageUseCase>(), getOrNull<SendCreditAlertUseCase>()) }
    single { AddCreditsUseCase(get<UserRepository>()) }
    single { HandlePaymentUseCase(get<UserRepository>(), get<AddCreditsUseCase>()) }
    single { GetAvailablePlansUseCase(get<PlanRepository>()) }
    single { GetUserSubscriptionUseCase(get<UserRepository>(), get<SubscriptionRepository>(), get<PlanRepository>()) }
    single { CreateCheckoutSessionUseCase(get<UserRepository>(), get<PlanRepository>(), get<PaymentGatewayPort>()) }
    single { CreateBillingPortalSessionUseCase(get<UserRepository>(), get<SubscriptionRepository>(), get<PaymentGatewayPort>()) }

    // Webhook use cases
    single { CreateWebhookUseCase(get<WebhookConfigurationRepository>(), get<UserRepository>()) }
    single { UpdateWebhookUseCase(get<WebhookConfigurationRepository>()) }
    single { DeleteWebhookUseCase(get<WebhookConfigurationRepository>()) }
    single { ListWebhooksUseCase(get<WebhookConfigurationRepository>()) }
    single { RegenerateWebhookSecretUseCase(get<WebhookConfigurationRepository>()) }
    single { SendWebhookUseCase(get<WebhookConfigurationRepository>(), get<WebhookDeliveryRepository>(), get<HttpClient>(), get<AppConfig>().webhook) }
    single { GetWebhookDeliveriesUseCase(get<WebhookConfigurationRepository>(), get<WebhookDeliveryRepository>()) }
    single { CleanupWebhookDeliveriesUseCase(get<WebhookDeliveryRepository>()) }

    // Email use cases
    single { SendWelcomeEmailUseCase(get<UserRepository>(), get<EmailLogRepository>(), get<EmailService>(), get<LogUsageUseCase>()) }
    single { SendCreditAlertUseCase(get<UserRepository>(), get<EmailLogRepository>(), get<EmailService>(), get<LogUsageUseCase>()) }
    single { GetEmailLogsByUserUseCase(get<EmailLogRepository>(), get<UserRepository>()) }

    // OCR use cases
    single { CreateOcrJobUseCase(get<OcrResultRepository>(), get<ScreenshotRepository>(), get<QueueRepository>()) }
    single { GetOcrResultUseCase(get<OcrResultRepository>()) }
    single { ListOcrResultsUseCase(get<OcrResultRepository>()) }
    single { GetOcrAnalyticsUseCase(get<OcrResultRepository>()) }
    single { ExtractTextUseCase(get<OcrService>(), get<UserRepository>(), get<DeductCreditsUseCase>(), get<LogUsageUseCase>()) }
    single { ExtractPriceDataUseCase(get<ExtractTextUseCase>(), get<LogUsageUseCase>()) }
    
    // Analysis use cases (NEW - Separate Flow)
    single { CreateAnalysisUseCase(get<AnalysisJobRepository>(), get<AnalysisJobQueueRepository>(), get<ScreenshotRepository>(), get<ValidateApiKeyOwnershipUseCase>(), get<CheckCreditsUseCase>(), get<DeductCreditsUseCase>(), get<LogUsageUseCase>()) }
    single { GetAnalysisStatusUseCase(get<AnalysisJobRepository>()) }
    single { GetScreenshotAnalysesUseCase(get<AnalysisJobRepository>(), get<ScreenshotRepository>()) }
    single { ListAnalysesUseCase(get<AnalysisJobRepository>()) }
    single { ProcessAnalysisUseCase(get<AnalysisJobRepository>(), get<OcrResultRepository>(), get<OcrService>(), get<HttpClient>()) }
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
    // Screenshot token service - must be before ScreenshotService
    single { ScreenshotTokenService(get<HmacPort>(), get<AuthConfig>()) }
    single<ScreenshotService> { ScreenshotServiceImpl(get(), get(), get<UrlSecurityPort>(), getOrNull<ScreenshotTokenService>()) }
    single { BrowserPoolManager(get<ScreenshotConfig>()) }
    single<EmailProvider> { createEmailProvider(get<EmailConfig>()) }
    single { EmailService(get<EmailProvider>(), get<EmailConfig>()) }
    single { NotificationService(get<SendWebhookUseCase>()) }
    single { MetricsService() }
    single { AnalysisMetricsCollector(get()) }
    single<RetryPolicy> { DefaultRetryPolicyImpl() }
    single { JobRetryScheduler(get(), get(), get()) }
    single<UsageTrackingPort> {
        UsageTrackingServiceImpl(
            userRepository = get(),
            usageRepository = get(),
            shortTermCache = CacheFactory.createRateLimitCache(get(), getOrNull()),
            monthlyCache = CacheFactory.createUsageCache(get(), getOrNull())
        )
    }
    single<RateLimitingService> { RateLimitingServiceImpl(get(), get(), get()) }
    single<OcrService> { createOcrService(get<BedrockConfig>(), get<BedrockFeatureFlags>(), get<OcrConfig>()) }
    single { ScreenshotOcrWorkflowService(
        screenshotRepository = get(),
        ocrResultRepository = get(),
        extractTextUseCase = get(),
        extractPriceDataUseCase = get(),
        storagePort = get(),
        httpClient = get()
    ) }
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
            authConfig = get(),
            sendWelcomeEmailUseCase = getOrNull<SendWelcomeEmailUseCase>()
        )
    }
    single {
        JwtAuthProvider(
            userRepository = get(),
            jwtSecret = get<AuthConfig>().jwtSecret,
            jwtIssuer = get<AuthConfig>().jwtIssuer,
            jwtAudience = get<AuthConfig>().jwtAudience,
            jwtExpirationHours = get<AuthConfig>().jwtExpirationHours
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
            retryPolicy = get(),
            jobRetryScheduler = get(),
            config = get(),
            emailService = getOrNull<EmailService>(),
            ocrWorkflowService = getOrNull<ScreenshotOcrWorkflowService>(),
            ocrResultRepository = get(),
            extractTextUseCase = get<ExtractTextUseCase>()
        )
    }
    single {
        AnalysisWorkerManager(
            analysisJobRepository = get(),
            analysisJobQueueRepository = get(),
            userRepository = get(),
            processAnalysisUseCase = get(),
            logUsageUseCase = get(),
            notificationService = get(),
            metricsService = get(),
            metricsCollector = get(),
            config = get(),
            analysisConfig = get()
        )
    }

}

private fun createAnalysisJobQueueRepository(config: AppConfig, redisConnection: StatefulRedisConnection<String, String>?): AnalysisJobQueueRepository =
    if (config.redis.useInMemory) {
        InMemoryAnalysisJobQueueAdapter()
    } else {
        RedisAnalysisJobQueueAdapter(redisConnection!!)
    }

fun controllerModule() = module {
    single { ScreenshotController() }
    single { AnalysisController() }
    single { AuthController() }
    single { BillingController() }
    single { AdminController() }
    single { HealthController() }
    single { WebhookController() }
}
