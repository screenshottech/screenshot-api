package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.repositories.PlanRepository

class ListUsersUseCase(
    private val userRepository: UserRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val planRepository: PlanRepository
) {
    suspend operator fun invoke(request: ListUsersRequest): ListUsersResponse {
        try {
            println("[ListUsersUseCase] Getting users with params: page=${request.page}, limit=${request.limit}, search=${request.searchQuery}, status=${request.statusFilter}")
            
            // Get users with pagination using repository method
            val users = userRepository.findAllWithPagination(
                page = request.page,
                limit = request.limit,
                searchQuery = request.searchQuery,
                statusFilter = request.statusFilter
            )
            
            println("[ListUsersUseCase] Found ${users.size} users")
            
            // Get total count for pagination
            val total = userRepository.countAll(
                searchQuery = request.searchQuery,
                statusFilter = request.statusFilter
            )
            
            // Convert to UserSummary DTOs (as expected by ListUsersResponse)
            val userSummaries = users.map { user ->
                // Get screenshot count for user
                val userScreenshots = screenshotRepository.findByUserId(user.id)
                val totalScreenshots = userScreenshots.size.toLong()
                
                // Get plan information from repository
                val planEntity = planRepository.findById(user.planId)
                val planInfo = if (planEntity != null) {
                    PlanInfo(
                        id = planEntity.id,
                        name = planEntity.name,
                        creditsPerMonth = planEntity.creditsPerMonth,
                        priceCents = planEntity.priceCentsMonthly
                    )
                } else {
                    // Fallback if plan not found - use info from User entity
                    PlanInfo(
                        id = user.planId,
                        name = user.planName,
                        creditsPerMonth = 0, // Unknown
                        priceCents = 0 // Unknown
                    )
                }
                
                println("[ListUsersUseCase] User: ${user.email}, roles: ${user.roles.map { it.name }}, plan: ${planInfo.name}")
                
                UserSummary(
                    id = user.id,
                    email = user.email,
                    name = user.name ?: "Unknown",
                    status = user.status,
                    roles = user.roles.map { it.name },
                    plan = planInfo,
                    creditsRemaining = user.creditsRemaining,
                    totalScreenshots = totalScreenshots,
                    lastActivity = user.lastActivity,
                    createdAt = user.createdAt
                )
            }
            
            println("[ListUsersUseCase] Returning ${userSummaries.size} user summaries")
            
            return ListUsersResponse(
                users = userSummaries,
                page = request.page,
                limit = request.limit,
                total = total
            )
        } catch (e: Exception) {
            // Log error and return empty result
            println("[ListUsersUseCase] Error listing users: ${e.message}")
            e.printStackTrace()
            
            return ListUsersResponse(
                users = emptyList(),
                page = request.page,
                limit = request.limit,
                total = 0L
            )
        }
    }
}
