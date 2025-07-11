package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.EmailLog
import dev.screenshotapi.core.domain.entities.EmailType
import dev.screenshotapi.core.domain.entities.EmailCategory
import dev.screenshotapi.core.domain.entities.EmailPriority
import dev.screenshotapi.core.domain.repositories.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of EmailLogRepository for testing and development
 */
class InMemoryEmailLogRepository : EmailLogRepository {
    private val emailLogs = ConcurrentHashMap<String, EmailLog>()
    
    override suspend fun save(emailLog: EmailLog): EmailLog {
        emailLogs[emailLog.id] = emailLog
        return emailLog
    }
    
    override suspend fun findById(id: String): EmailLog? {
        return emailLogs[id]
    }
    
    override suspend fun findByUserId(userId: String): List<EmailLog> {
        return emailLogs.values
            .filter { it.userId == userId }
            .sortedByDescending { it.sentAt }
    }
    
    override suspend fun findByUserIdAndType(userId: String, emailType: EmailType): EmailLog? {
        return emailLogs.values
            .filter { it.userId == userId && it.emailType == emailType }
            .maxByOrNull { it.sentAt }
    }
    
    override suspend fun markAsOpened(emailId: String): Boolean {
        val emailLog = emailLogs[emailId] ?: return false
        emailLogs[emailId] = emailLog.markAsOpened()
        return true
    }
    
    override suspend fun markAsClicked(emailId: String): Boolean {
        val emailLog = emailLogs[emailId] ?: return false
        emailLogs[emailId] = emailLog.markAsClicked()
        return true
    }
    
    override suspend fun markAsBounced(emailId: String): Boolean {
        val emailLog = emailLogs[emailId] ?: return false
        emailLogs[emailId] = emailLog.markAsBounced()
        return true
    }
    
    override suspend fun markAsUnsubscribed(emailId: String): Boolean {
        val emailLog = emailLogs[emailId] ?: return false
        emailLogs[emailId] = emailLog.markAsUnsubscribed()
        return true
    }
    
    override suspend fun getEmailStats(userId: String): EmailStats {
        val userEmails = emailLogs.values.filter { it.userId == userId }
        return calculateStats(userEmails)
    }
    
    override suspend fun getEmailStatsByType(userId: String, emailType: EmailType): EmailTypeStats {
        val userEmails = emailLogs.values.filter { it.userId == userId && it.emailType == emailType }
        val stats = calculateStats(userEmails)
        val lastSentAt = userEmails.maxByOrNull { it.sentAt }?.sentAt
        val averageTimeBetweenSends = calculateAverageTimeBetweenSends(userEmails.map { it.sentAt })
        
        return EmailTypeStats(
            emailType = emailType,
            stats = stats,
            lastSentAt = lastSentAt,
            averageTimeBetweenSends = averageTimeBetweenSends
        )
    }
    
    override suspend fun getEmailStatsByPeriod(
        userId: String,
        startDate: Instant,
        endDate: Instant
    ): EmailStats {
        val userEmails = emailLogs.values.filter { 
            it.userId == userId && 
            it.sentAt >= startDate && 
            it.sentAt <= endDate 
        }
        return calculateStats(userEmails)
    }
    
    override suspend fun findRecentEmailsByType(
        userId: String,
        emailType: EmailType,
        limit: Int
    ): List<EmailLog> {
        return emailLogs.values
            .filter { it.userId == userId && it.emailType == emailType }
            .sortedByDescending { it.sentAt }
            .take(limit)
    }
    
    override suspend fun countEmailsSentToday(userId: String): Int {
        val today = Clock.System.now()
        val startOfDay = Instant.fromEpochSeconds(today.epochSeconds - (today.epochSeconds % 86400))
        val endOfDay = Instant.fromEpochSeconds(startOfDay.epochSeconds + 86400)
        
        return emailLogs.values.count { 
            it.userId == userId && 
            it.sentAt >= startOfDay && 
            it.sentAt < endOfDay 
        }
    }
    
    override suspend fun countEmailsSentInLastHour(userId: String): Int {
        val oneHourAgo = Clock.System.now().minus(1.hours)
        
        return emailLogs.values.count { 
            it.userId == userId && 
            it.sentAt >= oneHourAgo 
        }
    }
    
    override suspend fun findEmailsToCleanup(
        olderThan: Instant,
        limit: Int
    ): List<EmailLog> {
        return emailLogs.values
            .filter { it.sentAt < olderThan && it.bounced }
            .sortedBy { it.sentAt }
            .take(limit)
    }
    
    override suspend fun deleteEmailLogs(emailIds: List<String>): Int {
        var deletedCount = 0
        emailIds.forEach { id ->
            if (emailLogs.remove(id) != null) {
                deletedCount++
            }
        }
        return deletedCount
    }
    
    override suspend fun getGlobalEmailStats(): GlobalEmailStats {
        val allEmails = emailLogs.values.toList()
        val uniqueUsers = allEmails.map { it.userId }.distinct().size.toLong()
        val avgEngagement = allEmails.map { it.getEngagementScore() }.average()
        
        val topTypes = allEmails.groupBy { it.emailType }
            .map { (type, emails) ->
                EmailTypePerformance(
                    emailType = type,
                    totalSent = emails.size.toLong(),
                    engagementScore = emails.map { it.getEngagementScore() }.average(),
                    conversionRate = null
                )
            }
            .sortedByDescending { it.totalSent }
            .take(5)
        
        return GlobalEmailStats(
            totalEmailsSent = allEmails.size.toLong(),
            totalUsersEmailed = uniqueUsers,
            averageEngagementScore = avgEngagement,
            topPerformingEmailTypes = topTypes,
            recentTrends = EmailTrends(
                dailyVolume = emptyList(),
                weeklyEngagement = emptyList()
            )
        )
    }
    
    override suspend fun findEmailsWithPagination(
        userId: String?,
        emailType: EmailType?,
        category: EmailCategory?,
        priority: EmailPriority?,
        startDate: Instant?,
        endDate: Instant?,
        page: Int,
        limit: Int
    ): EmailLogPage {
        var filteredEmails = emailLogs.values.toList()
        
        // Apply filters
        if (userId != null) {
            filteredEmails = filteredEmails.filter { it.userId == userId }
        }
        if (emailType != null) {
            filteredEmails = filteredEmails.filter { it.emailType == emailType }
        }
        if (category != null) {
            filteredEmails = filteredEmails.filter { it.emailType.category == category }
        }
        if (priority != null) {
            filteredEmails = filteredEmails.filter { it.emailType.priority == priority }
        }
        if (startDate != null) {
            filteredEmails = filteredEmails.filter { it.sentAt >= startDate }
        }
        if (endDate != null) {
            filteredEmails = filteredEmails.filter { it.sentAt <= endDate }
        }
        
        // Sort by sent date descending
        filteredEmails = filteredEmails.sortedByDescending { it.sentAt }
        
        // Apply pagination
        val totalCount = filteredEmails.size.toLong()
        val offset = (page - 1) * limit
        val paginatedEmails = filteredEmails.drop(offset).take(limit)
        
        return EmailLogPage(
            emails = paginatedEmails,
            totalCount = totalCount,
            page = page,
            limit = limit,
            hasNextPage = offset + limit < totalCount,
            hasPreviousPage = page > 1
        )
    }
    
    private fun calculateStats(emails: List<EmailLog>): EmailStats {
        if (emails.isEmpty()) return EmailStats.empty()
        
        val totalSent = emails.size
        val totalOpened = emails.count { it.opened }
        val totalClicked = emails.count { it.clicked }
        val totalBounced = emails.count { it.bounced }
        val totalUnsubscribed = emails.count { it.unsubscribed }
        
        val openRate = if (totalSent > 0) (totalOpened.toDouble() / totalSent) * 100 else 0.0
        val clickRate = if (totalSent > 0) (totalClicked.toDouble() / totalSent) * 100 else 0.0
        val bounceRate = if (totalSent > 0) (totalBounced.toDouble() / totalSent) * 100 else 0.0
        val unsubscribeRate = if (totalSent > 0) (totalUnsubscribed.toDouble() / totalSent) * 100 else 0.0
        val engagementScore = if (totalSent > 0) ((totalOpened * 50 + totalClicked * 100).toDouble() / totalSent) / 100 else 0.0
        
        return EmailStats(
            totalSent = totalSent,
            totalOpened = totalOpened,
            totalClicked = totalClicked,
            totalBounced = totalBounced,
            totalUnsubscribed = totalUnsubscribed,
            openRate = openRate,
            clickRate = clickRate,
            bounceRate = bounceRate,
            unsubscribeRate = unsubscribeRate,
            engagementScore = engagementScore
        )
    }
    
    private fun calculateAverageTimeBetweenSends(timestamps: List<Instant>): Long? {
        if (timestamps.size < 2) return null
        
        val sortedTimestamps = timestamps.sorted()
        val intervals = sortedTimestamps.zipWithNext { a, b ->
            (b.epochSeconds - a.epochSeconds).coerceAtLeast(0)
        }
        
        return if (intervals.isNotEmpty()) {
            intervals.average().toLong()
        } else {
            null
        }
    }
}