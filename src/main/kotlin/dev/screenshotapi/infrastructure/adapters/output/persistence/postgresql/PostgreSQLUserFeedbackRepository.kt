package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.FeedbackType
import dev.screenshotapi.core.domain.entities.UserFeedback
import dev.screenshotapi.core.ports.output.DailySatisfaction
import dev.screenshotapi.core.ports.output.FeedbackStats
import dev.screenshotapi.core.ports.output.SatisfactionMetrics
import dev.screenshotapi.core.ports.output.UserFeedbackRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.UserFeedbacks
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.toInsertStatement
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.toUserFeedback
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

/**
 * PostgreSQL implementation of UserFeedbackRepository following established patterns
 */
class PostgreSQLUserFeedbackRepository(
    private val database: Database
) : UserFeedbackRepository {

    private val logger = LoggerFactory.getLogger(PostgreSQLUserFeedbackRepository::class.java)

    override suspend fun save(feedback: UserFeedback): UserFeedback = dbQuery(database) {
        logger.debug("Saving feedback: ${feedback.id} for user: ${feedback.userId}")
        
        try {
            val existingFeedback = UserFeedbacks.select { UserFeedbacks.id eq feedback.id }.singleOrNull()

            if (existingFeedback != null) {
                UserFeedbacks.update({ UserFeedbacks.id eq feedback.id }, body = feedback.toInsertStatement())
            } else {
                UserFeedbacks.insert(feedback.toInsertStatement())
            }

            UserFeedbacks.select { UserFeedbacks.id eq feedback.id }
                .single()
                .toUserFeedback()
                ?: throw DatabaseException.OperationFailed("Failed to retrieve saved feedback")
                
        } catch (e: Exception) {
            logger.error("Error saving feedback: ${feedback.id}", e)
            throw DatabaseException.OperationFailed("Failed to save feedback", e)
        }
    }

    override suspend fun findById(id: String): UserFeedback? = dbQuery(database) {
        logger.debug("Finding feedback by id: $id")
        
        UserFeedbacks.select { UserFeedbacks.id eq id }
            .singleOrNull()
            ?.toUserFeedback()
    }

    override suspend fun findAll(page: Int, size: Int): List<UserFeedback> = dbQuery(database) {
        logger.debug("Finding all feedback, page: $page, size: $size")
        
        UserFeedbacks.selectAll()
            .orderBy(UserFeedbacks.createdAt, SortOrder.DESC)
            .limit(size, offset = ((page - 1) * size).toLong())
            .mapNotNull { it.toUserFeedback() }
    }

    override suspend fun findByUserId(userId: String, page: Int, size: Int): List<UserFeedback> = dbQuery(database) {
        logger.debug("Finding feedback for user: $userId, page: $page, size: $size")
        
        UserFeedbacks.select { UserFeedbacks.userId eq userId }
            .orderBy(UserFeedbacks.createdAt, SortOrder.DESC)
            .limit(size, offset = ((page - 1) * size).toLong())
            .mapNotNull { it.toUserFeedback() }
    }

    override suspend fun findByUserIdAndStatus(
        userId: String, 
        status: FeedbackStatus, 
        page: Int, 
        size: Int
    ): List<UserFeedback> = dbQuery(database) {
        logger.debug("Finding feedback for user: $userId with status: $status")
        
        UserFeedbacks.select { 
            (UserFeedbacks.userId eq userId) and 
            (UserFeedbacks.status eq status.name) 
        }
        .orderBy(UserFeedbacks.createdAt, SortOrder.DESC)
        .limit(size, offset = ((page - 1) * size).toLong())
        .mapNotNull { it.toUserFeedback() }
    }

    override suspend fun findByType(feedbackType: FeedbackType, page: Int, size: Int): List<UserFeedback> = dbQuery(database) {
        logger.debug("Finding feedback by type: $feedbackType")
        
        UserFeedbacks.select { UserFeedbacks.feedbackType eq feedbackType.name }
            .orderBy(UserFeedbacks.createdAt, SortOrder.DESC)
            .limit(size, offset = ((page - 1) * size).toLong())
            .mapNotNull { it.toUserFeedback() }
    }

    override suspend fun findByStatus(status: FeedbackStatus, page: Int, size: Int): List<UserFeedback> = dbQuery(database) {
        logger.debug("Finding feedback by status: $status")
        
        UserFeedbacks.select { UserFeedbacks.status eq status.name }
            .orderBy(UserFeedbacks.createdAt, SortOrder.DESC)
            .limit(size, offset = ((page - 1) * size).toLong())
            .mapNotNull { it.toUserFeedback() }
    }

    override suspend fun findByDateRange(
        startDate: Instant, 
        endDate: Instant, 
        page: Int, 
        size: Int
    ): List<UserFeedback> = dbQuery(database) {
        logger.debug("Finding feedback between: $startDate and $endDate")
        
        UserFeedbacks.select { 
            UserFeedbacks.createdAt.between(startDate, endDate) 
        }
        .orderBy(UserFeedbacks.createdAt, SortOrder.DESC)
        .limit(size, offset = ((page - 1) * size).toLong())
        .mapNotNull { it.toUserFeedback() }
    }

    override suspend fun findCriticalFeedback(page: Int, size: Int): List<UserFeedback> = dbQuery(database) {
        logger.debug("Finding critical feedback")
        
        UserFeedbacks.select { 
            (UserFeedbacks.feedbackType inList FeedbackType.getCriticalTypes().map { it.name }) or
            (UserFeedbacks.rating lessEq 2)
        }
        .andWhere { UserFeedbacks.status neq FeedbackStatus.RESOLVED.name }
        .orderBy(UserFeedbacks.createdAt, SortOrder.DESC)
        .limit(size, offset = ((page - 1) * size).toLong())
        .mapNotNull { it.toUserFeedback() }
    }

    override suspend fun updateStatus(
        feedbackId: String, 
        status: FeedbackStatus, 
        adminId: String?, 
        adminNotes: String?
    ): UserFeedback? = dbQuery(database) {
        logger.debug("Updating feedback status: $feedbackId to $status")
        
        val now = Clock.System.now()
        
        val updated = UserFeedbacks.update({ UserFeedbacks.id eq feedbackId }) {
            it[UserFeedbacks.status] = status.name
            it[updatedAt] = now
            if (adminNotes != null) {
                it[UserFeedbacks.adminNotes] = adminNotes
            }
            if (status.isResolved && adminId != null) {
                it[resolvedBy] = adminId
                it[resolvedAt] = now
            }
        }
        
        if (updated > 0) {
            findById(feedbackId)
        } else {
            null
        }
    }

    override suspend fun delete(id: String): Boolean = dbQuery(database) {
        logger.debug("Deleting feedback: $id")
        
        val deleted = UserFeedbacks.deleteWhere { UserFeedbacks.id eq id }
        deleted > 0
    }

    override suspend fun count(): Long = dbQuery(database) {
        UserFeedbacks.selectAll().count()
    }

    override suspend fun countByUserId(userId: String): Long = dbQuery(database) {
        UserFeedbacks.select { UserFeedbacks.userId eq userId }.count()
    }

    override suspend fun countByStatus(status: FeedbackStatus): Long = dbQuery(database) {
        UserFeedbacks.select { UserFeedbacks.status eq status.name }.count()
    }

    override suspend fun countByType(feedbackType: FeedbackType): Long = dbQuery(database) {
        UserFeedbacks.select { UserFeedbacks.feedbackType eq feedbackType.name }.count()
    }

    override suspend fun getFeedbackStats(): FeedbackStats = dbQuery(database) {
        logger.debug("Generating feedback statistics")

        val totalFeedback = UserFeedbacks.selectAll().count()
        val pendingFeedback = UserFeedbacks.select { UserFeedbacks.status eq FeedbackStatus.PENDING.name }.count()
        val resolvedFeedback = FeedbackStatus.getResolvedStatuses().sumOf { status ->
            UserFeedbacks.select { UserFeedbacks.status eq status.name }.count()
        }
        
        // Critical feedback count
        val criticalFeedback = UserFeedbacks.select { 
            (UserFeedbacks.feedbackType inList FeedbackType.getCriticalTypes().map { it.name }) or
            (UserFeedbacks.rating lessEq 2)
        }
        .andWhere { UserFeedbacks.status neq FeedbackStatus.RESOLVED.name }
        .count()

        // Average rating
        val avgRating = UserFeedbacks
            .slice(UserFeedbacks.rating.avg())
            .select { UserFeedbacks.rating.isNotNull() }
            .singleOrNull()
            ?.get(UserFeedbacks.rating.avg())
            ?.toDouble()

        // Feedback by type
        val feedbackByType = FeedbackType.values().associateWith { type ->
            UserFeedbacks.select { UserFeedbacks.feedbackType eq type.name }.count()
        }

        // Feedback by status  
        val feedbackByStatus = FeedbackStatus.values().associateWith { status ->
            UserFeedbacks.select { UserFeedbacks.status eq status.name }.count()
        }

        // Recent trends (last 7 days) - using kotlinx.datetime
        val recentTrends = mutableMapOf<String, Long>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        for (i in 0..6) {
            val date = today.minus(i, DateTimeUnit.DAY)
            val startOfDay = date.atStartOfDayIn(TimeZone.UTC)
            val endOfDay = date.atTime(23, 59, 59).toInstant(TimeZone.UTC)
            
            val count = UserFeedbacks.select { 
                UserFeedbacks.createdAt.between(startOfDay, endOfDay)
            }.count()
            
            recentTrends[date.toString()] = count
        }

        FeedbackStats(
            totalFeedback = totalFeedback,
            pendingFeedback = pendingFeedback,
            resolvedFeedback = resolvedFeedback,
            criticalFeedback = criticalFeedback,
            averageRating = avgRating,
            feedbackByType = feedbackByType,
            feedbackByStatus = feedbackByStatus,
            recentTrends = recentTrends
        )
    }

    override suspend fun getSatisfactionMetrics(
        feedbackType: FeedbackType?, 
        days: Int
    ): SatisfactionMetrics = dbQuery(database) {
        logger.debug("Generating satisfaction metrics for type: $feedbackType, days: $days")

        val sinceDate = Clock.System.now().minus(days, DateTimeUnit.DAY, TimeZone.UTC)
        
        var query = UserFeedbacks.select { 
            (UserFeedbacks.rating.isNotNull()) and
            (UserFeedbacks.createdAt greaterEq sinceDate)
        }

        if (feedbackType != null) {
            query = query.andWhere { UserFeedbacks.feedbackType eq feedbackType.name }
        }

        val ratings = query.map { it[UserFeedbacks.rating]!! }
        
        val averageRating = if (ratings.isNotEmpty()) ratings.average() else null
        val totalRatings = ratings.size.toLong()
        
        // Rating distribution
        val ratingDistribution = ratings.groupingBy { it }.eachCount().mapValues { it.value.toLong() }
        
        // Daily satisfaction trend
        val satisfactionTrend = mutableListOf<DailySatisfaction>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        
        for (i in 0 until days) {
            val date = today.minus(i, DateTimeUnit.DAY)
            val startOfDay = date.atStartOfDayIn(TimeZone.UTC)
            val endOfDay = date.atTime(23, 59, 59).toInstant(TimeZone.UTC)
            
            var dailyQuery = UserFeedbacks.select { 
                (UserFeedbacks.rating.isNotNull()) and
                (UserFeedbacks.createdAt.between(startOfDay, endOfDay))
            }
            
            if (feedbackType != null) {
                dailyQuery = dailyQuery.andWhere { UserFeedbacks.feedbackType eq feedbackType.name }
            }
            
            val dailyRatings = dailyQuery.map { it[UserFeedbacks.rating]!! }
            val dailyAverage = if (dailyRatings.isNotEmpty()) dailyRatings.average() else null
            
            satisfactionTrend.add(DailySatisfaction(
                date = date.toString(),
                averageRating = dailyAverage,
                totalRatings = dailyRatings.size.toLong()
            ))
        }

        // Simple NPS calculation (ratings 4-5 are promoters, 1-2 are detractors)
        val npsScore = if (ratings.isNotEmpty()) {
            val promoters = ratings.count { it >= 4 }
            val detractors = ratings.count { it <= 2 }
            ((promoters - detractors).toDouble() / ratings.size) * 100
        } else null

        SatisfactionMetrics(
            averageRating = averageRating,
            totalRatings = totalRatings,
            ratingDistribution = ratingDistribution,
            satisfactionTrend = satisfactionTrend.reversed(), // Oldest first
            npsScore = npsScore
        )
    }
}