package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.Plan

interface PlanRepository {
    suspend fun findById(id: String): Plan?
    suspend fun findAll(): List<Plan>
    suspend fun findByBillingCycle(billingCycle: String): List<Plan>
    suspend fun save(plan: Plan): Plan
    suspend fun update(plan: Plan): Plan
    suspend fun delete(id: String): Boolean
}