package dev.screenshotapi.infrastructure.config

data class AppConfig(
    val environment: Environment,
    val server: ServerConfig,
    val database: DatabaseConfig,
    val redis: RedisConfig,
    val storage: StorageConfig,
    val auth: AuthConfig,
    val screenshot: ScreenshotConfig,
    val billing: BillingConfig
) {
    companion object {
        fun load(): AppConfig {
            val environment = Environment.current()

            return AppConfig(
                environment = environment,
                server = ServerConfig.load(),
                database = DatabaseConfig.load(environment),
                redis = RedisConfig.load(environment),
                storage = StorageConfig.load(environment),
                auth = AuthConfig.load(),
                screenshot = ScreenshotConfig.load(),
                billing = BillingConfig.load()
            )
        }
    }
}

data class ServerConfig(
    val host: String,
    val port: Int,
    val development: Boolean
) {
    companion object {
        fun load() = ServerConfig(
            host = System.getenv("HOST") ?: "0.0.0.0",
            port = System.getenv("PORT")?.toInt() ?: 8080,
            development = System.getenv("DEVELOPMENT")?.toBoolean() ?: false
        )
    }
}


data class RedisConfig(
    val useInMemory: Boolean,
    val url: String?,
    val maxConnections: Int
) {
    companion object {
        fun load(environment: Environment) = RedisConfig(
            useInMemory = environment.isLocal,
            url = if (environment.isLocal) null else System.getenv("REDIS_URL"),
            maxConnections = System.getenv("REDIS_MAX_CONNECTIONS")?.toInt() ?: 10
        )
    }
}

data class StorageConfig(
    val useLocal: Boolean,
    val localPath: String,
    val localPublicUrl: String,
    val s3Bucket: String?,
    val s3Region: String?,
    val awsAccessKey: String?,
    val awsSecretKey: String?,
    val awsEndpointUrl: String?,
    val awsPublicEndpointUrl: String?,
    val includeBucketInUrl: Boolean
) {
    companion object {
        fun load(environment: Environment) = StorageConfig(
            useLocal = System.getenv("STORAGE_USE_LOCAL")?.toBoolean() ?: environment.isLocal,
            localPath = System.getenv("STORAGE_LOCAL_PATH") ?: "./screenshots",
            localPublicUrl = System.getenv("PUBLIC_API_URL") ?: "http://localhost:8080",
            s3Bucket = System.getenv("S3_BUCKET"),
            s3Region = System.getenv("S3_REGION"),
            awsAccessKey = System.getenv("AWS_ACCESS_KEY_ID"),
            awsSecretKey = System.getenv("AWS_SECRET_ACCESS_KEY"),
            awsEndpointUrl = System.getenv("AWS_ENDPOINT_URL"),
            awsPublicEndpointUrl = System.getenv("AWS_PUBLIC_ENDPOINT_URL"),
            includeBucketInUrl = System.getenv("AWS_INCLUDE_BUCKET_IN_URL")?.toBoolean() ?: true
        )
    }
}
