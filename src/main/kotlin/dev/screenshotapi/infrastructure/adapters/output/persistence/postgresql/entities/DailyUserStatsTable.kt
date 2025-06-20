package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Database table for daily user statistics
 * 
 * This table stores pre-aggregated daily statistics for efficient querying
 * and serves as the foundation for monthly and yearly aggregations.
 */
object DailyUserStatsTable : IdTable<String>("daily_user_stats") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)

    // Composite key fields
    val userId = reference("user_id", Users).index("idx_daily_stats_user_id")
    val date = date("date").index("idx_daily_stats_date")
    
    // Core screenshot metrics
    val screenshotsCreated = integer("screenshots_created").default(0)
    val screenshotsCompleted = integer("screenshots_completed").default(0)
    val screenshotsFailed = integer("screenshots_failed").default(0)
    val screenshotsRetried = integer("screenshots_retried").default(0)
    
    // Usage metrics
    val creditsUsed = integer("credits_used").default(0)
    val apiCallsCount = integer("api_calls_count").default(0)
    val apiKeysUsed = integer("api_keys_used").default(0)
    
    // Billing metrics
    val creditsAdded = integer("credits_added").default(0)
    val paymentsProcessed = integer("payments_processed").default(0)
    
    // Administrative metrics
    val apiKeysCreated = integer("api_keys_created").default(0)
    val planChanges = integer("plan_changes").default(0)
    
    // Metadata
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(1) // For optimistic locking
    
    // Indexes for efficient querying
    init {
        // Unique constraint on user_id + date (one record per user per day)
        uniqueIndex(customIndexName = "idx_daily_stats_user_date", columns = arrayOf(userId, date))
        
        // Index for date range queries
        index(customIndexName = "idx_daily_stats_user_date_range", columns = arrayOf(userId, date))
        
        // Index for finding users with activity on specific dates
        index(customIndexName = "idx_daily_stats_date_activity", columns = arrayOf(date, screenshotsCreated))
        
        // Index for aggregation queries by month
        index(customIndexName = "idx_daily_stats_user_month", columns = arrayOf(userId, date))
        
        // Index for data retention queries
        index(customIndexName = "idx_daily_stats_date_retention", columns = arrayOf(date))
    }
}

/**
 * Database table for monthly user statistics
 */
object MonthlyUserStatsTable : IdTable<String>("monthly_user_stats") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)

    val userId = reference("user_id", Users).index("idx_monthly_stats_user_id")
    val month = varchar("month", 7) // Format: "2025-01"
    
    // Aggregated metrics from daily stats
    val screenshotsCreated = integer("screenshots_created").default(0)
    val screenshotsCompleted = integer("screenshots_completed").default(0)
    val screenshotsFailed = integer("screenshots_failed").default(0)
    val screenshotsRetried = integer("screenshots_retried").default(0)
    val creditsUsed = integer("credits_used").default(0)
    val apiCallsCount = integer("api_calls_count").default(0)
    val creditsAdded = integer("credits_added").default(0)
    
    // Monthly specific metrics
    val peakDailyScreenshots = integer("peak_daily_screenshots").default(0)
    val activeDays = integer("active_days").default(0)
    
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(1)
    
    init {
        uniqueIndex(customIndexName = "idx_monthly_stats_user_month", columns = arrayOf(userId, month))
        index(customIndexName = "idx_monthly_stats_month", columns = arrayOf(month))
        index(customIndexName = "idx_monthly_stats_user_year", columns = arrayOf(userId, month))
    }
}

/**
 * Database table for yearly user statistics
 */
object YearlyUserStatsTable : IdTable<String>("yearly_user_stats") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)

    val userId = reference("user_id", Users).index("idx_yearly_stats_user_id")
    val year = integer("year")
    
    // Aggregated metrics from monthly stats
    val screenshotsCreated = integer("screenshots_created").default(0)
    val screenshotsCompleted = integer("screenshots_completed").default(0)
    val screenshotsFailed = integer("screenshots_failed").default(0)
    val screenshotsRetried = integer("screenshots_retried").default(0)
    val creditsUsed = integer("credits_used").default(0)
    val apiCallsCount = integer("api_calls_count").default(0)
    val creditsAdded = integer("credits_added").default(0)
    
    // Yearly specific metrics
    val peakMonthlyScreenshots = integer("peak_monthly_screenshots").default(0)
    val activeMonths = integer("active_months").default(0)
    
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(1)
    
    init {
        uniqueIndex(customIndexName = "idx_yearly_stats_user_year", columns = arrayOf(userId, year))
        index(customIndexName = "idx_yearly_stats_year", columns = arrayOf(year))
        index(customIndexName = "idx_yearly_stats_user", columns = arrayOf(userId))
    }
}