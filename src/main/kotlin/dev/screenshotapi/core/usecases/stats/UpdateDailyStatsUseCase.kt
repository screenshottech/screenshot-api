package dev.screenshotapi.core.usecases.stats

import dev.screenshotapi.core.domain.entities.DailyUserStats
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.repositories.DailyStatsRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

/**
 * Use case for updating daily user statistics in real-time
 * 
 * This use case is called whenever a usage log entry is created to maintain
 * up-to-date aggregated statistics for efficient querying.
 */
class UpdateDailyStatsUseCase(
    private val dailyStatsRepository: DailyStatsRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    data class Request(
        val userId: String,
        val action: UsageLogAction,
        val date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
        val creditsUsed: Int = 0,
        val metadata: Map<String, String> = emptyMap()
    )

    data class Response(
        val success: Boolean,
        val updatedStats: DailyUserStats?,
        val error: String? = null
    )

    suspend fun invoke(request: Request): Response {
        return try {
            logger.debug("Updating daily stats for user ${request.userId}, action ${request.action}, date ${request.date}")

            // Get or create daily stats for the user and date
            val currentStats = dailyStatsRepository.findByUserAndDate(request.userId, request.date)
                ?: DailyUserStats.createEmpty(request.userId, request.date)

            // Update stats based on the action
            val updatedStats = when (request.action) {
                UsageLogAction.SCREENSHOT_CREATED -> {
                    currentStats.incrementScreenshotsCreated()
                }
                
                UsageLogAction.SCREENSHOT_COMPLETED -> {
                    currentStats.incrementScreenshotsCompleted()
                }
                
                UsageLogAction.SCREENSHOT_FAILED -> {
                    currentStats.incrementScreenshotsFailed()
                }
                
                UsageLogAction.SCREENSHOT_RETRIED -> {
                    currentStats.incrementScreenshotsRetried()
                }
                
                UsageLogAction.CREDITS_DEDUCTED -> {
                    currentStats.incrementCreditsUsed(request.creditsUsed)
                }
                
                UsageLogAction.CREDITS_ADDED -> {
                    currentStats.incrementCreditsAdded(request.creditsUsed)
                }
                
                UsageLogAction.API_KEY_USED -> {
                    currentStats.incrementApiCalls()
                }
                
                UsageLogAction.API_KEY_CREATED -> {
                    currentStats.copy(
                        apiKeysCreated = currentStats.apiKeysCreated + 1,
                        updatedAt = Clock.System.now(),
                        version = currentStats.version + 1
                    )
                }
                
                UsageLogAction.PLAN_UPGRADED, UsageLogAction.PLAN_CHANGED -> {
                    currentStats.copy(
                        planChanges = currentStats.planChanges + 1,
                        updatedAt = Clock.System.now(),
                        version = currentStats.version + 1
                    )
                }
                
                UsageLogAction.PAYMENT_PROCESSED -> {
                    currentStats.copy(
                        paymentsProcessed = currentStats.paymentsProcessed + 1,
                        updatedAt = Clock.System.now(),
                        version = currentStats.version + 1
                    )
                }
                
                UsageLogAction.USER_REGISTERED -> {
                    // User registration doesn't affect daily stats for screenshots/credits
                    currentStats
                }
            }

            // Save the updated stats using atomic operations
            val savedStats = if (currentStats.createdAt == currentStats.updatedAt) {
                // This is a new record, create it
                dailyStatsRepository.create(updatedStats)
            } else {
                // This is an update, use atomic increment
                dailyStatsRepository.atomicUpdate(updatedStats)
            }

            logger.info("Successfully updated daily stats for user ${request.userId}: ${request.action}")
            
            Response(
                success = true,
                updatedStats = savedStats
            )
            
        } catch (e: Exception) {
            logger.error("Failed to update daily stats for user ${request.userId}", e)
            Response(
                success = false,
                updatedStats = null,
                error = "Failed to update daily statistics: ${e.message}"
            )
        }
    }

    /**
     * Batch update multiple stats for efficiency
     */
    suspend fun batchUpdate(requests: List<Request>): List<Response> {
        return requests.map { request ->
            invoke(request)
        }
    }

    /**
     * Get current daily stats for a user (for dashboard)
     */
    suspend fun getCurrentDailyStats(userId: String): DailyUserStats? {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        return dailyStatsRepository.findByUserAndDate(userId, today)
    }

    /**
     * Get daily stats for a date range (for analytics)
     */
    suspend fun getDailyStatsRange(
        userId: String, 
        startDate: LocalDate, 
        endDate: LocalDate
    ): List<DailyUserStats> {
        return dailyStatsRepository.findByUserAndDateRange(userId, startDate, endDate)
    }
}