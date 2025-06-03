package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.Plan
import dev.screenshotapi.core.domain.entities.ScreenshotStatus
import dev.screenshotapi.core.domain.entities.User
import dev.screenshotapi.core.domain.entities.UserStatus
import dev.screenshotapi.core.domain.repositories.TopUserInfo
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.UserScreenshotStats
import dev.screenshotapi.core.domain.repositories.UserWithDetails
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*
import kotlin.time.Duration.Companion.days

class PostgreSQLUserRepository(private val database: Database) : UserRepository {

    override suspend fun save(user: User): User = dbQuery(database) {
        val existingUser = if (user.id.isNotBlank()) {
            Users.select { Users.id eq user.id }.singleOrNull()
        } else null

        if (existingUser != null) {
            // Update existing user
            Users.update({ Users.id eq user.id }) {
                it[email] = user.email
                it[name] = user.name
                it[passwordHash] = user.passwordHash
                it[planId] = user.planId
                it[creditsRemaining] = user.creditsRemaining
                it[status] = user.status.name
                it[stripeCustomerId] = user.stripeCustomerId
                it[lastActivity] = user.lastActivity
                it[updatedAt] = Clock.System.now()
            }

            Users.select { Users.id eq user.id }
                .single()
                .toUser()
        } else {
            // Insert new user
            val id = user.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val now = Clock.System.now()

            Users.insert {
                it[Users.id] = id
                it[email] = user.email
                it[name] = user.name
                it[passwordHash] = user.passwordHash
                it[planId] = user.planId
                it[creditsRemaining] = user.creditsRemaining
                it[status] = user.status.name
                it[stripeCustomerId] = user.stripeCustomerId
                it[lastActivity] = user.lastActivity
                it[createdAt] = now
                it[updatedAt] = now
            }

            Users.select { Users.id eq id }
                .single()
                .toUser()
        }
    }

    override suspend fun findById(id: String): User? = dbQuery(database) {
        Users.select { Users.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun findByEmail(email: String): User? = dbQuery(database) {
        Users.select { Users.email eq email }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun update(user: User): User = dbQuery(database) {
        Users.update({ Users.id eq user.id }) {
            it[email] = user.email
            it[name] = user.name
            it[passwordHash] = user.passwordHash
            it[planId] = user.planId
            it[creditsRemaining] = user.creditsRemaining
            it[status] = user.status.name
            it[stripeCustomerId] = user.stripeCustomerId
            it[lastActivity] = user.lastActivity
            it[updatedAt] = Clock.System.now()
        }

        Users.select { Users.id eq user.id }
            .single()
            .toUser()
    }

    override suspend fun delete(id: String): Boolean = dbQuery(database) {
        Users.deleteWhere { Users.id eq id } > 0
    }

    override suspend fun findByStripeCustomerId(customerId: String): User? = dbQuery(database) {
        Users.select { Users.stripeCustomerId eq customerId }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun findAllWithPagination(
        page: Int,
        limit: Int,
        searchQuery: String?,
        statusFilter: UserStatus?
    ): List<User> = dbQuery(database) {
        val query = Users.selectAll()

        // Apply search filter
        if (!searchQuery.isNullOrBlank()) {
            query.andWhere {
                (Users.email like "%$searchQuery%") or
                        (Users.name.isNotNull() and (Users.name like "%$searchQuery%"))
            }
        }

        // Apply status filter
        if (statusFilter != null) {
            query.andWhere { Users.status eq statusFilter.name }
        }

        // Apply pagination
        val offset = (page - 1) * limit

        query.orderBy(Users.createdAt, SortOrder.DESC)
            .limit(limit, offset.toLong())
            .map { it.toUser() }
    }

    override suspend fun countAll(
        searchQuery: String?,
        statusFilter: UserStatus?
    ): Long = dbQuery(database) {
        val query = Users.selectAll()

        // Apply search filter
        if (!searchQuery.isNullOrBlank()) {
            query.andWhere {
                (Users.email like "%$searchQuery%") or
                        (Users.name.isNotNull() and (Users.name like "%$searchQuery%"))
            }
        }

        // Apply status filter
        if (statusFilter != null) {
            query.andWhere { Users.status eq statusFilter.name }
        }

        query.count()
    }

    override suspend fun findWithDetails(userId: String): UserWithDetails? = dbQuery(database) {
        val user = findById(userId) ?: return@dbQuery null

        // Get screenshotapi stats
        val screenshotStats = Screenshots
            .select { Screenshots.userId eq userId }
            .let { rows ->
                UserScreenshotStats(
                    total = rows.count().toLong(),
                    successful = rows.count { it[Screenshots.status] == ScreenshotStatus.COMPLETED.name }.toLong(),
                    failed = rows.count { it[Screenshots.status] == ScreenshotStatus.FAILED.name }.toLong(),
                    creditsUsed = rows.count { it[Screenshots.status] == ScreenshotStatus.COMPLETED.name }.toLong()
                )
            }

        // Get plan
        val plan = Plans
            .select { Plans.id eq user.planId }
            .singleOrNull()
            ?.toPlan() ?: Plan(
            id = user.planId,
            name = user.planName,
            creditsPerMonth = 1000,
            priceCentsMonthly = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        UserWithDetails(
            user = user,
            screenshotStats = screenshotStats,
            plan = plan
        )
    }

    override suspend fun getTopUsers(limit: Int): List<TopUserInfo> = dbQuery(database) {
        val query = Users
            .join(Screenshots, JoinType.LEFT, Users.id, Screenshots.userId)
            .slice(
                Users.id,
                Users.email,
                Users.name,
                Screenshots.id.count(),
                Screenshots.id.count()
            )
            .selectAll()
            .groupBy(Users.id, Users.email, Users.name)
            .orderBy(Screenshots.id.count(), SortOrder.DESC)
            .limit(limit)

        query.map { row ->
            TopUserInfo(
                userId = row[Users.id].toString(),
                email = row[Users.email],
                name = row[Users.name],
                screenshotCount = row[Screenshots.id.count()],
                creditsUsed = row[Screenshots.id.count()]
            )
        }
    }

    override suspend fun getNewUsersCount(days: Int): Long = dbQuery(database) {
        val cutoffDate = Clock.System.now().minus(days.days)

        Users.select { Users.createdAt greaterEq cutoffDate }
            .count()
    }

    override suspend fun getActiveUsersCount(): Long = dbQuery(database) {
        Users.select { Users.status eq UserStatus.ACTIVE.name }
            .count()
    }
}
