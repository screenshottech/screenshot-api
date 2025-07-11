package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.EmailLog
import dev.screenshotapi.core.domain.entities.EmailType
import dev.screenshotapi.core.domain.entities.EmailCategory
import dev.screenshotapi.core.domain.entities.EmailPriority
import dev.screenshotapi.core.domain.repositories.*
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.EmailLogs
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.toEmailLog
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.toInsertStatement
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.dbQuery
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.slf4j.LoggerFactory
import kotlin.math.max

class PostgreSQLEmailLogRepository(private val database: Database) : EmailLogRepository {

    private val logger = LoggerFactory.getLogger(PostgreSQLEmailLogRepository::class.java)

    override suspend fun save(emailLog: EmailLog): EmailLog = dbQuery(database) {
        try {
            val existingLog = EmailLogs.select { EmailLogs.id eq emailLog.id }.singleOrNull()

            if (existingLog != null) {
                EmailLogs.update({ EmailLogs.id eq emailLog.id }, body = emailLog.toInsertStatement())
            } else {
                EmailLogs.insert(emailLog.toInsertStatement())
            }

            EmailLogs.select { EmailLogs.id eq emailLog.id }
                .single()
                .toEmailLog()
        } catch (e: Exception) {
            logger.error("Error saving email log: ${emailLog.id}", e)
            throw e
        }
    }

    override suspend fun findById(id: String): EmailLog? = dbQuery(database) {
        EmailLogs.select { EmailLogs.id eq id }
            .singleOrNull()
            ?.toEmailLog()
    }

    override suspend fun findByUserId(userId: String): List<EmailLog> = dbQuery(database) {
        EmailLogs.select { EmailLogs.userId eq userId }
            .orderBy(EmailLogs.sentAt, SortOrder.DESC)
            .map { it.toEmailLog() }
    }

    override suspend fun findByUserIdAndType(userId: String, emailType: EmailType): EmailLog? = dbQuery(database) {
        EmailLogs.select { (EmailLogs.userId eq userId) and (EmailLogs.emailType eq emailType.name) }
            .orderBy(EmailLogs.sentAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toEmailLog()
    }

    override suspend fun markAsOpened(emailId: String): Boolean = dbQuery(database) {
        try {
            val now = Clock.System.now()
            val updateCount = EmailLogs.update({ EmailLogs.id eq emailId }) {
                it[opened] = true
                it[openedAt] = now
                it[updatedAt] = now
            }
            updateCount > 0
        } catch (e: Exception) {
            logger.error("Error marking email as opened: $emailId", e)
            false
        }
    }

    override suspend fun markAsClicked(emailId: String): Boolean = dbQuery(database) {
        try {
            val now = Clock.System.now()

            // First get the current openedAt value
            val currentEmail = EmailLogs.select { EmailLogs.id eq emailId }.singleOrNull()
            val currentOpenedAt = currentEmail?.get(EmailLogs.openedAt)

            val updateCount = EmailLogs.update({ EmailLogs.id eq emailId }) {
                it[clicked] = true
                it[clickedAt] = now
                it[opened] = true // If clicked, it was also opened
                it[openedAt] = currentOpenedAt ?: now
                it[updatedAt] = now
            }
            updateCount > 0
        } catch (e: Exception) {
            logger.error("Error marking email as clicked: $emailId", e)
            false
        }
    }

    override suspend fun markAsBounced(emailId: String): Boolean = dbQuery(database) {
        try {
            val now = Clock.System.now()
            val updateCount = EmailLogs.update({ EmailLogs.id eq emailId }) {
                it[bounced] = true
                it[bouncedAt] = now
                it[updatedAt] = now
            }
            updateCount > 0
        } catch (e: Exception) {
            logger.error("Error marking email as bounced: $emailId", e)
            false
        }
    }

    override suspend fun markAsUnsubscribed(emailId: String): Boolean = dbQuery(database) {
        try {
            val now = Clock.System.now()
            val updateCount = EmailLogs.update({ EmailLogs.id eq emailId }) {
                it[unsubscribed] = true
                it[unsubscribedAt] = now
                it[updatedAt] = now
            }
            updateCount > 0
        } catch (e: Exception) {
            logger.error("Error marking email as unsubscribed: $emailId", e)
            false
        }
    }

    override suspend fun getEmailStats(userId: String): EmailStats = dbQuery(database) {
        val stats = EmailLogs.select { EmailLogs.userId eq userId }
            .fold(EmailStats.empty()) { acc, row ->
                EmailStats(
                    totalSent = acc.totalSent + 1,
                    totalOpened = acc.totalOpened + if (row[EmailLogs.opened]) 1 else 0,
                    totalClicked = acc.totalClicked + if (row[EmailLogs.clicked]) 1 else 0,
                    totalBounced = acc.totalBounced + if (row[EmailLogs.bounced]) 1 else 0,
                    totalUnsubscribed = acc.totalUnsubscribed + if (row[EmailLogs.unsubscribed]) 1 else 0,
                    openRate = 0.0, // Calculate below
                    clickRate = 0.0, // Calculate below
                    bounceRate = 0.0, // Calculate below
                    unsubscribeRate = 0.0, // Calculate below
                    engagementScore = 0.0 // Calculate below
                )
            }

        calculateRates(stats)
    }

    override suspend fun getEmailStatsByType(userId: String, emailType: EmailType): EmailTypeStats = dbQuery(database) {
        val logs = EmailLogs.select {
            (EmailLogs.userId eq userId) and (EmailLogs.emailType eq emailType.name)
        }.orderBy(EmailLogs.sentAt, SortOrder.DESC)

        val stats = logs.fold(EmailStats.empty()) { acc, row ->
            EmailStats(
                totalSent = acc.totalSent + 1,
                totalOpened = acc.totalOpened + if (row[EmailLogs.opened]) 1 else 0,
                totalClicked = acc.totalClicked + if (row[EmailLogs.clicked]) 1 else 0,
                totalBounced = acc.totalBounced + if (row[EmailLogs.bounced]) 1 else 0,
                totalUnsubscribed = acc.totalUnsubscribed + if (row[EmailLogs.unsubscribed]) 1 else 0,
                openRate = 0.0, // Calculate below
                clickRate = 0.0, // Calculate below
                bounceRate = 0.0, // Calculate below
                unsubscribeRate = 0.0, // Calculate below
                engagementScore = 0.0 // Calculate below
            )
        }

        val lastSentAt = logs.firstOrNull()?.let { it[EmailLogs.sentAt] }
        val timeBetweenSends = calculateAverageTimeBetweenSends(logs.map { it[EmailLogs.sentAt] })

        EmailTypeStats(
            emailType = emailType,
            stats = calculateRates(stats),
            lastSentAt = lastSentAt,
            averageTimeBetweenSends = timeBetweenSends
        )
    }

    override suspend fun getEmailStatsByPeriod(
        userId: String,
        startDate: Instant,
        endDate: Instant
    ): EmailStats = dbQuery(database) {
        val stats = EmailLogs.select {
            (EmailLogs.userId eq userId) and
            (EmailLogs.sentAt greater startDate) and
            (EmailLogs.sentAt less endDate)
        }.fold(EmailStats.empty()) { acc, row ->
            EmailStats(
                totalSent = acc.totalSent + 1,
                totalOpened = acc.totalOpened + if (row[EmailLogs.opened]) 1 else 0,
                totalClicked = acc.totalClicked + if (row[EmailLogs.clicked]) 1 else 0,
                totalBounced = acc.totalBounced + if (row[EmailLogs.bounced]) 1 else 0,
                totalUnsubscribed = acc.totalUnsubscribed + if (row[EmailLogs.unsubscribed]) 1 else 0,
                openRate = 0.0, // Calculate below
                clickRate = 0.0, // Calculate below
                bounceRate = 0.0, // Calculate below
                unsubscribeRate = 0.0, // Calculate below
                engagementScore = 0.0 // Calculate below
            )
        }

        calculateRates(stats)
    }

    override suspend fun findRecentEmailsByType(
        userId: String,
        emailType: EmailType,
        limit: Int
    ): List<EmailLog> = dbQuery(database) {
        EmailLogs.select {
            (EmailLogs.userId eq userId) and (EmailLogs.emailType eq emailType.name)
        }
            .orderBy(EmailLogs.sentAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toEmailLog() }
    }

    override suspend fun countEmailsSentToday(userId: String): Int = dbQuery(database) {
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.UTC).date.atStartOfDayIn(TimeZone.UTC)
        val tomorrow = today.plus(1.days)

        EmailLogs.select {
            (EmailLogs.userId eq userId) and
            (EmailLogs.sentAt greater today) and
            (EmailLogs.sentAt less tomorrow)
        }.count().toInt()
    }

    override suspend fun countEmailsSentInLastHour(userId: String): Int = dbQuery(database) {
        val oneHourAgo = Clock.System.now().minus(1.hours)
        EmailLogs.select {
            (EmailLogs.userId eq userId) and
            (EmailLogs.sentAt greater oneHourAgo)
        }.count().toInt()
    }

    override suspend fun findEmailsToCleanup(
        olderThan: Instant,
        limit: Int
    ): List<EmailLog> = dbQuery(database) {
        EmailLogs.select {
            (EmailLogs.sentAt less olderThan) and
            (EmailLogs.bounced eq true)
        }
            .orderBy(EmailLogs.sentAt, SortOrder.ASC)
            .limit(limit)
            .map { it.toEmailLog() }
    }

    override suspend fun deleteEmailLogs(emailIds: List<String>): Int = dbQuery(database) {
        if (emailIds.isEmpty()) return@dbQuery 0

        try {
            var deletedCount = 0
            for (emailId in emailIds) {
                deletedCount += EmailLogs.deleteWhere { EmailLogs.id eq emailId }
            }
            deletedCount
        } catch (e: Exception) {
            logger.error("Error deleting email logs: ${emailIds.size} emails", e)
            0
        }
    }

    override suspend fun getGlobalEmailStats(): GlobalEmailStats = dbQuery(database) {
        val totalSent = EmailLogs.selectAll().count()
        val uniqueUsers = EmailLogs.slice(EmailLogs.userId).selectAll().withDistinct().count()

        val avgEngagement = EmailLogs.selectAll().map { it.toEmailLog() }
            .map { it.getEngagementScore() }
            .average()

        // Get top performing email types
        val topTypes = EmailLogs.slice(EmailLogs.emailType, EmailLogs.id.count())
            .selectAll()
            .groupBy(EmailLogs.emailType)
            .orderBy(EmailLogs.id.count(), SortOrder.DESC)
            .limit(5)
            .map { row ->
                val type = EmailType.valueOf(row[EmailLogs.emailType])
                EmailTypePerformance(
                    emailType = type,
                    totalSent = row[EmailLogs.id.count()],
                    engagementScore = 0.0, // Would need more complex calculation
                    conversionRate = null // Would need conversion tracking
                )
            }

        GlobalEmailStats(
            totalEmailsSent = totalSent,
            totalUsersEmailed = uniqueUsers,
            averageEngagementScore = avgEngagement,
            topPerformingEmailTypes = topTypes,
            recentTrends = EmailTrends(
                dailyVolume = emptyList(), // Would need date aggregation
                weeklyEngagement = emptyList() // Would need date aggregation
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
    ): EmailLogPage = dbQuery(database) {
        val query = EmailLogs.selectAll()

        // Apply filters
        if (userId != null) {
            query.andWhere { EmailLogs.userId eq userId }
        }

        if (emailType != null) {
            query.andWhere { EmailLogs.emailType eq emailType.name }
        }

        if (category != null) {
            val typesInCategory = EmailType.getByCategory(category)
            query.andWhere { EmailLogs.emailType inList typesInCategory.map { it.name } }
        }

        if (priority != null) {
            val typesWithPriority = EmailType.getByPriority(priority)
            query.andWhere { EmailLogs.emailType inList typesWithPriority.map { it.name } }
        }

        if (startDate != null) {
            query.andWhere { EmailLogs.sentAt greater startDate }
        }

        if (endDate != null) {
            query.andWhere { EmailLogs.sentAt less endDate }
        }

        // Get total count
        val totalCount = query.count()

        // Apply pagination
        val offset = (page - 1) * limit
        val emails = query
            .orderBy(EmailLogs.sentAt, SortOrder.DESC)
            .limit(limit, offset.toLong())
            .map { it.toEmailLog() }

        EmailLogPage(
            emails = emails,
            totalCount = totalCount,
            page = page,
            limit = limit,
            hasNextPage = offset + limit < totalCount,
            hasPreviousPage = page > 1
        )
    }

    private fun calculateRates(stats: EmailStats): EmailStats {
        val totalSent = stats.totalSent.toDouble()
        if (totalSent == 0.0) return stats

        return stats.copy(
            openRate = (stats.totalOpened / totalSent) * 100,
            clickRate = (stats.totalClicked / totalSent) * 100,
            bounceRate = (stats.totalBounced / totalSent) * 100,
            unsubscribeRate = (stats.totalUnsubscribed / totalSent) * 100,
            engagementScore = ((stats.totalOpened * 50 + stats.totalClicked * 100) / totalSent) / 100
        )
    }

    private fun calculateAverageTimeBetweenSends(timestamps: List<Instant>): Long? {
        if (timestamps.size < 2) return null

        val intervals = timestamps.zipWithNext { a, b ->
            (b.epochSeconds - a.epochSeconds).coerceAtLeast(0)
        }

        return if (intervals.isNotEmpty()) {
            intervals.average().toLong()
        } else {
            null
        }
    }
}
