package dev.screenshotapi.infrastructure.utils

import kotlinx.serialization.json.*

/**
 * Utility object for safe JSON serialization of heterogeneous maps and objects.
 * Centralizes the conversion of Any types to JsonElement to avoid serialization errors.
 */
object JsonSerializationUtils {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Safely converts a Map<String, Any> to a JSON string.
     * Handles primitive types, maps, lists, and falls back to toString() for unknown types.
     */
    fun mapToJsonString(map: Map<String, Any>): String {
        val jsonObject = mapToJsonObject(map)
        return jsonObject.toString()
    }
    
    /**
     * Converts a Map<String, Any> to a JsonObject.
     */
    fun mapToJsonObject(map: Map<String, Any>): JsonObject {
        return JsonObject(map.mapValues { (_, value) -> anyToJsonElement(value) })
    }
    
    /**
     * Converts any value to a JsonElement safely.
     * Handles common types and provides fallback for unknown types.
     */
    fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                // Recursively handle nested maps
                val stringKeyMap = value.entries.associate { 
                    it.key.toString() to anyToJsonElement(it.value)
                }
                JsonObject(stringKeyMap)
            }
            is List<*> -> {
                // Handle lists
                JsonArray(value.map { anyToJsonElement(it) })
            }
            is Array<*> -> {
                // Handle arrays
                JsonArray(value.map { anyToJsonElement(it) })
            }
            is JsonElement -> {
                // Already a JsonElement, return as is
                value
            }
            else -> {
                // Fallback: convert to string
                JsonPrimitive(value.toString())
            }
        }
    }
    
    /**
     * Extension function to easily convert a Map<String, Any> to JsonObject
     */
    fun Map<String, Any>.toJsonObject(): JsonObject = mapToJsonObject(this)
    
    /**
     * Extension function to easily convert a Map<String, Any> to JSON string
     */
    fun Map<String, Any>.toJsonString(): String = mapToJsonString(this)
}