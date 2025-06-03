package dev.screenshotapi.infrastructure.adapters.output.cache

import dev.screenshotapi.core.ports.output.CachePort
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration

/**
 * Redis cache implementation for distributed caching
 */
class RedisCacheAdapter(
    private val connection: StatefulRedisConnection<String, String>,
    private val keyPrefix: String = "screenshot_api:"
) : CachePort {

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
            // Log error but don't throw - fail gracefully
        }
    }

    override suspend fun <T> put(key: String, value: T) {
        put(key, value, Duration.INFINITE)
    }

    override suspend fun remove(key: String) {
        try {
            commands.del(prefixKey(key)).await()
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    override suspend fun exists(key: String): Boolean {
        return try {
            commands.exists(prefixKey(key)).await() > 0
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun clear() {
        try {
            // Get all keys with our prefix
            val keys = commands.keys("$keyPrefix*").await()
            if (keys.isNotEmpty()) {
                commands.del(*keys.toTypedArray()).await()
            }
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    override suspend fun increment(key: String, delta: Long): Long {
        return try {
            commands.incrby(prefixKey(key), delta).await()
        } catch (e: Exception) {
            0L
        }
    }

    override suspend fun expire(key: String, ttl: Duration): Boolean {
        return try {
            commands.expire(prefixKey(key), ttl.inWholeSeconds).await()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Add prefix to key to avoid conflicts
     */
    private fun prefixKey(key: String): String = "$keyPrefix$key"

    /**
     * Serialize value to JSON string
     */
    private fun serializeValue(value: Any?): String {
        return when (value) {
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            null -> ""
            else -> {
                // For complex objects, wrap into a JsonObject with type info
                try {
                    // First try to convert using toString (reliable for simple objects)
                    val stringRepresentation = value.toString()
                    // Create a JsonObject with type information and value
                    val jsonObject = mapOf(
                        "_type" to JsonPrimitive(value.javaClass.name),
                        "_value" to JsonPrimitive(stringRepresentation)
                    )
                    json.encodeToString(JsonObject.serializer(), JsonObject(jsonObject))
                } catch (e: Exception) {
                    // Fallback to simple toString if serialization fails
                    value.toString()
                }
            }
        }
    }

    /**
     * Deserialize value from JSON string
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> deserializeValue(value: String, type: Class<T>): T? {
        return try {
            when (type) {
                String::class.java -> value as T
                Int::class.java -> value.toInt() as T
                Long::class.java -> value.toLong() as T
                Double::class.java -> value.toDouble() as T
                Boolean::class.java -> value.toBoolean() as T
                else -> {
                    // For complex objects, check if it's a JsonObject with our type/value structure
                    try {
                        val jsonObject = json.decodeFromString(JsonObject.serializer(), value)
                        val typeName = (jsonObject["_type"] as? JsonPrimitive)?.content
                        val objectValue = (jsonObject["_value"] as? JsonPrimitive)?.content
                        
                        if (typeName != null && objectValue != null && Class.forName(typeName).isAssignableFrom(type)) {
                            // Try to create an instance from string representation
                            // This is simplified; in practice would need factory methods for complex objects
                            objectValue as T
                        } else {
                            // Try direct cast if types match
                            value as? T
                        }
                    } catch (e: Exception) {
                        // Fallback to direct cast
                        value as? T
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
