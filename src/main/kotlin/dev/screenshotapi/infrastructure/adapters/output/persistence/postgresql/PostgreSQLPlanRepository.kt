package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.Plan
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Plans
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.toPlan
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class PostgreSQLPlanRepository(private val database: Database) : PlanRepository {

    override suspend fun findById(id: String): Plan? = dbQuery(database) {
        Plans.select { Plans.id eq id }
            .singleOrNull()
            ?.toPlan()
    }

    override suspend fun findAll(): List<Plan> = dbQuery(database) {
        Plans.selectAll()
            .orderBy(Plans.creditsPerMonth, SortOrder.ASC)
            .map { it.toPlan() }
    }

    override suspend fun findByBillingCycle(billingCycle: String): List<Plan> = dbQuery(database) {
        Plans.select { Plans.billingCycle eq billingCycle }
            .orderBy(Plans.creditsPerMonth, SortOrder.ASC)
            .map { it.toPlan() }
    }

    override suspend fun save(plan: Plan): Plan = dbQuery(database) {
        val now = Clock.System.now()
        
        Plans.insert {
            it[id] = plan.id
            it[name] = plan.name
            it[description] = plan.description
            it[creditsPerMonth] = plan.creditsPerMonth
            it[priceCentsMonthly] = plan.priceCentsMonthly
            it[priceCentsAnnual] = plan.priceCentsAnnual
            it[billingCycle] = plan.billingCycle
            it[currency] = plan.currency
            it[features] = plan.features.joinToString(",")
            it[isActive] = plan.isActive
            it[stripeProductId] = plan.stripeProductId
            it[stripePriceIdMonthly] = plan.stripePriceIdMonthly
            it[stripePriceIdAnnual] = plan.stripePriceIdAnnual
            it[stripeMetadata] = plan.stripeMetadata
            it[sortOrder] = plan.sortOrder
            it[createdAt] = plan.createdAt
            it[updatedAt] = now
        }

        Plans.select { Plans.id eq plan.id }
            .single()
            .toPlan()
    }

    override suspend fun update(plan: Plan): Plan = dbQuery(database) {
        Plans.update({ Plans.id eq plan.id }) {
            it[name] = plan.name
            it[description] = plan.description
            it[creditsPerMonth] = plan.creditsPerMonth
            it[priceCentsMonthly] = plan.priceCentsMonthly
            it[priceCentsAnnual] = plan.priceCentsAnnual
            it[billingCycle] = plan.billingCycle
            it[currency] = plan.currency
            it[features] = plan.features.joinToString(",")
            it[isActive] = plan.isActive
            it[stripeProductId] = plan.stripeProductId
            it[stripePriceIdMonthly] = plan.stripePriceIdMonthly
            it[stripePriceIdAnnual] = plan.stripePriceIdAnnual
            it[stripeMetadata] = plan.stripeMetadata
            it[sortOrder] = plan.sortOrder
            it[updatedAt] = Clock.System.now()
        }

        Plans.select { Plans.id eq plan.id }
            .single()
            .toPlan()
    }

    override suspend fun delete(id: String): Boolean = dbQuery(database) {
        Plans.deleteWhere { Plans.id eq id } > 0
    }
}