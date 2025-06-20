package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.usecases.stats.AggregateStatsUseCase
import dev.screenshotapi.core.domain.repositories.DailyStatsRepository
import kotlinx.coroutines.*
import kotlinx.datetime.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours

/**
 * Lightweight coroutines-based scheduler for statistics aggregation
 * 
 * This scheduler runs three main jobs:
 * 1. Daily aggregation (2:30 AM) - Aggregates yesterday's daily stats to monthly
 * 2. Monthly aggregation (3:00 AM on 1st) - Aggregates last month's data to yearly  
 * 3. Data cleanup (4:00 AM on 1st) - Removes old daily stats (90+ days)
 */
class StatsAggregationScheduler(
    private val aggregateStatsUseCase: AggregateStatsUseCase,
    private val dailyStatsRepository: DailyStatsRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var schedulerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start the scheduler
     */
    fun start() {
        if (schedulerJob?.isActive == true) {
            logger.warn("Stats aggregation scheduler is already running")
            return
        }
        
        logger.info("Starting stats aggregation scheduler...")
        
        schedulerJob = scope.launch {
            while (isActive) {
                try {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    
                    // Check each job type
                    checkDailyAggregation(now)
                    checkMonthlyAggregation(now)
                    checkDataCleanup(now)
                    
                    // Check every minute
                    delay(1.minutes)
                    
                } catch (e: Exception) {
                    logger.error("Error in stats aggregation scheduler", e)
                    delay(5.minutes) // Wait longer on error
                }
            }
        }
        
        logger.info("Stats aggregation scheduler started successfully")
    }
    
    /**
     * Stop the scheduler gracefully
     */
    fun stop() {
        logger.info("Stopping stats aggregation scheduler...")
        
        schedulerJob?.cancel()
        runBlocking {
            schedulerJob?.join()
        }
        
        logger.info("Stats aggregation scheduler stopped")
    }
    
    /**
     * Check if daily aggregation should run (2:30 AM every day)
     */
    private suspend fun checkDailyAggregation(now: LocalDateTime) {
        if (now.hour == 2 && now.minute >= 30 && now.minute < 35) {
            val yesterday = now.date.minus(1, DateTimeUnit.DAY)
            
            logger.info("⏰ Time for daily aggregation - processing date: $yesterday")
            
            try {
                val result = aggregateStatsUseCase.aggregateDailyToMonthly(
                    AggregateStatsUseCase.DailyToMonthlyRequest(
                        targetDate = yesterday,
                        forceRecalculation = false
                    )
                )
                
                if (result.success) {
                    logger.info(
                        "✅ Daily aggregation completed: " +
                        "users=${result.usersProcessed}, " +
                        "records=${result.recordsAggregated}, " +
                        "time=${result.processingTimeMs}ms"
                    )
                } else {
                    logger.error("❌ Daily aggregation failed: ${result.error}")
                }
                
                // Wait 5 minutes to avoid multiple executions
                delay(5.minutes)
                
            } catch (e: Exception) {
                logger.error("💥 Fatal error in daily aggregation", e)
            }
        }
    }
    
    /**
     * Check if monthly aggregation should run (3:00 AM on 1st of month)
     */
    private suspend fun checkMonthlyAggregation(now: LocalDateTime) {
        if (now.dayOfMonth == 1 && now.hour == 3 && now.minute >= 0 && now.minute < 5) {
            val lastMonth = now.date.minus(1, DateTimeUnit.MONTH)
            
            logger.info("⏰ Time for monthly aggregation - processing year: ${lastMonth.year}")
            
            try {
                val result = aggregateStatsUseCase.aggregateMonthlyToYearly(
                    AggregateStatsUseCase.MonthlyToYearlyRequest(
                        targetYear = lastMonth.year,
                        forceRecalculation = false
                    )
                )
                
                if (result.success) {
                    logger.info(
                        "✅ Monthly aggregation completed: " +
                        "users=${result.usersProcessed}, " +
                        "records=${result.recordsAggregated}, " +
                        "time=${result.processingTimeMs}ms"
                    )
                } else {
                    logger.error("❌ Monthly aggregation failed: ${result.error}")
                }
                
                // Wait 5 minutes to avoid multiple executions
                delay(5.minutes)
                
            } catch (e: Exception) {
                logger.error("💥 Fatal error in monthly aggregation", e)
            }
        }
    }
    
    /**
     * Check if data cleanup should run (4:00 AM on 1st of month)
     */
    private suspend fun checkDataCleanup(now: LocalDateTime) {
        if (now.dayOfMonth == 1 && now.hour == 4 && now.minute >= 0 && now.minute < 5) {
            val retentionDate = now.date.minus(90, DateTimeUnit.DAY)
            
            logger.info("⏰ Time for data cleanup - removing records older than: $retentionDate")
            
            try {
                val deletedRecords = dailyStatsRepository.deleteOlderThan(retentionDate)
                
                logger.info("🗑️ Data cleanup completed: deleted $deletedRecords daily stats records")
                
                // Wait 5 minutes to avoid multiple executions
                delay(5.minutes)
                
            } catch (e: Exception) {
                logger.error("💥 Fatal error in data cleanup", e)
            }
        }
    }
    
    /**
     * Manual trigger for daily aggregation (for testing/maintenance)
     */
    suspend fun triggerDailyAggregation(): Boolean {
        return try {
            logger.info("🔧 Manually triggering daily aggregation...")
            
            val yesterday = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.minus(1, DateTimeUnit.DAY)
            
            val result = aggregateStatsUseCase.aggregateDailyToMonthly(
                AggregateStatsUseCase.DailyToMonthlyRequest(
                    targetDate = yesterday,
                    forceRecalculation = true // Force when manually triggered
                )
            )
            
            if (result.success) {
                logger.info("✅ Manual daily aggregation completed successfully")
                true
            } else {
                logger.error("❌ Manual daily aggregation failed: ${result.error}")
                false
            }
            
        } catch (e: Exception) {
            logger.error("💥 Error in manual daily aggregation", e)
            false
        }
    }
    
    /**
     * Get scheduler status information
     */
    fun getStatus(): SchedulerStatus {
        return SchedulerStatus(
            isRunning = schedulerJob?.isActive == true,
            nextDailyAggregation = getNextDailyAggregationTime(),
            nextMonthlyAggregation = getNextMonthlyAggregationTime(),
            nextDataCleanup = getNextDataCleanupTime()
        )
    }
    
    private fun getNextDailyAggregationTime(): LocalDateTime {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val today230AM = LocalDateTime(now.year, now.month, now.dayOfMonth, 2, 30)
        
        return if (now < today230AM) {
            today230AM
        } else {
            today230AM.date.plus(1, DateTimeUnit.DAY).atTime(2, 30)
        }
    }
    
    private fun getNextMonthlyAggregationTime(): LocalDateTime {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val firstOf300AM = LocalDateTime(now.year, now.month, 1, 3, 0)
        
        return if (now.dayOfMonth == 1 && now < firstOf300AM) {
            firstOf300AM
        } else {
            firstOf300AM.date.plus(1, DateTimeUnit.MONTH).atTime(3, 0)
        }
    }
    
    private fun getNextDataCleanupTime(): LocalDateTime {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val firstOf400AM = LocalDateTime(now.year, now.month, 1, 4, 0)
        
        return if (now.dayOfMonth == 1 && now < firstOf400AM) {
            firstOf400AM
        } else {
            firstOf400AM.date.plus(1, DateTimeUnit.MONTH).atTime(4, 0)
        }
    }
    
    data class SchedulerStatus(
        val isRunning: Boolean,
        val nextDailyAggregation: LocalDateTime,
        val nextMonthlyAggregation: LocalDateTime,
        val nextDataCleanup: LocalDateTime
    )
}