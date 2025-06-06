package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.Plan
import dev.screenshotapi.core.domain.entities.User
import dev.screenshotapi.core.domain.entities.UserStatus

interface UserRepository {
    suspend fun save(user: User): User
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findByExternalId(externalId: String, authProvider: String): User?
    suspend fun update(user: User): User
    suspend fun delete(id: String): Boolean
    suspend fun findByStripeCustomerId(customerId: String): User?
    suspend fun findAllWithPagination(
        page: Int,
        limit: Int,
        searchQuery: String? = null,
        statusFilter: UserStatus? = null
    ): List<User>

    suspend fun countAll(
        searchQuery: String? = null,
        statusFilter: UserStatus? = null
    ): Long

    suspend fun findWithDetails(userId: String): UserWithDetails?
    suspend fun getTopUsers(limit: Int = 10): List<TopUserInfo>
    suspend fun getNewUsersCount(days: Int): Long
    suspend fun getActiveUsersCount(): Long
}


data class UserWithDetails(
    val user: User,
    val screenshotStats: UserScreenshotStats,
    val plan: Plan
)

data class UserScreenshotStats(
    val total: Long,
    val successful: Long,
    val failed: Long,
    val creditsUsed: Long
)

data class TopUserInfo(
    val userId: String,
    val email: String,
    val name: String?,
    val screenshotCount: Long,
    val creditsUsed: Long
)
