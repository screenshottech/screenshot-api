package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.UserUsage
import dev.screenshotapi.core.domain.repositories.UsageRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of UsageRepository for development/testing
 */
class InMemoryUsageRepository : UsageRepository {
    
    private val usageData = ConcurrentHashMap<String, UserUsage>() // key: "userId:month"
    
    override suspend fun findByUserAndMonth(userId: String, month: String): UserUsage? {
        return usageData["$userId:$month"]
    }
    
    override suspend fun incrementUsage(userId: String, month: String, amount: Int): UserUsage {
        val key = "$userId:$month"
        val now = Clock.System.now()
        
        val existing = usageData[key]
        
        return if (existing != null) {
            // Update existing record
            val newTotalRequests = existing.totalRequests + amount
            val newRemainingCredits = (existing.remainingCredits - amount).coerceAtLeast(0)
            
            val updated = existing.copy(
                totalRequests = newTotalRequests,
                remainingCredits = newRemainingCredits,
                lastRequestAt = now,
                updatedAt = now
            )
            
            usageData[key] = updated
            updated
        } else {
            // Create new record - use default plan credits
            val planCredits = getDefaultPlanCredits()
            val newRemainingCredits = (planCredits - amount).coerceAtLeast(0)
            
            val newUsage = UserUsage(
                userId = userId,
                month = month,
                totalRequests = amount,
                planCreditsLimit = planCredits,
                remainingCredits = newRemainingCredits,
                lastRequestAt = now,
                createdAt = now,
                updatedAt = now
            )
            
            usageData[key] = newUsage
            newUsage
        }
    }
    
    override suspend fun createUsage(usage: UserUsage): UserUsage {
        val key = "${usage.userId}:${usage.month}"
        usageData[key] = usage
        return usage
    }
    
    override suspend fun updateUsage(usage: UserUsage): UserUsage {
        val key = "${usage.userId}:${usage.month}"
        usageData[key] = usage
        return usage
    }
    
    override suspend fun getUserMonthlyStats(userId: String, year: Int): List<UserUsage> {
        return usageData.values
            .filter { it.userId == userId && it.month.startsWith("$year-") }
            .sortedByDescending { it.month }
    }
    
    /**
     * Get default plan credits (for testing/development)
     */
    private fun getDefaultPlanCredits(): Int {
        return 300 // Free plan default
    }
    
    /**
     * Clear all data (useful for testing)
     */
    fun clear() {
        usageData.clear()
    }
}