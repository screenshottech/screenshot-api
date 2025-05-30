package dev.screenshotapi.infrastructure.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.*
import dev.screenshotapi.infrastructure.exceptions.ConfigurationException
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.get

data class DatabaseConfig(
    val useInMemory: Boolean,
    val url: String?,
    val username: String?,
    val password: String?,
    val maxPoolSize: Int,
    val driver: String
) {
    companion object {
        fun load(environment: Environment): DatabaseConfig = DatabaseConfig(
            useInMemory = System.getenv("DATABASE_USE_IN_MEMORY")?.toBoolean() ?: environment.isLocal,
            url = System.getenv("DATABASE_URL"),
            username = System.getenv("DATABASE_USERNAME"),
            password = System.getenv("DATABASE_PASSWORD"),
            maxPoolSize = System.getenv("DB_POOL_SIZE")?.toInt() ?: 20,
            driver = System.getenv("DB_DRIVER") ?: "org.postgresql.Driver"
        )
    }

    fun validate() {
        if (!useInMemory) {
            if (url.isNullOrBlank()) {
                throw ConfigurationException.missingRequired("DATABASE_URL")
            }
            if (username.isNullOrBlank()) {
                throw ConfigurationException.missingRequired("DATABASE_USERNAME")
            }
            if (password.isNullOrBlank()) {
                throw ConfigurationException.missingRequired("DATABASE_PASSWORD")
            }
        }
    }
}


fun Application.initializeDatabase() {
    val databaseConfig = get<DatabaseConfig>()

    if (!databaseConfig.useInMemory) {
        try {

            databaseConfig.validate()
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = databaseConfig.url
                username = databaseConfig.username
                password = databaseConfig.password
                driverClassName = databaseConfig.driver
                maximumPoolSize = databaseConfig.maxPoolSize
                minimumIdle = 5
                idleTimeout = 300000
                connectionTimeout = 20000
                maxLifetime = 1200000
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            }

            val dataSource = HikariDataSource(hikariConfig)
            Database.connect(dataSource)

            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    Users,
                    ApiKeys,
                    Screenshots,
                    Activities,
                    Plans,
                    UsageLogs,
                    StripeCustomers
                )
            }

            log.info("Database initialized successfully")

        } catch (e: Exception) {
            log.error("Failed to initialize database", e)
            throw ConfigurationException("DATABASE", "Failed to initialize database: ${e.message}", e)
        }
    } else {
        log.info("Using in-memory database - no initialization required")
    }
}
