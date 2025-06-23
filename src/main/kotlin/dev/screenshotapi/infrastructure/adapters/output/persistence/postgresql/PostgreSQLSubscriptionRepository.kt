package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.BillingCycle
import dev.screenshotapi.core.domain.entities.Subscription
import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Subscriptions
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.Clock
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
            // Use atomic saveOrUpdateByStripeId for true idempotency
            if (subscription.stripeSubscriptionId?.isNotBlank() == true) {
                logger.info("SUBSCRIPTION_UPSERT_STRIPE: Using atomic upsert by Stripe ID [id=${subscription.id}, stripeSubscriptionId=${subscription.stripeSubscriptionId}]")
                return@dbQuery saveOrUpdateByStripeId(subscription)
            }

            // Fallback to ID-based upsert for subscriptions without Stripe ID
            logger.info("SUBSCRIPTION_UPSERT_ID: Using ID-based upsert [id=${subscription.id}]")
            return@dbQuery saveOrUpdateById(subscription)

        } catch (e: Exception) {
            logger.error("Error saving subscription: ${subscription.id}", e)
            throw DatabaseException.OperationFailed("Failed to save subscription", e)
        }
    }

    /**
     * Atomic upsert operation using Stripe subscription ID.
     * This method is race-condition safe and handles duplicate webhook processing.
     */
    private suspend fun saveOrUpdateByStripeId(subscription: Subscription): Subscription = dbQuery(database) {
        try {
            logger.debug("SUBSCRIPTION_UPSERT_ATTEMPT: Attempting atomic upsert [stripeSubscriptionId=${subscription.stripeSubscriptionId}]")
            
            // First check if subscription exists to preserve original ID and createdAt
            val existing = findByStripeSubscriptionId(subscription.stripeSubscriptionId!!)
            val finalSubscription = if (existing != null) {
                logger.info("SUBSCRIPTION_UPSERT_UPDATE: Existing subscription found, preserving metadata [existingId=${existing.id}, createdAt=${existing.createdAt}]")
                subscription.copy(
                    id = existing.id,
                    createdAt = existing.createdAt,
                    updatedAt = Clock.System.now()
                )
            } else {
                logger.info("SUBSCRIPTION_UPSERT_INSERT: No existing subscription, will create new [id=${subscription.id}]")
                subscription
            }
            
            // Use upsert with stripeSubscriptionId as the key for conflict resolution
            Subscriptions.upsert(
                keys = arrayOf(Subscriptions.stripeSubscriptionId)
            ) {
                it[id] = finalSubscription.id
                it[userId] = finalSubscription.userId
                it[planId] = finalSubscription.planId
                it[billingCycle] = finalSubscription.billingCycle.toExternalString()
                it[status] = finalSubscription.status.name.lowercase()
                it[stripeSubscriptionId] = finalSubscription.stripeSubscriptionId
                it[stripeCustomerId] = finalSubscription.stripeCustomerId
                it[currentPeriodStart] = finalSubscription.currentPeriodStart
                it[currentPeriodEnd] = finalSubscription.currentPeriodEnd
                it[cancelAtPeriodEnd] = finalSubscription.cancelAtPeriodEnd
                it[createdAt] = finalSubscription.createdAt
                it[updatedAt] = finalSubscription.updatedAt
            }
            
            logger.info("SUBSCRIPTION_UPSERT_SUCCESS: Subscription saved successfully [id=${finalSubscription.id}, stripeSubscriptionId=${subscription.stripeSubscriptionId}, wasUpdate=${existing != null}]")
            return@dbQuery finalSubscription
            
        } catch (e: Exception) {
            logger.error("Error in saveOrUpdateByStripeId for subscription: ${subscription.id} [stripeSubscriptionId=${subscription.stripeSubscriptionId}, userId=${subscription.userId}, error=${e.javaClass.simpleName}]", e)
            throw DatabaseException.OperationFailed("Failed to upsert subscription by Stripe ID", e)
        }
    }

    /**
     * Fallback upsert operation using subscription ID.
     * Uses Exposed's native upsert functionality for atomicity.
     */
    private suspend fun saveOrUpdateById(subscription: Subscription): Subscription = dbQuery(database) {
        try {
            logger.debug("SUBSCRIPTION_UPSERT_BY_ID: Attempting upsert by ID [id=${subscription.id}]")
            
            // Check if subscription exists to preserve createdAt
            val existing = findById(subscription.id)
            val finalSubscription = if (existing != null) {
                logger.info("SUBSCRIPTION_UPDATE_BY_ID: Updating existing subscription [id=${subscription.id}]")
                subscription.copy(
                    createdAt = existing.createdAt,
                    updatedAt = Clock.System.now()
                )
            } else {
                logger.info("SUBSCRIPTION_CREATE_BY_ID: Creating new subscription [id=${subscription.id}]")
                subscription
            }
            
            // Use upsert with primary key (id)
            Subscriptions.upsert {
                it[id] = finalSubscription.id
                it[userId] = finalSubscription.userId
                it[planId] = finalSubscription.planId
                it[billingCycle] = finalSubscription.billingCycle.toExternalString()
                it[status] = finalSubscription.status.name.lowercase()
                it[stripeSubscriptionId] = finalSubscription.stripeSubscriptionId
                it[stripeCustomerId] = finalSubscription.stripeCustomerId
                it[currentPeriodStart] = finalSubscription.currentPeriodStart
                it[currentPeriodEnd] = finalSubscription.currentPeriodEnd
                it[cancelAtPeriodEnd] = finalSubscription.cancelAtPeriodEnd
                it[createdAt] = finalSubscription.createdAt
                it[updatedAt] = finalSubscription.updatedAt
            }
            
            logger.info("SUBSCRIPTION_UPSERT_BY_ID_SUCCESS: Subscription saved [id=${subscription.id}, wasUpdate=${existing != null}]")
            return@dbQuery finalSubscription
            
        } catch (e: Exception) {
            logger.error("Error in saveOrUpdateById for subscription: ${subscription.id} [userId=${subscription.userId}, planId=${subscription.planId}, error=${e.javaClass.simpleName}]", e)
            throw DatabaseException.OperationFailed("Failed to upsert subscription by ID", e)
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
