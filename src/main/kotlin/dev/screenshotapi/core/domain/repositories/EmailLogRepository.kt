package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.EmailLog
import dev.screenshotapi.core.domain.entities.EmailType
import dev.screenshotapi.core.domain.entities.EmailCategory
import dev.screenshotapi.core.domain.entities.EmailPriority
import kotlinx.datetime.Instant

interface EmailLogRepository {
    suspend fun save(emailLog: EmailLog): EmailLog
    suspend fun findById(id: String): EmailLog?
    suspend fun findByUserId(userId: String): List<EmailLog>
    suspend fun findByUserIdAndType(userId: String, emailType: EmailType): EmailLog?
    suspend fun markAsOpened(emailId: String): Boolean
    suspend fun markAsClicked(emailId: String): Boolean
    suspend fun markAsBounced(emailId: String): Boolean
    suspend fun markAsUnsubscribed(emailId: String): Boolean
    suspend fun getEmailStats(userId: String): EmailStats
    suspend fun getEmailStatsByType(userId: String, emailType: EmailType): EmailTypeStats
    suspend fun getEmailStatsByPeriod(
        userId: String,
        startDate: Instant,
        endDate: Instant
    ): EmailStats
    suspend fun findRecentEmailsByType(
        userId: String,
        emailType: EmailType,
        limit: Int = 10
    ): List<EmailLog>
    suspend fun countEmailsSentToday(userId: String): Int
    suspend fun countEmailsSentInLastHour(userId: String): Int
    suspend fun findEmailsToCleanup(
        olderThan: Instant,
        limit: Int = 1000
    ): List<EmailLog>
    suspend fun deleteEmailLogs(emailIds: List<String>): Int
    suspend fun getGlobalEmailStats(): GlobalEmailStats
    suspend fun findEmailsWithPagination(
        userId: String? = null,
        emailType: EmailType? = null,
        category: EmailCategory? = null,
        priority: EmailPriority? = null,
        startDate: Instant? = null,
        endDate: Instant? = null,
        page: Int = 1,
        limit: Int = 50
    ): EmailLogPage
}

data class EmailStats(
    val totalSent: Int,
    val totalOpened: Int,
    val totalClicked: Int,
    val totalBounced: Int,
    val totalUnsubscribed: Int,
    val openRate: Double,
    val clickRate: Double,
    val bounceRate: Double,
    val unsubscribeRate: Double,
    val engagementScore: Double
) {
    companion object {
        fun empty(): EmailStats {
            return EmailStats(
                totalSent = 0,
                totalOpened = 0,
                totalClicked = 0,
                totalBounced = 0,
                totalUnsubscribed = 0,
                openRate = 0.0,
                clickRate = 0.0,
                bounceRate = 0.0,
                unsubscribeRate = 0.0,
                engagementScore = 0.0
            )
        }
    }
}

data class EmailTypeStats(
    val emailType: EmailType,
    val stats: EmailStats,
    val lastSentAt: Instant?,
    val averageTimeBetweenSends: Long? // in seconds
)

data class GlobalEmailStats(
    val totalEmailsSent: Long,
    val totalUsersEmailed: Long,
    val averageEngagementScore: Double,
    val topPerformingEmailTypes: List<EmailTypePerformance>,
    val recentTrends: EmailTrends
)

data class EmailTypePerformance(
    val emailType: EmailType,
    val totalSent: Long,
    val engagementScore: Double,
    val conversionRate: Double? = null
)

data class EmailTrends(
    val dailyVolume: List<DailyEmailVolume>,
    val weeklyEngagement: List<WeeklyEngagement>
)

data class DailyEmailVolume(
    val date: Instant,
    val totalSent: Int,
    val totalOpened: Int,
    val totalClicked: Int
)

data class WeeklyEngagement(
    val weekStart: Instant,
    val openRate: Double,
    val clickRate: Double,
    val engagementScore: Double
)

data class EmailLogPage(
    val emails: List<EmailLog>,
    val totalCount: Long,
    val page: Int,
    val limit: Int,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean
) {
    val totalPages: Int = ((totalCount + limit - 1) / limit).toInt()
}