package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.UserUsage
import dev.screenshotapi.core.domain.repositories.UsageRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Plans
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.UsageTracking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class PostgreSQLUsageRepository : UsageRepository {

    override suspend fun findByUserAndMonth(userId: String, month: String): UserUsage? {
        return transaction {
            UsageTracking.select {
                (UsageTracking.userId eq userId) and (UsageTracking.month eq month)
            }.map { row ->
                UserUsage(
                    userId = row[UsageTracking.userId].value,
                    month = row[UsageTracking.month],
                    totalRequests = row[UsageTracking.totalRequests],
                    planCreditsLimit = row[UsageTracking.planCreditsLimit],
                    remainingCredits = row[UsageTracking.remainingCredits],
                    lastRequestAt = row[UsageTracking.lastRequestAt],
                    createdAt = row[UsageTracking.createdAt],
                    updatedAt = row[UsageTracking.updatedAt]
                )
            }.singleOrNull()
        }
    }

    override suspend fun incrementUsage(userId: String, month: String, amount: Int): UserUsage {
        return transaction {
            val existing = UsageTracking.select {
                (UsageTracking.userId eq userId) and (UsageTracking.month eq month)
            }.singleOrNull()

            val now = Clock.System.now()

            if (existing != null) {
                // Update existing record
                val newTotalRequests = existing[UsageTracking.totalRequests] + amount
                val newRemainingCredits = (existing[UsageTracking.remainingCredits] - amount).coerceAtLeast(0)

                UsageTracking.update({
                    (UsageTracking.userId eq userId) and (UsageTracking.month eq month)
                }) {
                    it[totalRequests] = newTotalRequests
                    it[remainingCredits] = newRemainingCredits
                    it[lastRequestAt] = now
                    it[updatedAt] = now
                }

                UserUsage(
                    userId = userId,
                    month = month,
                    totalRequests = newTotalRequests,
                    planCreditsLimit = existing[UsageTracking.planCreditsLimit],
                    remainingCredits = newRemainingCredits,
                    lastRequestAt = now,
                    createdAt = existing[UsageTracking.createdAt],
                    updatedAt = now
                )
            } else {
                // Create new record - need to get plan credits
                val planCredits = getDefaultPlanCredits()
                val newRemainingCredits = (planCredits - amount).coerceAtLeast(0)

                UsageTracking.insert {
                    it[this.userId] = userId
                    it[this.month] = month
                    it[totalRequests] = amount
                    it[planCreditsLimit] = planCredits
                    it[remainingCredits] = newRemainingCredits
                    it[lastRequestAt] = now
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                UserUsage(
                    userId = userId,
                    month = month,
                    totalRequests = amount,
                    planCreditsLimit = planCredits,
                    remainingCredits = newRemainingCredits,
                    lastRequestAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            }
        }
    }

    override suspend fun createUsage(usage: UserUsage): UserUsage {
        return transaction {
            UsageTracking.insert {
                it[userId] = usage.userId
                it[month] = usage.month
                it[totalRequests] = usage.totalRequests
                it[planCreditsLimit] = usage.planCreditsLimit
                it[remainingCredits] = usage.remainingCredits
                it[lastRequestAt] = usage.lastRequestAt
                it[createdAt] = usage.createdAt
                it[updatedAt] = usage.updatedAt
            }
            usage
        }
    }

    override suspend fun updateUsage(usage: UserUsage): UserUsage {
        return transaction {
            UsageTracking.update({
                (UsageTracking.userId eq usage.userId) and (UsageTracking.month eq usage.month)
            }) {
                it[totalRequests] = usage.totalRequests
                it[planCreditsLimit] = usage.planCreditsLimit
                it[remainingCredits] = usage.remainingCredits
                it[lastRequestAt] = usage.lastRequestAt
                it[updatedAt] = usage.updatedAt
            }
            usage
        }
    }

    override suspend fun getUserMonthlyStats(userId: String, year: Int): List<UserUsage> {
        return transaction {
            UsageTracking.select {
                (UsageTracking.userId eq userId) and (UsageTracking.month like "$year-%")
            }.orderBy(UsageTracking.month to SortOrder.DESC)
                .map { row ->
                    UserUsage(
                        userId = row[UsageTracking.userId].value,
                        month = row[UsageTracking.month],
                        totalRequests = row[UsageTracking.totalRequests],
                        planCreditsLimit = row[UsageTracking.planCreditsLimit],
                        remainingCredits = row[UsageTracking.remainingCredits],
                        lastRequestAt = row[UsageTracking.lastRequestAt],
                        createdAt = row[UsageTracking.createdAt],
                        updatedAt = row[UsageTracking.updatedAt]
                    )
                }
        }
    }

    /**
     * Helper function to get default plan credits
     */
    private fun getDefaultPlanCredits(): Int {
        return try {
            val freePlanCredits = Plans.select {
                (Plans.id eq "plan_free") and (Plans.isActive eq true)
            }
                .map { it[Plans.creditsPerMonth] }
                .singleOrNull()
            freePlanCredits?.takeIf { it > 0 } ?: 300
        } catch (e: Exception) {
            300 // Free plan default
        }
    }
}
