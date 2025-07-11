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

            // Use atomic increment operations to avoid optimistic locking conflicts
            val updatedStats = when (request.action) {
                UsageLogAction.SCREENSHOT_CREATED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.SCREENSHOTS_CREATED, 
                        1
                    )
                }
                
                UsageLogAction.SCREENSHOT_COMPLETED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.SCREENSHOTS_COMPLETED, 
                        1
                    )
                }
                
                UsageLogAction.SCREENSHOT_FAILED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.SCREENSHOTS_FAILED, 
                        1
                    )
                }
                
                UsageLogAction.SCREENSHOT_RETRIED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.SCREENSHOTS_RETRIED, 
                        1
                    )
                }
                
                UsageLogAction.CREDITS_DEDUCTED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.CREDITS_USED, 
                        request.creditsUsed
                    )
                }
                
                UsageLogAction.CREDITS_ADDED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.CREDITS_ADDED, 
                        request.creditsUsed
                    )
                }
                
                UsageLogAction.API_KEY_USED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.API_CALLS_COUNT, 
                        1
                    )
                }
                
                UsageLogAction.API_KEY_CREATED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.API_KEYS_CREATED, 
                        1
                    )
                }
                
                UsageLogAction.PLAN_UPGRADED, UsageLogAction.PLAN_CHANGED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.PLAN_CHANGES, 
                        1
                    )
                }
                
                UsageLogAction.PAYMENT_PROCESSED -> {
                    dailyStatsRepository.atomicIncrement(
                        request.userId, 
                        request.date, 
                        dev.screenshotapi.core.domain.repositories.StatsField.PAYMENTS_PROCESSED, 
                        1
                    )
                }
                
                UsageLogAction.USER_REGISTERED, UsageLogAction.EMAIL_SENT -> {
                    // These actions don't affect daily stats, return current state
                    dailyStatsRepository.findByUserAndDate(request.userId, request.date)
                }
            }

            // atomicIncrement already handles the database operations
            if (updatedStats != null) {
                logger.info("Successfully updated daily stats for user ${request.userId}: ${request.action}")
            } else {
                logger.debug("No daily stats update needed for user ${request.userId}: ${request.action}")
            }
            
            Response(
                success = true,
                updatedStats = updatedStats
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