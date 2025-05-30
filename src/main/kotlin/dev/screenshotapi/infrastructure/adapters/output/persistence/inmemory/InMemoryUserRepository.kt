package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.Plan
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.entities.User
import dev.screenshotapi.core.domain.entities.UserStatus
import dev.screenshotapi.core.domain.repositories.TopUserInfo
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.UserScreenshotStats
import dev.screenshotapi.core.domain.repositories.UserWithDetails
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

class InMemoryUserRepository : UserRepository {
    override suspend fun save(user: User): User = InMemoryDatabase.saveUser(user)

    override suspend fun findById(id: String): User? = InMemoryDatabase.findUser(id)

    override suspend fun findByEmail(email: String): User? = InMemoryDatabase.findUserByEmail(email)

    override suspend fun update(user: User): User = InMemoryDatabase.saveUser(user)

    override suspend fun delete(id: String): Boolean {
        // Implementation for delete
        return true
    }

    override suspend fun findByStripeCustomerId(customerId: String): User? {
        // Implementation for Stripe lookup
        return null
    }

    override suspend fun findAllWithPagination(
        page: Int,
        limit: Int,
        searchQuery: String?,
        statusFilter: UserStatus?
    ): List<User> {
        var filteredUsers = InMemoryDatabase.getAllUsers()

        if (!searchQuery.isNullOrBlank()) {
            filteredUsers = filteredUsers.filter { user ->
                user.email.contains(searchQuery, ignoreCase = true) ||
                        user.name?.contains(searchQuery, ignoreCase = true) == true
            }
        }

        if (statusFilter != null) {
            filteredUsers = filteredUsers.filter { it.status == statusFilter }
        }

        val startIndex = (page - 1) * limit
        return filteredUsers
            .sortedByDescending { it.createdAt }
            .drop(startIndex)
            .take(limit)
    }

    override suspend fun countAll(
        searchQuery: String?,
        statusFilter: UserStatus?
    ): Long {
        var filteredUsers = InMemoryDatabase.getAllUsers()

        if (!searchQuery.isNullOrBlank()) {
            filteredUsers = filteredUsers.filter { user ->
                user.email.contains(searchQuery, ignoreCase = true) ||
                        user.name?.contains(searchQuery, ignoreCase = true) == true
            }
        }

        if (statusFilter != null) {
            filteredUsers = filteredUsers.filter { it.status == statusFilter }
        }

        return filteredUsers.size.toLong()
    }

    override suspend fun findWithDetails(userId: String): UserWithDetails? {
        val user = InMemoryDatabase.findUser(userId) ?: return null

        val screenshots = InMemoryDatabase.findScreenshotsByUser(userId)
        val screenshotStats = UserScreenshotStats(
            total = screenshots.size.toLong(),
            successful = screenshots.count { it.status == ScreenshotStatus.COMPLETED }.toLong(),
            failed = screenshots.count { it.status == ScreenshotStatus.FAILED }.toLong(),
            creditsUsed = screenshots.count { it.status == ScreenshotStatus.COMPLETED }.toLong()
        )

        val plan = Plan(
            id = user.planId,
            name = user.planName,
            creditsPerMonth = 1000,
            priceCents = 0,
            createdAt = kotlinx.datetime.Clock.System.now(),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )

        return UserWithDetails(
            user = user,
            screenshotStats = screenshotStats,
            plan = plan
        )
    }

    override suspend fun getTopUsers(limit: Int): List<TopUserInfo> {
        val users = InMemoryDatabase.getAllUsers()

        return users.map { user ->
            val screenshots = InMemoryDatabase.findScreenshotsByUser(user.id)
            TopUserInfo(
                userId = user.id,
                email = user.email,
                name = user.name,
                screenshotCount = screenshots.size.toLong(),
                creditsUsed = screenshots.count { it.status == ScreenshotStatus.COMPLETED }.toLong()
            )
        }
            .sortedByDescending { it.screenshotCount }
            .take(limit)
    }

    override suspend fun getNewUsersCount(days: Int): Long {
        val cutoffDate = Clock.System.now().minus(days.days)
        return InMemoryDatabase.getAllUsers()
            .count { it.createdAt >= cutoffDate }
            .toLong()
    }

    override suspend fun getActiveUsersCount(): Long {
        return InMemoryDatabase.getAllUsers()
            .count { it.status == UserStatus.ACTIVE }
            .toLong()
    }
}
