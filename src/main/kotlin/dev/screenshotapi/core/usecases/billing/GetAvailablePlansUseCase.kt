package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.entities.Plan
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.usecases.common.UseCase

/**
 * Request model for getting available plans
 */
data class GetAvailablePlansRequest(
    val includeInactive: Boolean = false
)

/**
 * Response model for available plans
 */
data class GetAvailablePlansResponse(
    val plans: List<Plan>
)

/**
 * Use case for retrieving available subscription plans.
 * Follows clean architecture principles and existing patterns.
 */
class GetAvailablePlansUseCase(
    private val planRepository: PlanRepository
) : UseCase<GetAvailablePlansRequest, GetAvailablePlansResponse> {

    override suspend operator fun invoke(request: GetAvailablePlansRequest): GetAvailablePlansResponse {
        val allPlans = planRepository.findAll()
        val plans = if (request.includeInactive) {
            allPlans
        } else {
            allPlans.filter { it.isActive }
        }
        
        return GetAvailablePlansResponse(
            plans = plans.sortedBy { it.priceCentsMonthly }
        )
    }
}