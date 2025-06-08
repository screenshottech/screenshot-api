package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.UserUsage
import dev.screenshotapi.core.domain.entities.UsageTimelineEntry
import dev.screenshotapi.core.domain.entities.TimeGranularity
import dev.screenshotapi.core.domain.repositories.UsageRepository
import kotlinx.datetime.*

/**
 * In-memory implementation of UsageRepository for development/testing
 */
class InMemoryUsageRepository : UsageRepository {
    
    private val usageData = mutableMapOf<String, UserUsage>() // key: "userId:month"
    
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
    
    override suspend fun getUsageTimeline(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        granularity: TimeGranularity
    ): List<UsageTimelineEntry> {
        // Generate date range for in-memory implementation
        val dateRange = mutableListOf<LocalDate>()
        var currentDate = startDate
        while (currentDate <= endDate) {
            dateRange.add(currentDate)
            currentDate = currentDate.plus(DatePeriod(days = 1))
        }

        // For in-memory implementation, return sample data with some variation
        return dateRange.mapIndexed { index, date ->
            // Generate sample data with some patterns
            val dayOfWeek = (date.dayOfMonth % 7) + 1
            val baseScreenshots = if (dayOfWeek <= 5) 10 + (index % 5) else 5 + (index % 3) // Weekdays vs weekends
            val screenshots = if (date == startDate.plus(DatePeriod(days = dateRange.size / 2))) {
                baseScreenshots + 15 // Peak day in the middle
            } else {
                baseScreenshots
            }
            
            UsageTimelineEntry(
                date = date,
                screenshots = screenshots,
                creditsUsed = screenshots * 2, // 2 credits per screenshot
                apiCalls = screenshots + (index % 3), // Slightly more API calls
                successfulScreenshots = (screenshots * 0.9).toInt(), // 90% success rate
                failedScreenshots = screenshots - (screenshots * 0.9).toInt()
            )
        }
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