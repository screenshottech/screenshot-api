package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.Plan
import dev.screenshotapi.core.domain.repositories.PlanRepository
import kotlinx.datetime.Clock

class InMemoryPlanRepository : PlanRepository {
    private val plans = mutableMapOf<String, Plan>()

    init {
        // Initialize with default free plan
        val freePlan = Plan(
            id = "plan_free",
            name = "Free Forever",
            description = "3x more generous than competitors - perfect for developers",
            creditsPerMonth = 300,
            priceCentsMonthly = 0,
            priceCentsAnnual = null,
            billingCycle = "monthly",
            currency = "USD",
            features = listOf("Basic screenshots", "PNG/JPEG formats", "Standard support", "API access"),
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        plans[freePlan.id] = freePlan

        // Add other plans for testing
        initializeDefaultPlans()
    }

    override suspend fun findById(id: String): Plan? {
        synchronized(plans) {
            return plans[id]
        }
    }

    override suspend fun findAll(): List<Plan> {
        synchronized(plans) {
            return plans.values
                .sortedBy { it.creditsPerMonth }
                .toList()
        }
    }

    override suspend fun findByBillingCycle(billingCycle: String): List<Plan> {
        synchronized(plans) {
            return plans.values
                .filter { it.billingCycle == billingCycle }
                .sortedBy { it.creditsPerMonth }
                .toList()
        }
    }

    override suspend fun save(plan: Plan): Plan {
        synchronized(plans) {
            plans[plan.id] = plan
            return plan
        }
    }

    override suspend fun update(plan: Plan): Plan {
        synchronized(plans) {
            val updatedPlan = plan.copy(updatedAt = Clock.System.now())
            plans[plan.id] = updatedPlan
            return updatedPlan
        }
    }

    override suspend fun delete(id: String): Boolean {
        synchronized(plans) {
            return plans.remove(id) != null
        }
    }

    private fun initializeDefaultPlans() {
        val now = Clock.System.now()
        
        val defaultPlans = listOf(
            Plan("plan_starter_monthly", "Starter Monthly", "12% cheaper than competitors + OCR included", 
                2000, 1499, null, "monthly", "USD", 
                listOf("All Basic features", "OCR text extraction", "PDF format support", "Priority support", "Webhooks"), 
                true, now, now),
            Plan("plan_starter_annual", "Starter Annual", "12% cheaper + OCR included + 10% annual savings", 
                2000, 1499, 16200, "annual", "USD", 
                listOf("All Basic features", "OCR text extraction", "PDF format support", "Priority support", "Webhooks", "10% savings (2 months free)"), 
                true, now, now),
            Plan("plan_pro_monthly", "Professional Monthly", "13% cheaper + batch processing + analytics dashboard", 
                10000, 6900, null, "monthly", "USD", 
                listOf("All Starter features", "Batch processing", "Analytics dashboard", "Custom dimensions", "Multiple formats", "SLA guarantee"), 
                true, now, now),
            Plan("plan_pro_annual", "Professional Annual", "13% cheaper + batch processing + analytics + 10% annual savings", 
                10000, 6900, 74520, "annual", "USD", 
                listOf("All Starter features", "Batch processing", "Analytics dashboard", "Custom dimensions", "Multiple formats", "SLA guarantee", "10% savings (2 months free)"), 
                true, now, now),
            Plan("plan_enterprise_monthly", "Enterprise Monthly", "12% cheaper + unlimited requests + white-label + on-premise", 
                50000, 22900, null, "monthly", "USD", 
                listOf("All Professional features", "Unlimited concurrent requests", "White-label solution", "On-premise deployment", "Dedicated support", "Custom integrations"), 
                true, now, now),
            Plan("plan_enterprise_annual", "Enterprise Annual", "12% cheaper + unlimited + white-label + on-premise + 10% annual savings", 
                50000, 22900, 247320, "annual", "USD", 
                listOf("All Professional features", "Unlimited concurrent requests", "White-label solution", "On-premise deployment", "Dedicated support", "Custom integrations", "10% savings (2 months free)"), 
                true, now, now)
        )

        defaultPlans.forEach { plan ->
            plans[plan.id] = plan
        }
    }
}