package dev.screenshotapi.core.usecases.auth

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.UsageRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.usecases.common.UseCase
import kotlinx.datetime.*
import kotlin.math.roundToInt

/**
 * Request model for getting user usage timeline
 */
data class GetUserUsageTimelineRequest(
    val userId: String,
    val period: TimePeriod = TimePeriod.THIRTY_DAYS,
    val granularity: TimeGranularity = TimeGranularity.DAILY
)

/**
 * Response model for user usage timeline
 */
data class GetUserUsageTimelineResponse(
    val timeline: List<UsageTimelineEntry>,
    val summary: UsageTimelineSummary,
    val period: TimePeriod,
    val granularity: TimeGranularity
)

/**
 * Use case for retrieving user usage timeline data
 * Follows clean architecture principles and existing patterns
 */
class GetUserUsageTimelineUseCase(
    private val usageRepository: UsageRepository,
    private val userRepository: UserRepository
) : UseCase<GetUserUsageTimelineRequest, GetUserUsageTimelineResponse> {

    override suspend operator fun invoke(request: GetUserUsageTimelineRequest): GetUserUsageTimelineResponse {
        // Verify user exists
        userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User not found with id: ${request.userId}")

        // Calculate date range
        val today = Clock.System.todayIn(TimeZone.UTC)
        val startDate = today.minus(DatePeriod(days = request.period.days - 1)) // Include today
        
        // Get usage timeline data
        val timeline = usageRepository.getUsageTimeline(
            userId = request.userId,
            startDate = startDate,
            endDate = today,
            granularity = request.granularity
        )
        
        // Calculate summary statistics
        val summary = calculateSummary(timeline)
        
        return GetUserUsageTimelineResponse(
            timeline = timeline,
            summary = summary,
            period = request.period,
            granularity = request.granularity
        )
    }
    
    /**
     * Calculate summary statistics from timeline data
     */
    private fun calculateSummary(timeline: List<UsageTimelineEntry>): UsageTimelineSummary {
        if (timeline.isEmpty()) {
            return UsageTimelineSummary(
                totalScreenshots = 0,
                totalCreditsUsed = 0,
                totalApiCalls = 0,
                averageDaily = 0.0,
                successRate = 0.0,
                peakDay = null,
                peakDayScreenshots = 0
            )
        }
        
        val totalScreenshots = timeline.sumOf { it.screenshots }
        val totalCreditsUsed = timeline.sumOf { it.creditsUsed }
        val totalApiCalls = timeline.sumOf { it.apiCalls }
        val totalSuccessful = timeline.sumOf { it.successfulScreenshots }
        
        val averageDaily = if (timeline.isNotEmpty()) {
            totalScreenshots.toDouble() / timeline.size
        } else 0.0
        
        val successRate = if (totalScreenshots > 0) {
            (totalSuccessful.toDouble() / totalScreenshots) * 100
        } else 0.0
        
        // Find peak day
        val peakEntry = timeline.maxByOrNull { it.screenshots }
        
        return UsageTimelineSummary(
            totalScreenshots = totalScreenshots,
            totalCreditsUsed = totalCreditsUsed,
            totalApiCalls = totalApiCalls,
            averageDaily = (averageDaily * 100).roundToInt() / 100.0, // Round to 2 decimals
            successRate = (successRate * 100).roundToInt() / 100.0, // Round to 2 decimals
            peakDay = peakEntry?.date,
            peakDayScreenshots = peakEntry?.screenshots ?: 0
        )
    }
}