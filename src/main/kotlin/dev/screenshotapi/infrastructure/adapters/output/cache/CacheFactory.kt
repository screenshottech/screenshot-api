package dev.screenshotapi.infrastructure.adapters.output.cache

import dev.screenshotapi.core.ports.output.CachePort
import dev.screenshotapi.infrastructure.config.AppConfig
import io.lettuce.core.api.StatefulRedisConnection

/**
 * Factory to create cache implementations based on configuration
 */
object CacheFactory {
    
    /**
     * Create cache adapter based on app configuration
     */
    fun create(
        config: AppConfig,
        redisConnection: StatefulRedisConnection<String, String>? = null
    ): CachePort {
        return if (config.redis.useInMemory) {
            createInMemoryCache()
        } else {
            createRedisCache(redisConnection!!)
        }
    }
    
    /**
     * Create in-memory cache with sensible defaults for development
     */
    private fun createInMemoryCache(): CachePort {
        return InMemoryCacheAdapter(
            maxSize = 10_000, // Max 10k users in cache
            defaultTtl = kotlin.time.Duration.parse("1h") // 1 hour default TTL
        )
    }
    
    /**
     * Create Redis cache for production
     */
    private fun createRedisCache(connection: StatefulRedisConnection<String, String>): CachePort {
        return RedisCacheAdapter(
            connection = connection,
            keyPrefix = "screenshot_api:cache:"
        )
    }
    
    /**
     * Create specific cache for usage tracking with appropriate TTLs
     */
    fun createUsageCache(
        config: AppConfig,
        redisConnection: StatefulRedisConnection<String, String>? = null
    ): CachePort {
        return if (config.redis.useInMemory) {
            InMemoryCacheAdapter(
                maxSize = 50_000, // More space for usage data
                defaultTtl = kotlin.time.Duration.parse("30m") // 30 min for usage data
            )
        } else {
            RedisCacheAdapter(
                connection = redisConnection!!,
                keyPrefix = "screenshot_api:usage:"
            )
        }
    }
    
    /**
     * Create specific cache for rate limiting with short TTLs
     */
    fun createRateLimitCache(
        config: AppConfig,
        redisConnection: StatefulRedisConnection<String, String>? = null
    ): CachePort {
        return if (config.redis.useInMemory) {
            InMemoryCacheAdapter(
                maxSize = 20_000, // Rate limit data for many users
                defaultTtl = kotlin.time.Duration.parse("5m") // 5 min for rate limit data
            )
        } else {
            RedisCacheAdapter(
                connection = redisConnection!!,
                keyPrefix = "screenshot_api:ratelimit:"
            )
        }
    }
}