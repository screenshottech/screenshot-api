package dev.screenshotapi.core.ports.output

import kotlin.time.Duration

/**
 * Port for cache operations in hexagonal architecture
 */
interface CachePort {
    /**
     * Get value from cache
     */
    suspend fun <T> get(key: String, type: Class<T>): T?
    
    /**
     * Put value in cache with TTL
     */
    suspend fun <T> put(key: String, value: T, ttl: Duration)
    
    /**
     * Put value in cache without TTL (permanent until eviction)
     */
    suspend fun <T> put(key: String, value: T)
    
    /**
     * Remove value from cache
     */
    suspend fun remove(key: String)
    
    /**
     * Check if key exists in cache
     */
    suspend fun exists(key: String): Boolean
    
    /**
     * Clear all cache entries
     */
    suspend fun clear()
    
    /**
     * Increment counter atomically
     */
    suspend fun increment(key: String, delta: Long = 1): Long
    
    /**
     * Set expiration time for existing key
     */
    suspend fun expire(key: String, ttl: Duration): Boolean
}

/**
 * Extension functions for better type safety
 */
suspend inline fun <reified T> CachePort.get(key: String): T? = get(key, T::class.java)
suspend inline fun <reified T> CachePort.put(key: String, value: T, ttl: Duration) = put(key, value, ttl)
suspend inline fun <reified T> CachePort.put(key: String, value: T) = put(key, value)