package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.repositories.UsageLogRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.entities.UsageLog
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.entities.UserActivityType
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException

class GetUserActivityUseCase(
    private val usageLogRepository: UsageLogRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: GetUserActivityRequest): GetUserActivityResponse {
        // Verify user exists
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User", request.userId)
        
        // Get recent usage logs for the user
        val recentLogs = usageLogRepository.findByUserId(
            userId = request.userId,
            limit = request.limit,
            offset = 0
        )
        
        // Convert usage logs to user activities
        val activities = recentLogs.map { log ->
            UserActivity(
                id = log.id,
                userId = log.userId,
                type = convertUsageLogActionToUserActivityType(log.action),
                description = generateActivityDescription(log),
                metadata = log.metadata,
                timestamp = log.timestamp
            )
        }
        
        return GetUserActivityResponse(
            userId = request.userId,
            days = request.days,
            activities = activities
        )
    }
    
    private fun convertUsageLogActionToUserActivityType(action: UsageLogAction): UserActivityType {
        return when (action) {
            UsageLogAction.SCREENSHOT_CREATED -> UserActivityType.SCREENSHOT_CREATED
            UsageLogAction.SCREENSHOT_COMPLETED -> UserActivityType.SCREENSHOT_COMPLETED
            UsageLogAction.SCREENSHOT_FAILED -> UserActivityType.SCREENSHOT_FAILED
            UsageLogAction.API_KEY_CREATED -> UserActivityType.API_KEY_CREATED
            UsageLogAction.API_KEY_USED -> UserActivityType.LOGIN // API usage as activity
            UsageLogAction.USER_REGISTERED -> UserActivityType.LOGIN // Registration as first login
            UsageLogAction.PLAN_UPGRADED -> UserActivityType.PLAN_CHANGED
            UsageLogAction.PLAN_CHANGED -> UserActivityType.PLAN_CHANGED
            UsageLogAction.PAYMENT_PROCESSED -> UserActivityType.PAYMENT_SUCCESS
            UsageLogAction.CREDITS_DEDUCTED -> UserActivityType.SCREENSHOT_CREATED // Credit usage implies screenshot
            UsageLogAction.CREDITS_ADDED -> UserActivityType.CREDITS_PROVISIONED_ADMIN
        }
    }
    
    private fun generateActivityDescription(log: UsageLog): String {
        return when (log.action) {
            UsageLogAction.SCREENSHOT_CREATED -> 
                "Created screenshot request"
            UsageLogAction.SCREENSHOT_COMPLETED -> 
                "Screenshot generation completed successfully"
            UsageLogAction.SCREENSHOT_FAILED -> 
                "Screenshot generation failed"
            UsageLogAction.CREDITS_DEDUCTED -> 
                "Used ${log.creditsUsed} credits"
            UsageLogAction.CREDITS_ADDED -> 
                "Added ${log.creditsUsed} credits"
            UsageLogAction.API_KEY_CREATED -> 
                "Created new API key"
            UsageLogAction.API_KEY_USED -> 
                "Used API key for request"
            UsageLogAction.USER_REGISTERED -> 
                "User account registered"
            UsageLogAction.PLAN_UPGRADED -> 
                "Plan upgraded"
            UsageLogAction.PLAN_CHANGED -> 
                "Plan changed"
            UsageLogAction.PAYMENT_PROCESSED -> 
                "Payment processed successfully"
        }
    }
}
