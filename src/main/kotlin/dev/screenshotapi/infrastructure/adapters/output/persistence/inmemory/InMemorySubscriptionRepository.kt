package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.Subscription
import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import kotlinx.datetime.Instant

/**
 * In-memory implementation of SubscriptionRepository for development and testing.
 * Following the same patterns as other in-memory repositories in the codebase.
 */
class InMemorySubscriptionRepository : SubscriptionRepository {

    override suspend fun findById(id: String): Subscription? {
        return InMemoryDatabase.findSubscriptionById(id)
    }

    override suspend fun findByUserId(userId: String): List<Subscription> {
        return InMemoryDatabase.findSubscriptionsByUserId(userId)
    }

    override suspend fun findActiveByUserId(userId: String): Subscription? {
        return InMemoryDatabase.findActiveSubscriptionByUserId(userId)
    }

    override suspend fun findByStripeSubscriptionId(stripeSubscriptionId: String): Subscription? {
        return InMemoryDatabase.findSubscriptionByStripeSubscriptionId(stripeSubscriptionId)
    }

    override suspend fun findByStripeCustomerId(stripeCustomerId: String): List<Subscription> {
        return InMemoryDatabase.findSubscriptionsByStripeCustomerId(stripeCustomerId)
    }

    override suspend fun save(subscription: Subscription): Subscription {
        return InMemoryDatabase.saveSubscription(subscription)
    }

    override suspend fun update(subscription: Subscription): Subscription {
        return InMemoryDatabase.saveSubscription(subscription) // In-memory treats save/update the same
    }

    override suspend fun delete(id: String) {
        InMemoryDatabase.deleteSubscription(id)
    }

    override suspend fun findByStatus(status: SubscriptionStatus): List<Subscription> {
        return InMemoryDatabase.findSubscriptionsByStatus(status)
    }

    override suspend fun findToRenewSoon(beforeDate: Instant): List<Subscription> {
        return InMemoryDatabase.findSubscriptionsToRenewSoon(beforeDate)
    }

    override suspend fun findAll(): List<Subscription> {
        return InMemoryDatabase.getAllSubscriptions()
    }

    override suspend fun countAll(): Long {
        return InMemoryDatabase.countAllSubscriptions()
    }

    override suspend fun findAllWithPagination(
        page: Int,
        limit: Int,
        searchQuery: String?,
        statusFilter: SubscriptionStatus?,
        planIdFilter: String?
    ): List<Subscription> {
        return InMemoryDatabase.findSubscriptionsWithPagination(
            page = page,
            limit = limit,
            searchQuery = searchQuery,
            statusFilter = statusFilter,
            planIdFilter = planIdFilter
        )
    }

    override suspend fun countAllWithFilters(
        searchQuery: String?,
        statusFilter: SubscriptionStatus?,
        planIdFilter: String?
    ): Long {
        return InMemoryDatabase.countSubscriptionsWithFilters(
            searchQuery = searchQuery,
            statusFilter = statusFilter,
            planIdFilter = planIdFilter
        )
    }
}