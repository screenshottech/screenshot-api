package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.UserUsage

/**
 * Repository interface for usage persistence (Domain Layer)
 */
interface UsageRepository {
    suspend fun findByUserAndMonth(userId: String, month: String): UserUsage?
    suspend fun incrementUsage(userId: String, month: String, amount: Int): UserUsage
    suspend fun createUsage(usage: UserUsage): UserUsage
    suspend fun updateUsage(usage: UserUsage): UserUsage
    suspend fun getUserMonthlyStats(userId: String, year: Int): List<UserUsage>
}