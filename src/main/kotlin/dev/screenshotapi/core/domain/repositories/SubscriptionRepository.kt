package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.Subscription
import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import kotlinx.datetime.Instant

/**
 * Repository interface for Subscription entity operations.
 * Following domain-driven design with clean architecture principles.
 */
interface SubscriptionRepository {
    suspend fun findById(id: String): Subscription?
    suspend fun findByUserId(userId: String): List<Subscription>
    suspend fun findActiveByUserId(userId: String): Subscription?
    suspend fun findByStripeSubscriptionId(stripeSubscriptionId: String): Subscription?
    suspend fun findByStripeCustomerId(stripeCustomerId: String): List<Subscription>
    suspend fun save(subscription: Subscription): Subscription
    suspend fun update(subscription: Subscription): Subscription
    suspend fun delete(id: String)
    suspend fun findByStatus(status: SubscriptionStatus): List<Subscription>
    suspend fun findToRenewSoon(beforeDate: Instant): List<Subscription>
    suspend fun findAll(): List<Subscription>
    suspend fun countAll(): Long
    suspend fun findAllWithPagination(
        page: Int,
        limit: Int,
        searchQuery: String? = null,
        statusFilter: SubscriptionStatus? = null,
        planIdFilter: String? = null
    ): List<Subscription>
    suspend fun countAllWithFilters(
        searchQuery: String? = null,
        statusFilter: SubscriptionStatus? = null,
        planIdFilter: String? = null
    ): Long
}