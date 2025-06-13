package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.BillingCycle
import dev.screenshotapi.core.domain.entities.Subscription
import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Subscriptions
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

class PostgreSQLSubscriptionRepository(private val database: Database) : SubscriptionRepository {
    
    private val logger = LoggerFactory.getLogger(PostgreSQLSubscriptionRepository::class.java)

    override suspend fun findById(id: String): Subscription? = dbQuery(database) {
        try {
            Subscriptions.select { Subscriptions.id eq id }
                .singleOrNull()
                ?.let { mapRowToSubscription(it) }
        } catch (e: Exception) {
            logger.error("Error finding subscription by id: $id", e)
            throw DatabaseException.OperationFailed("Failed to find subscription by id", e)
        }
    }

    override suspend fun findByUserId(userId: String): List<Subscription> = dbQuery(database) {
        try {
            Subscriptions.select { Subscriptions.userId eq userId }
                .orderBy(Subscriptions.createdAt, SortOrder.DESC)
                .map { mapRowToSubscription(it) }
        } catch (e: Exception) {
            logger.error("Error finding subscriptions for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find subscriptions for user", e)
        }
    }

    override suspend fun findActiveByUserId(userId: String): Subscription? = dbQuery(database) {
        try {
            Subscriptions.select { 
                (Subscriptions.userId eq userId) and 
                (Subscriptions.status eq SubscriptionStatus.ACTIVE.name.lowercase())
            }
                .orderBy(Subscriptions.createdAt, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let { mapRowToSubscription(it) }
        } catch (e: Exception) {
            logger.error("Error finding active subscription for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find active subscription for user", e)
        }
    }

    override suspend fun findByStripeSubscriptionId(stripeSubscriptionId: String): Subscription? = dbQuery(database) {
        try {
            Subscriptions.select { Subscriptions.stripeSubscriptionId eq stripeSubscriptionId }
                .singleOrNull()
                ?.let { mapRowToSubscription(it) }
        } catch (e: Exception) {
            logger.error("Error finding subscription by Stripe ID: $stripeSubscriptionId", e)
            throw DatabaseException.OperationFailed("Failed to find subscription by Stripe ID", e)
        }
    }

    override suspend fun findByStripeCustomerId(stripeCustomerId: String): List<Subscription> = dbQuery(database) {
        try {
            Subscriptions.select { Subscriptions.stripeCustomerId eq stripeCustomerId }
                .orderBy(Subscriptions.createdAt, SortOrder.DESC)
                .map { mapRowToSubscription(it) }
        } catch (e: Exception) {
            logger.error("Error finding subscriptions by Stripe customer ID: $stripeCustomerId", e)
            throw DatabaseException.OperationFailed("Failed to find subscriptions by Stripe customer ID", e)
        }
    }

    override suspend fun save(subscription: Subscription): Subscription = dbQuery(database) {
        try {
            Subscriptions.insert {
                it[id] = subscription.id
                it[userId] = subscription.userId
                it[planId] = subscription.planId
                it[billingCycle] = subscription.billingCycle.toExternalString()
                it[status] = subscription.status.name.lowercase()
                it[stripeSubscriptionId] = subscription.stripeSubscriptionId
                it[stripeCustomerId] = subscription.stripeCustomerId
                it[currentPeriodStart] = subscription.currentPeriodStart
                it[currentPeriodEnd] = subscription.currentPeriodEnd
                it[cancelAtPeriodEnd] = subscription.cancelAtPeriodEnd
                it[createdAt] = subscription.createdAt
                it[updatedAt] = subscription.updatedAt
            }
            subscription
        } catch (e: Exception) {
            logger.error("Error saving subscription: ${subscription.id}", e)
            throw DatabaseException.OperationFailed("Failed to save subscription", e)
        }
    }

    override suspend fun update(subscription: Subscription): Subscription = dbQuery(database) {
        try {
            Subscriptions.update({ Subscriptions.id eq subscription.id }) {
                it[userId] = subscription.userId
                it[planId] = subscription.planId
                it[billingCycle] = subscription.billingCycle.toExternalString()
                it[status] = subscription.status.name.lowercase()
                it[stripeSubscriptionId] = subscription.stripeSubscriptionId
                it[stripeCustomerId] = subscription.stripeCustomerId
                it[currentPeriodStart] = subscription.currentPeriodStart
                it[currentPeriodEnd] = subscription.currentPeriodEnd
                it[cancelAtPeriodEnd] = subscription.cancelAtPeriodEnd
                it[updatedAt] = subscription.updatedAt
            }
            subscription
        } catch (e: Exception) {
            logger.error("Error updating subscription: ${subscription.id}", e)
            throw DatabaseException.OperationFailed("Failed to update subscription", e)
        }
    }

    override suspend fun delete(id: String): Unit = dbQuery(database) {
        try {
            Subscriptions.deleteWhere { Subscriptions.id eq id }
        } catch (e: Exception) {
            logger.error("Error deleting subscription: $id", e)
            throw DatabaseException.OperationFailed("Failed to delete subscription", e)
        }
    }

    override suspend fun findByStatus(status: SubscriptionStatus): List<Subscription> = dbQuery(database) {
        try {
            Subscriptions.select { Subscriptions.status eq status.name.lowercase() }
                .orderBy(Subscriptions.createdAt, SortOrder.DESC)
                .map { mapRowToSubscription(it) }
        } catch (e: Exception) {
            logger.error("Error finding subscriptions by status: $status", e)
            throw DatabaseException.OperationFailed("Failed to find subscriptions by status", e)
        }
    }

    override suspend fun findToRenewSoon(beforeDate: Instant): List<Subscription> = dbQuery(database) {
        try {
            Subscriptions.select { 
                (Subscriptions.currentPeriodEnd lessEq beforeDate) and
                (Subscriptions.status eq SubscriptionStatus.ACTIVE.name.lowercase())
            }
                .orderBy(Subscriptions.currentPeriodEnd, SortOrder.ASC)
                .map { mapRowToSubscription(it) }
        } catch (e: Exception) {
            logger.error("Error finding subscriptions to renew before: $beforeDate", e)
            throw DatabaseException.OperationFailed("Failed to find subscriptions to renew", e)
        }
    }

    override suspend fun findAll(): List<Subscription> = dbQuery(database) {
        try {
            Subscriptions.selectAll()
                .orderBy(Subscriptions.createdAt, SortOrder.DESC)
                .map { mapRowToSubscription(it) }
        } catch (e: Exception) {
            logger.error("Error finding all subscriptions", e)
            throw DatabaseException.OperationFailed("Failed to find all subscriptions", e)
        }
    }

    override suspend fun countAll(): Long = dbQuery(database) {
        try {
            Subscriptions.selectAll().count()
        } catch (e: Exception) {
            logger.error("Error counting all subscriptions", e)
            throw DatabaseException.OperationFailed("Failed to count all subscriptions", e)
        }
    }

    override suspend fun findAllWithPagination(
        page: Int,
        limit: Int,
        searchQuery: String?,
        statusFilter: SubscriptionStatus?,
        planIdFilter: String?
    ): List<Subscription> = dbQuery(database) {
        try {
            val offset = (page - 1) * limit
            
            // Build the query with filters
            val query = Subscriptions.selectAll()
            
            // Apply status filter
            if (statusFilter != null) {
                query.adjustWhere { Subscriptions.status eq statusFilter.name.lowercase() }
            }
            
            // Apply plan filter
            if (!planIdFilter.isNullOrEmpty()) {
                query.adjustWhere { Subscriptions.planId eq planIdFilter }
            }
            
            // Apply search filter on user data (this would need a join with Users table)
            // For now, we'll handle this at the use case level since we need user email/name
            
            query.orderBy(Subscriptions.createdAt, SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { mapRowToSubscription(it) }
                
        } catch (e: Exception) {
            logger.error("Error finding subscriptions with pagination", e)
            throw DatabaseException.OperationFailed("Failed to find subscriptions with pagination", e)
        }
    }

    override suspend fun countAllWithFilters(
        searchQuery: String?,
        statusFilter: SubscriptionStatus?,
        planIdFilter: String?
    ): Long = dbQuery(database) {
        try {
            val query = Subscriptions.selectAll()
            
            // Apply status filter
            if (statusFilter != null) {
                query.adjustWhere { Subscriptions.status eq statusFilter.name.lowercase() }
            }
            
            // Apply plan filter
            if (!planIdFilter.isNullOrEmpty()) {
                query.adjustWhere { Subscriptions.planId eq planIdFilter }
            }
            
            // Search filter would need to be handled at use case level for user email/name
            
            query.count()
            
        } catch (e: Exception) {
            logger.error("Error counting subscriptions with filters", e)
            throw DatabaseException.OperationFailed("Failed to count subscriptions with filters", e)
        }
    }

    /**
     * Maps a database row to a Subscription domain entity.
     */
    private fun mapRowToSubscription(row: ResultRow): Subscription {
        return Subscription(
            id = row[Subscriptions.id],
            userId = row[Subscriptions.userId],
            planId = row[Subscriptions.planId],
            billingCycle = BillingCycle.fromString(row[Subscriptions.billingCycle]),
            status = SubscriptionStatus.fromString(row[Subscriptions.status]),
            stripeSubscriptionId = row[Subscriptions.stripeSubscriptionId],
            stripeCustomerId = row[Subscriptions.stripeCustomerId],
            currentPeriodStart = row[Subscriptions.currentPeriodStart],
            currentPeriodEnd = row[Subscriptions.currentPeriodEnd],
            cancelAtPeriodEnd = row[Subscriptions.cancelAtPeriodEnd],
            createdAt = row[Subscriptions.createdAt],
            updatedAt = row[Subscriptions.updatedAt]
        )
    }
}