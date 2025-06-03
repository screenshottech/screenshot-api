package dev.screenshotapi.infrastructure.adapters.output.cache

import dev.screenshotapi.core.ports.output.CachePort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * In-memory cache implementation with TTL and size limits for scalability
 */
class InMemoryCacheAdapter(
    private val maxSize: Int = 10_000,
    private val defaultTtl: Duration = Duration.INFINITE
) : CachePort {

    private data class CacheEntry<T>(
        val value: T,
        val expiresAt: Instant?
    )

    private val cache = ConcurrentHashMap<String, CacheEntry<Any>>()
    private val accessOrder = ConcurrentHashMap<String, Instant>()
    private val mutex = Mutex()

    override suspend fun <T> get(key: String, type: Class<T>): T? {
        val entry = cache[key] ?: return null

        // Check expiration
        if (entry.expiresAt != null && Clock.System.now() > entry.expiresAt) {
            remove(key)
            return null
        }

        // Update access time for LRU
        accessOrder[key] = Clock.System.now()

        @Suppress("UNCHECKED_CAST")
        return entry.value as? T
    }

    override suspend fun <T> put(key: String, value: T, ttl: Duration) {
        val expiresAt = if (ttl == Duration.INFINITE) null else Clock.System.now() + ttl

        mutex.withLock {
            // Evict if needed
            evictIfNeeded()

            cache[key] = CacheEntry(value as Any, expiresAt)
            accessOrder[key] = Clock.System.now()
        }
    }

    override suspend fun <T> put(key: String, value: T) {
        put(key, value, defaultTtl)
    }

    override suspend fun remove(key: String) {
        cache.remove(key)
        accessOrder.remove(key)
    }

    override suspend fun exists(key: String): Boolean {
        val entry = cache[key] ?: return false

        // Check expiration
        if (entry.expiresAt != null && Clock.System.now() > entry.expiresAt) {
            remove(key)
            return false
        }

        return true
    }

    override suspend fun clear() {
        cache.clear()
        accessOrder.clear()
    }

    override suspend fun increment(key: String, delta: Long): Long {
        mutex.withLock {
            val current = get(key, Long::class.java) ?: 0L
            val newValue = current + delta
            put(key, newValue)
            return newValue
        }
    }

    override suspend fun expire(key: String, ttl: Duration): Boolean {
        val entry = cache[key] ?: return false
        val expiresAt = if (ttl == Duration.INFINITE) null else Clock.System.now() + ttl

        cache[key] = CacheEntry(entry.value, expiresAt)
        return true
    }

    /**
     * Evict entries when cache is full using LRU strategy
     */
    private fun evictIfNeeded() {
        if (cache.size < maxSize) return

        // Remove expired entries first
        val now = Clock.System.now()
        val expiredKeys = cache.entries
            .filter { it.value.expiresAt != null && now > it.value.expiresAt!! }
            .map { it.key }

        expiredKeys.forEach { key ->
            cache.remove(key)
            accessOrder.remove(key)
        }

        // If still over limit, remove LRU entries
        if (cache.size >= maxSize) {
            val sortedByAccess = accessOrder.entries
                .sortedBy { it.value }
                .take(maxSize / 4) // Remove 25% to avoid frequent evictions

            sortedByAccess.forEach { (key, _) ->
                cache.remove(key)
                accessOrder.remove(key)
            }
        }
    }

    /**
     * Get cache statistics for monitoring
     */
    fun getStats(): CacheStats {
        val now = Clock.System.now()
        val expiredCount = cache.values.count {
            it.expiresAt != null && now > it.expiresAt
        }

        return CacheStats(
            size = cache.size,
            maxSize = maxSize,
            expiredEntries = expiredCount
        )
    }
}

data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val expiredEntries: Int
)
