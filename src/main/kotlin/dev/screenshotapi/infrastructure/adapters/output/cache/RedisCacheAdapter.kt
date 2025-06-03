package dev.screenshotapi.infrastructure.adapters.output.cache

import dev.screenshotapi.core.domain.entities.DailyUsage
import dev.screenshotapi.core.domain.entities.ShortTermUsage
import dev.screenshotapi.core.domain.entities.UserUsage
import dev.screenshotapi.core.ports.output.CachePort
import dev.screenshotapi.infrastructure.adapters.output.cache.dto.DailyUsageCacheDto
import dev.screenshotapi.infrastructure.adapters.output.cache.dto.ShortTermUsageCacheDto
import dev.screenshotapi.infrastructure.adapters.output.cache.dto.UserUsageCacheDto
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration

/**
 * Redis cache implementation for distributed caching
 */
class RedisCacheAdapter(
    private val connection: StatefulRedisConnection<String, String>,
    private val keyPrefix: String = "screenshot_api:"
) : CachePort {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val commands = connection.async()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun <T> get(key: String, type: Class<T>): T? {
        return try {
            val value = commands.get(prefixKey(key)).await() ?: return null
            deserializeValue(value, type)
        } catch (e: Exception) {
            logger.error("Error getting value from cache for key: $key", e)
            null
        }
    }

    override suspend fun <T> put(key: String, value: T, ttl: Duration) {
        try {
            val serializedValue = serializeValue(value)
            if (ttl == Duration.INFINITE) {
                commands.set(prefixKey(key), serializedValue).await()
            } else {
                commands.setex(prefixKey(key), ttl.inWholeSeconds, serializedValue).await()
            }
        } catch (e: Exception) {
            logger.error("Error putting value in cache for key: $key", e)
        }
    }

    override suspend fun <T> put(key: String, value: T) {
        put(key, value, Duration.INFINITE)
    }

    override suspend fun remove(key: String) {
        try {
            commands.del(prefixKey(key)).await()
        } catch (e: Exception) {
            logger.error("Error removing key from cache: $key", e)
        }
    }

    override suspend fun exists(key: String): Boolean {
        return try {
            commands.exists(prefixKey(key)).await() > 0
        } catch (e: Exception) {
            logger.error("Error checking key existence in cache: $key", e)
            false
        }
    }

    override suspend fun clear() {
        try {
            val keys = commands.keys("$keyPrefix*").await()
            if (keys.isNotEmpty()) {
                commands.del(*keys.toTypedArray()).await()
            }
        } catch (e: Exception) {
            logger.error("Error clearing cache", e)
        }
    }

    override suspend fun increment(key: String, delta: Long): Long {
        return try {
            commands.incrby(prefixKey(key), delta).await()
        } catch (e: Exception) {
            logger.error("Error incrementing value in cache for key: $key", e)
            0L
        }
    }

    override suspend fun expire(key: String, ttl: Duration): Boolean {
        return try {
            commands.expire(prefixKey(key), ttl.inWholeSeconds).await()
        } catch (e: Exception) {
            logger.error("Error setting expiration for key: $key", e)
            false
        }
    }

    private fun prefixKey(key: String): String = "$keyPrefix$key"

    private fun <T> serializeValue(value: T): String {
        return when (value) {
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            is UserUsage -> {
                val dto = UserUsageCacheDto.fromDomain(value)
                json.encodeToString(UserUsageCacheDto.serializer(), dto)
            }
            is ShortTermUsage -> {
                val dto = ShortTermUsageCacheDto.fromDomain(value)
                json.encodeToString(ShortTermUsageCacheDto.serializer(), dto)
            }
            is DailyUsage -> {
                val dto = DailyUsageCacheDto.fromDomain(value)
                json.encodeToString(DailyUsageCacheDto.serializer(), dto)
            }
            null -> ""
            else -> {
                try {
                    // Fallback to toString for non-serializable objects
                    logger.debug("Serializing non-serializable object to string: ${value::class.java.name}")
                    value.toString()
                } catch (e: Exception) {
                    logger.warn("Failed to serialize object, using empty string: ${value::class.java.name}", e)
                    ""
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> deserializeValue(value: String, type: Class<T>): T? {
        return try {
            when (type) {
                String::class.java -> value as T
                Int::class.java -> value.toInt() as T
                Long::class.java -> value.toLong() as T
                Double::class.java -> value.toDouble() as T
                Boolean::class.java -> value.toBoolean() as T
                UserUsage::class.java -> {
                    val dto = json.decodeFromString(UserUsageCacheDto.serializer(), value)
                    dto.toDomain() as T
                }
                ShortTermUsage::class.java -> {
                    val dto = json.decodeFromString(ShortTermUsageCacheDto.serializer(), value)
                    dto.toDomain() as T
                }
                DailyUsage::class.java -> {
                    val dto = json.decodeFromString(DailyUsageCacheDto.serializer(), value)
                    dto.toDomain() as T
                }
                else -> {
                    logger.warn("Cannot deserialize unknown type: ${type.name}, returning null")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error deserializing value for type: ${type.name}", e)
            null
        }
    }
}
