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
            UsageLogAction.SCREENSHOT_RETRIED -> UserActivityType.SCREENSHOT_CREATED // Treat retry as creation activity
            UsageLogAction.API_KEY_CREATED -> UserActivityType.API_KEY_CREATED
            UsageLogAction.API_KEY_USED -> UserActivityType.LOGIN // API usage as activity
            UsageLogAction.USER_REGISTERED -> UserActivityType.LOGIN // Registration as first login
            UsageLogAction.PLAN_UPGRADED -> UserActivityType.PLAN_CHANGED
            UsageLogAction.PLAN_CHANGED -> UserActivityType.PLAN_CHANGED
            UsageLogAction.PAYMENT_PROCESSED -> UserActivityType.PAYMENT_SUCCESS
            UsageLogAction.CREDITS_DEDUCTED -> UserActivityType.SCREENSHOT_CREATED // Credit usage implies screenshot
            UsageLogAction.CREDITS_ADDED -> UserActivityType.CREDITS_PROVISIONED_ADMIN
            UsageLogAction.EMAIL_SENT -> UserActivityType.LOGIN // Email activity as engagement
            // OCR Actions
            UsageLogAction.OCR_CREATED -> UserActivityType.SCREENSHOT_CREATED // Treat OCR as similar to screenshot
            UsageLogAction.OCR_COMPLETED -> UserActivityType.SCREENSHOT_COMPLETED
            UsageLogAction.OCR_FAILED -> UserActivityType.SCREENSHOT_FAILED
            UsageLogAction.OCR_PRICE_EXTRACTION -> UserActivityType.SCREENSHOT_COMPLETED // Specialized OCR operation
            
            // AI Analysis Actions (NEW - Separate Flow)
            UsageLogAction.AI_ANALYSIS_STARTED -> UserActivityType.SCREENSHOT_CREATED // Analysis start as creation activity
            UsageLogAction.AI_ANALYSIS_COMPLETED -> UserActivityType.SCREENSHOT_COMPLETED // Analysis completion
            UsageLogAction.AI_ANALYSIS_FAILED -> UserActivityType.SCREENSHOT_FAILED // Analysis failure
            UsageLogAction.AI_ANALYSIS_CREDITS_DEDUCTED -> UserActivityType.SCREENSHOT_CREATED // Credit usage implies analysis activity
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
            UsageLogAction.SCREENSHOT_RETRIED ->
                "Retried failed screenshot"
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
            UsageLogAction.EMAIL_SENT ->
                "Email notification sent"
            // OCR Actions
            UsageLogAction.OCR_CREATED ->
                "Created OCR extraction request"
            UsageLogAction.OCR_COMPLETED ->
                "OCR extraction completed successfully"
            UsageLogAction.OCR_FAILED ->
                "OCR extraction failed"
            UsageLogAction.OCR_PRICE_EXTRACTION ->
                "Performed price extraction analysis"
                
            // AI Analysis Actions (NEW - Separate Flow)
            UsageLogAction.AI_ANALYSIS_STARTED ->
                "Started AI analysis of screenshot"
            UsageLogAction.AI_ANALYSIS_COMPLETED ->
                "AI analysis completed successfully"
            UsageLogAction.AI_ANALYSIS_FAILED ->
                "AI analysis failed"
            UsageLogAction.AI_ANALYSIS_CREDITS_DEDUCTED ->
                "Used ${log.creditsUsed} credits for AI analysis"
        }
    }
}
