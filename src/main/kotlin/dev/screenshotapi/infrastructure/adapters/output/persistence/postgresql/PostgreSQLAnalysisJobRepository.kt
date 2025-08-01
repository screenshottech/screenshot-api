package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.AnalysisStatus
import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.repositories.AnalysisJobRepository
import dev.screenshotapi.core.domain.repositories.AnalysisStats
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.AnalysisJobs
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.dbQuery
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.slf4j.LoggerFactory

/**
 * PostgreSQL Analysis Job Repository
 *
 * Handles persistence of AI analysis jobs with proper transaction management
 * following the established dbQuery pattern.
 */
class PostgreSQLAnalysisJobRepository(
    private val database: Database
) : AnalysisJobRepository {

    private val logger = LoggerFactory.getLogger(PostgreSQLAnalysisJobRepository::class.java)

    override suspend fun save(job: AnalysisJob): AnalysisJob = dbQuery(database) {
        try {
            // Use atomic upsert to handle potential duplicates
            AnalysisJobs.upsert(
                keys = arrayOf(AnalysisJobs.id)
            ) { stmt ->
                stmt[AnalysisJobs.id] = job.id
                fillStatement(stmt, job)
            }
            logger.debug("Saved analysis job: ${job.id}")
            job
        } catch (e: Exception) {
            logger.error("Error saving analysis job: ${job.id}", e)
            throw DatabaseException.OperationFailed("Failed to save analysis job", e)
        }
    }

    override suspend fun findById(id: String): AnalysisJob? = dbQuery(database) {
        try {
            AnalysisJobs.select { AnalysisJobs.id eq id }
                .singleOrNull()
                ?.let { mapToAnalysisJob(it) }
        } catch (e: Exception) {
            logger.error("Error finding analysis job by ID: $id", e)
            null
        }
    }

    override suspend fun findByIdAndUserId(id: String, userId: String): AnalysisJob? = dbQuery(database) {
        try {
            AnalysisJobs.select {
                (AnalysisJobs.id eq id) and (AnalysisJobs.userId eq userId)
            }.singleOrNull()?.let { mapToAnalysisJob(it) }
        } catch (e: Exception) {
            logger.error("Error finding analysis job by ID and user: $id, $userId", e)
            null
        }
    }

    override suspend fun findByScreenshotJobId(screenshotJobId: String): List<AnalysisJob> = dbQuery(database) {
        try {
            AnalysisJobs.select { AnalysisJobs.screenshotJobId eq screenshotJobId }
                .orderBy(AnalysisJobs.createdAt, SortOrder.DESC)
                .map { mapToAnalysisJob(it) }
        } catch (e: Exception) {
            logger.error("Error finding analysis jobs by screenshot ID: $screenshotJobId", e)
            emptyList()
        }
    }

    override suspend fun findByUserId(
        userId: String,
        page: Int,
        limit: Int,
        status: AnalysisStatus?
    ): List<AnalysisJob> = dbQuery(database) {
        try {
            val query = if (status != null) {
                AnalysisJobs.select {
                    (AnalysisJobs.userId eq userId) and (AnalysisJobs.status eq status.name)
                }
            } else {
                AnalysisJobs.select { AnalysisJobs.userId eq userId }
            }

            query.orderBy(AnalysisJobs.createdAt, SortOrder.DESC)
                .limit(limit, offset = ((page - 1) * limit).toLong())
                .map { mapToAnalysisJob(it) }
        } catch (e: Exception) {
            logger.error("Error finding analysis jobs by user: $userId", e)
            emptyList()
        }
    }

    override suspend fun countByUserId(userId: String, status: AnalysisStatus?): Long = dbQuery(database) {
        try {
            val query = if (status != null) {
                AnalysisJobs.select {
                    (AnalysisJobs.userId eq userId) and (AnalysisJobs.status eq status.name)
                }
            } else {
                AnalysisJobs.select { AnalysisJobs.userId eq userId }
            }

            query.count()
        } catch (e: Exception) {
            logger.error("Error counting analysis jobs for user: $userId", e)
            0L
        }
    }

    override suspend fun findNextQueuedJob(): AnalysisJob? = dbQuery(database) {
        try {
            // Simple but effective approach: Find and immediately try to claim
            // The key is to check the status in the UPDATE condition
            val job = AnalysisJobs.select { AnalysisJobs.status eq AnalysisStatus.QUEUED.name }
                .orderBy(AnalysisJobs.createdAt, SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.let { mapToAnalysisJob(it) }
            
            if (job != null) {
                val now = Clock.System.now()
                
                // Atomic update: only succeeds if status is still QUEUED
                val updatedRows = AnalysisJobs.update({
                    (AnalysisJobs.id eq job.id) and (AnalysisJobs.status eq AnalysisStatus.QUEUED.name)
                }) { stmt ->
                    stmt[status] = AnalysisStatus.PROCESSING.name
                    stmt[startedAt] = now
                    stmt[updatedAt] = now
                }
                
                if (updatedRows > 0) {
                    // Successfully claimed the job
                    logger.debug("Successfully claimed analysis job: ${job.id}")
                    job.copy(
                        status = AnalysisStatus.PROCESSING,
                        startedAt = now,
                        updatedAt = now
                    )
                } else {
                    // Job was claimed by another worker, try again
                    logger.debug("Job ${job.id} was already claimed by another worker")
                    null
                }
            } else {
                // No queued jobs available
                null
            }
        } catch (e: Exception) {
            logger.error("Error finding and claiming next queued analysis job", e)
            null
        }
    }

    override suspend fun findByStatus(status: AnalysisStatus, limit: Int): List<AnalysisJob> = dbQuery(database) {
        try {
            AnalysisJobs.select { AnalysisJobs.status eq status.name }
                .orderBy(AnalysisJobs.createdAt, SortOrder.ASC)
                .limit(limit)
                .map { mapToAnalysisJob(it) }
        } catch (e: Exception) {
            logger.error("Error finding analysis jobs by status: $status", e)
            emptyList()
        }
    }

    override suspend fun updateStatus(
        id: String,
        status: AnalysisStatus,
        errorMessage: String?
    ): Boolean = dbQuery(database) {
        try {
            val updatedRows = AnalysisJobs.update({ AnalysisJobs.id eq id }) {
                it[AnalysisJobs.status] = status.name
                it[AnalysisJobs.updatedAt] = Clock.System.now()

                when (status) {
                    AnalysisStatus.PROCESSING -> {
                        it[AnalysisJobs.startedAt] = Clock.System.now()
                    }
                    AnalysisStatus.COMPLETED, AnalysisStatus.FAILED, AnalysisStatus.CANCELLED -> {
                        it[AnalysisJobs.completedAt] = Clock.System.now()
                    }
                    else -> {
                        // No additional timestamp updates for QUEUED
                    }
                }

                errorMessage?.let { msg ->
                    it[AnalysisJobs.errorMessage] = msg
                }
            }

            updatedRows > 0
        } catch (e: Exception) {
            logger.error("Error updating analysis job status: $id", e)
            false
        }
    }

    /**
     * Attempts to claim a job by updating its status from QUEUED to PROCESSING
     * Returns true only if the job was successfully claimed (status was QUEUED)
     */
    suspend fun claimJob(id: String): Boolean = dbQuery(database) {
        try {
            val now = Clock.System.now()
            val updatedRows = AnalysisJobs.update({
                (AnalysisJobs.id eq id) and (AnalysisJobs.status eq AnalysisStatus.QUEUED.name)
            }) { stmt ->
                stmt[status] = AnalysisStatus.PROCESSING.name
                stmt[startedAt] = now
                stmt[updatedAt] = now
            }
            
            val success = updatedRows > 0
            if (success) {
                logger.debug("Successfully claimed job: $id")
            } else {
                logger.debug("Failed to claim job: $id (already claimed or not queued)")
            }
            success
        } catch (e: Exception) {
            logger.error("Error claiming analysis job: $id", e)
            false
        }
    }

    override suspend fun deleteById(id: String): Boolean = dbQuery(database) {
        try {
            val deletedRows = AnalysisJobs.deleteWhere { AnalysisJobs.id eq id }
            deletedRows > 0
        } catch (e: Exception) {
            logger.error("Error deleting analysis job: $id", e)
            false
        }
    }

    override suspend fun getAnalysisStats(userId: String): AnalysisStats = dbQuery(database) {
        try {
            val totalAnalyses = AnalysisJobs.select { AnalysisJobs.userId eq userId }.count()

            val completedAnalyses = AnalysisJobs.select {
                (AnalysisJobs.userId eq userId) and (AnalysisJobs.status eq AnalysisStatus.COMPLETED.name)
            }.count()

            val failedAnalyses = AnalysisJobs.select {
                (AnalysisJobs.userId eq userId) and (AnalysisJobs.status eq AnalysisStatus.FAILED.name)
            }.count()

            val totalCost = AnalysisJobs.slice(AnalysisJobs.costUsd.sum())
                .select { AnalysisJobs.userId eq userId }
                .singleOrNull()
                ?.get(AnalysisJobs.costUsd.sum())?.toDouble() ?: 0.0

            val avgProcessingTime = AnalysisJobs.slice(AnalysisJobs.processingTimeMs.avg())
                .select {
                    (AnalysisJobs.userId eq userId) and (AnalysisJobs.processingTimeMs.isNotNull())
                }
                .singleOrNull()
                ?.get(AnalysisJobs.processingTimeMs.avg())?.toDouble()

            // Analysis by type statistics
            val analysesByType = mutableMapOf<String, Long>()
            AnalysisType.entries.forEach { type ->
                val count = AnalysisJobs.select {
                    (AnalysisJobs.userId eq userId) and (AnalysisJobs.analysisType eq type.name)
                }.count()
                if (count > 0) {
                    analysesByType[type.displayName] = count
                }
            }

            AnalysisStats(
                totalAnalyses = totalAnalyses,
                completedAnalyses = completedAnalyses,
                failedAnalyses = failedAnalyses,
                totalCostUsd = totalCost,
                averageProcessingTimeMs = avgProcessingTime,
                analysesByType = analysesByType
            )
        } catch (e: Exception) {
            logger.error("Error getting analysis stats for user: $userId", e)
            AnalysisStats(0, 0, 0, 0.0, null, emptyMap())
        }
    }

    /**
     * Fill statement with analysis job data
     */
    private fun fillStatement(stmt: UpdateBuilder<*>, job: AnalysisJob) {
        stmt[AnalysisJobs.userId] = job.userId
        stmt[AnalysisJobs.screenshotJobId] = job.screenshotJobId
        stmt[AnalysisJobs.screenshotUrl] = job.screenshotUrl
        stmt[AnalysisJobs.analysisType] = job.analysisType.name
        stmt[AnalysisJobs.status] = job.status.name
        stmt[AnalysisJobs.language] = job.language
        stmt[AnalysisJobs.webhookUrl] = job.webhookUrl
        stmt[AnalysisJobs.resultData] = job.resultData
        stmt[AnalysisJobs.confidence] = job.confidence
        stmt[AnalysisJobs.metadata] = job.metadata.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) }
        stmt[AnalysisJobs.processingTimeMs] = job.processingTimeMs
        stmt[AnalysisJobs.tokensUsed] = job.tokensUsed
        stmt[AnalysisJobs.costUsd] = job.costUsd
        stmt[AnalysisJobs.errorMessage] = job.errorMessage
        stmt[AnalysisJobs.createdAt] = job.createdAt
        stmt[AnalysisJobs.startedAt] = job.startedAt
        stmt[AnalysisJobs.completedAt] = job.completedAt
        stmt[AnalysisJobs.updatedAt] = job.updatedAt
    }

    /**
     * Map database row to AnalysisJob entity
     */
    private fun mapToAnalysisJob(row: ResultRow): AnalysisJob {
        return AnalysisJob(
            id = row[AnalysisJobs.id].value,
            userId = row[AnalysisJobs.userId],
            screenshotJobId = row[AnalysisJobs.screenshotJobId],
            screenshotUrl = row[AnalysisJobs.screenshotUrl],
            analysisType = AnalysisType.valueOf(row[AnalysisJobs.analysisType]),
            status = AnalysisStatus.valueOf(row[AnalysisJobs.status]),
            language = row[AnalysisJobs.language],
            webhookUrl = row[AnalysisJobs.webhookUrl],
            resultData = row[AnalysisJobs.resultData],
            confidence = row[AnalysisJobs.confidence],
            metadata = row[AnalysisJobs.metadata]?.let {
                Json.decodeFromString<Map<String, String>>(it)
            } ?: emptyMap(),
            processingTimeMs = row[AnalysisJobs.processingTimeMs],
            tokensUsed = row[AnalysisJobs.tokensUsed],
            costUsd = row[AnalysisJobs.costUsd],
            errorMessage = row[AnalysisJobs.errorMessage],
            createdAt = row[AnalysisJobs.createdAt],
            startedAt = row[AnalysisJobs.startedAt],
            completedAt = row[AnalysisJobs.completedAt],
            updatedAt = row[AnalysisJobs.updatedAt]
        )
    }

}

