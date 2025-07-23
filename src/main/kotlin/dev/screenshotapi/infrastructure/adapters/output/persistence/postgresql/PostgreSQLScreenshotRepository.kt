package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.ScreenshotOverallStats
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.usecases.admin.ScreenshotStatItem
import dev.screenshotapi.infrastructure.adapters.output.persistence.dto.ScreenshotFormatDto
import dev.screenshotapi.infrastructure.adapters.output.persistence.dto.ScreenshotRequestDto
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Screenshots
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.minutes

class PostgreSQLScreenshotRepository(private val database: Database) : ScreenshotRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(job: ScreenshotJob): ScreenshotJob {
        return try {
            dbQuery(database) {
                val entityId = try {
                    job.id
                } catch (e: IllegalArgumentException) {
                    UUID.randomUUID().toString()
                }

                val requestDto = ScreenshotRequestDto.fromDomain(job.request)

                val insertedId = Screenshots.insertAndGetId {
                    it[id] = entityId
                    it[userId] = job.userId
                    it[apiKeyId] = job.apiKeyId
                    it[url] = job.request.url
                    it[status] = job.status.name
                    it[resultUrl] = job.resultUrl
                    it[options] = json.encodeToString(ScreenshotRequestDto.serializer(), requestDto)
                    it[processingTimeMs] = job.processingTimeMs
                    it[fileSizeBytes] = job.fileSizeBytes
                    it[errorMessage] = job.errorMessage
                    it[webhookUrl] = job.webhookUrl
                    it[webhookSent] = job.webhookSent
                    it[createdAt] = job.createdAt
                    it[updatedAt] = job.updatedAt
                    it[completedAt] = job.completedAt
                    // Retry fields
                    it[retryCount] = job.retryCount
                    it[maxRetries] = job.maxRetries
                    it[nextRetryAt] = job.nextRetryAt
                    it[lastFailureReason] = job.lastFailureReason
                    it[isRetryable] = job.isRetryable
                    it[retryType] = job.retryType.name
                    it[lockedBy] = job.lockedBy
                    it[lockedAt] = job.lockedAt
                    // Metadata field
                    it[metadata] = job.metadata?.let { pageMetadata ->
                        json.encodeToString(PageMetadata.serializer(), pageMetadata)
                    }
                }

                job.copy(id = insertedId.value.toString())
            }
        } catch (e: Exception) {
            logger.error("Error saving screenshot job: ${job.id}", e)
            throw DatabaseException.OperationFailed("Failed to save screenshot job", e)
        }
    }

    override suspend fun findById(id: String): ScreenshotJob? {
        return try {
            dbQuery(database) {
                Screenshots.select { Screenshots.id eq id }
                    .singleOrNull()
                    ?.let { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding screenshot job by ID: $id", e)
            throw DatabaseException.OperationFailed("Failed to find screenshot job by ID", e)
        }
    }

    override suspend fun findByIdAndUserId(id: String, userId: String): ScreenshotJob? {
        return try {
            dbQuery(database) {
                Screenshots.select {
                    (Screenshots.id eq id) and
                    (Screenshots.userId eq userId)
                }
                .singleOrNull()
                ?.let { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding screenshot job by ID and user ID: $id, $userId", e)
            throw DatabaseException.OperationFailed("Failed to find screenshot job by ID and user ID", e)
        }
    }

    override suspend fun findByUserId(userId: String, page: Int, limit: Int): List<ScreenshotJob> {
        return try {
            dbQuery(database) {
                Screenshots.select { Screenshots.userId eq userId }
                    .orderBy(Screenshots.createdAt, SortOrder.DESC)
                    .limit(limit, offset = ((page - 1) * limit).toLong())
                    .map { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding screenshot jobs for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find screenshot jobs for user", e)
        }
    }

    override suspend fun findByUserIdAndStatus(userId: String, status: ScreenshotStatus, page: Int, limit: Int): List<ScreenshotJob> {
        return try {
            dbQuery(database) {
                Screenshots.select { 
                    (Screenshots.userId eq userId) and (Screenshots.status eq status.name)
                }
                    .orderBy(Screenshots.createdAt, SortOrder.DESC)
                    .limit(limit, offset = ((page - 1) * limit).toLong())
                    .map { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding screenshot jobs for user: $userId with status: $status", e)
            throw DatabaseException.OperationFailed("Failed to find screenshot jobs for user with status", e)
        }
    }

    override suspend fun findByIds(ids: List<String>, userId: String): List<ScreenshotJob> {
        return try {
            dbQuery(database) {
                Screenshots.select {
                    (Screenshots.id inList ids) and (Screenshots.userId eq userId)
                }
                    .map { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding screenshot jobs by IDs: $ids for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find screenshot jobs by IDs", e)
        }
    }

    override suspend fun update(job: ScreenshotJob): ScreenshotJob {
        return try {
            dbQuery(database) {
                val updatedRows = Screenshots.update({ Screenshots.id eq job.id }) {
                    it[status] = job.status.name
                    it[resultUrl] = job.resultUrl
                    it[processingTimeMs] = job.processingTimeMs
                    it[fileSizeBytes] = job.fileSizeBytes
                    it[errorMessage] = job.errorMessage
                    it[webhookSent] = job.webhookSent
                    it[updatedAt] = Clock.System.now()
                    it[completedAt] = job.completedAt
                    // Retry fields
                    it[retryCount] = job.retryCount
                    it[maxRetries] = job.maxRetries
                    it[nextRetryAt] = job.nextRetryAt
                    it[lastFailureReason] = job.lastFailureReason
                    it[isRetryable] = job.isRetryable
                    it[retryType] = job.retryType.name
                    it[lockedBy] = job.lockedBy
                    it[lockedAt] = job.lockedAt
                    // Metadata field
                    it[metadata] = job.metadata?.let { pageMetadata ->
                        json.encodeToString(PageMetadata.serializer(), pageMetadata)
                    }
                }

                if (updatedRows == 0) {
                    throw DatabaseException.OperationFailed("Screenshot job not found: ${job.id}")
                }

                job.copy(updatedAt = Clock.System.now())
            }
        } catch (e: Exception) {
            logger.error("Error updating screenshot job: ${job.id}", e)
            throw DatabaseException.OperationFailed("Failed to update screenshot job", e)
        }
    }

    override suspend fun countByUserId(userId: String): Long {
        return try {
            dbQuery(database) {
                Screenshots.select { Screenshots.userId eq userId }.count()
            }
        } catch (e: Exception) {
            logger.error("Error counting screenshot jobs for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to count screenshot jobs for user", e)
        }
    }

    override suspend fun countByUserIdAndStatus(userId: String, status: ScreenshotStatus): Long {
        return try {
            dbQuery(database) {
                Screenshots.select { 
                    (Screenshots.userId eq userId) and (Screenshots.status eq status.name)
                }.count()
            }
        } catch (e: Exception) {
            logger.error("Error counting screenshot jobs for user: $userId with status: $status", e)
            throw DatabaseException.OperationFailed("Failed to count screenshot jobs for user with status", e)
        }
    }

    override suspend fun findPendingJobs(): List<ScreenshotJob> {
        return try {
            dbQuery(database) {
                Screenshots.select { Screenshots.status eq ScreenshotStatus.QUEUED.name }
                    .orderBy(Screenshots.createdAt, SortOrder.ASC)
                    .map { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding pending screenshot jobs", e)
            throw DatabaseException.OperationFailed("Failed to find pending screenshot jobs", e)
        }
    }

    override suspend fun getStatsByPeriod(
        startDate: Instant,
        endDate: Instant,
        groupBy: StatsGroupBy
    ): List<ScreenshotStatItem> {
        return try {
            dbQuery(database) {
                val dateFormat = when (groupBy) {
                    StatsGroupBy.HOUR -> "YYYY-MM-DD HH24:00:00"
                    StatsGroupBy.DAY -> "YYYY-MM-DD"
                    StatsGroupBy.WEEK -> "IYYY-IW"
                    StatsGroupBy.MONTH -> "YYYY-MM"
                    StatsGroupBy.STATUS -> "YYYY-MM-DD" // Group by status doesn't need time format
                    StatsGroupBy.FORMAT -> "YYYY-MM-DD" // Group by format doesn't need time format
                }

                val query = Screenshots.slice(
                    Screenshots.createdAt,
                    Screenshots.status,
                    Screenshots.processingTimeMs
                ).select {
                    Screenshots.createdAt.between(startDate, endDate)
                }

                val results = mutableMapOf<String, MutableMap<String, Any>>()

                query.forEach { row ->
                    val date = row[Screenshots.createdAt].toLocalDateTime(TimeZone.UTC)
                    val period = when (groupBy) {
                        StatsGroupBy.HOUR -> "${date.date} ${date.hour}:00:00"
                        StatsGroupBy.DAY -> date.date.toString()
                        StatsGroupBy.WEEK -> "${date.year}-W${date.dayOfYear / 7 + 1}"
                        StatsGroupBy.MONTH -> "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
                        StatsGroupBy.STATUS -> row[Screenshots.status] // Group by status value
                        StatsGroupBy.FORMAT -> {
                            // Try to extract format from options JSON, fallback to "unknown"
                            try {
                                val requestDto = json.decodeFromString(ScreenshotRequestDto.serializer(), row[Screenshots.options])
                                requestDto.format.name
                            } catch (e: Exception) {
                                "unknown"
                            }
                        }
                    }

                    val periodData = results.getOrPut(period) {
                        mutableMapOf(
                            "count" to 0L,
                            "successful" to 0L,
                            "failed" to 0L,
                            "totalProcessingTime" to 0L,
                            "processedCount" to 0L
                        )
                    }

                    periodData["count"] = (periodData["count"] as Long) + 1

                    when (row[Screenshots.status]) {
                        ScreenshotStatus.COMPLETED.name -> {
                            periodData["successful"] = (periodData["successful"] as Long) + 1
                        }
                        ScreenshotStatus.FAILED.name -> {
                            periodData["failed"] = (periodData["failed"] as Long) + 1
                        }
                    }

                    row[Screenshots.processingTimeMs]?.let { processingTime ->
                        periodData["totalProcessingTime"] = (periodData["totalProcessingTime"] as Long) + processingTime
                        periodData["processedCount"] = (periodData["processedCount"] as Long) + 1
                    }
                }

                results.map { (period, data) ->
                    val processedCount = data["processedCount"] as Long
                    val averageProcessingTime = if (processedCount > 0) {
                        (data["totalProcessingTime"] as Long) / processedCount
                    } else null

                    ScreenshotStatItem(
                        period = period,
                        count = data["count"] as Long,
                        successful = data["successful"] as Long,
                        failed = data["failed"] as Long,
                        averageProcessingTime = averageProcessingTime
                    )
                }.sortedBy { it.period }
            }
        } catch (e: Exception) {
            logger.error("Error getting stats by period", e)
            throw DatabaseException.OperationFailed("Failed to get stats by period", e)
        }
    }

    override suspend fun getOverallStats(): ScreenshotOverallStats {
        return try {
            dbQuery(database) {
                val total = Screenshots.selectAll().count()
                val completed = Screenshots.select { Screenshots.status eq ScreenshotStatus.COMPLETED.name }.count()
                val failed = Screenshots.select { Screenshots.status eq ScreenshotStatus.FAILED.name }.count()
                val queued = Screenshots.select { Screenshots.status eq ScreenshotStatus.QUEUED.name }.count()
                val processing = Screenshots.select { Screenshots.status eq ScreenshotStatus.PROCESSING.name }.count()

                val avgProcessingTime = Screenshots.slice(Screenshots.processingTimeMs)
                    .select { Screenshots.processingTimeMs.isNotNull() }
                    .map { it[Screenshots.processingTimeMs]!! }
                    .takeIf { it.isNotEmpty() }
                    ?.average()?.toLong() ?: 0L

                val successRate = if (total > 0) {
                    completed.toDouble() / total.toDouble()
                } else {
                    0.0
                }

                ScreenshotOverallStats(
                    total = total,
                    completed = completed,
                    failed = failed,
                    queued = queued,
                    processing = processing,
                    averageProcessingTime = avgProcessingTime,
                    successRate = successRate
                )
            }
        } catch (e: Exception) {
            logger.error("Error getting overall stats", e)
            throw DatabaseException.OperationFailed("Failed to get overall stats", e)
        }
    }

    override suspend fun getStatsByFormat(): Map<String, Long> {
        return try {
            dbQuery(database) {
                val results = mutableMapOf<String, Long>()

                Screenshots.selectAll().forEach { row ->
                    try {
                        val requestDto = json.decodeFromString(ScreenshotRequestDto.serializer(), row[Screenshots.options])
                        val format = requestDto.format.name
                        results[format] = results.getOrDefault(format, 0) + 1
                    } catch (e: Exception) {
                        logger.warn("Failed to parse screenshot options: ${row[Screenshots.options]}", e)
                    }
                }

                results
            }
        } catch (e: Exception) {
            logger.error("Error getting stats by format", e)
            throw DatabaseException.OperationFailed("Failed to get stats by format", e)
        }
    }

    override suspend fun getStatsByStatus(): Map<String, Long> {
        return try {
            dbQuery(database) {
                Screenshots.slice(Screenshots.status)
                    .selectAll()
                    .groupBy { it[Screenshots.status] }
                    .mapValues { it.value.size.toLong() }
            }
        } catch (e: Exception) {
            logger.error("Error getting stats by status", e)
            throw DatabaseException.OperationFailed("Failed to get stats by status", e)
        }
    }

    override suspend fun getAverageProcessingTime(): Long {
        return try {
            dbQuery(database) {
                Screenshots.slice(Screenshots.processingTimeMs)
                    .select { Screenshots.processingTimeMs.isNotNull() }
                    .map { it[Screenshots.processingTimeMs]!! }
                    .takeIf { it.isNotEmpty() }
                    ?.average()?.toLong() ?: 0L
            }
        } catch (e: Exception) {
            logger.error("Error getting average processing time", e)
            throw DatabaseException.OperationFailed("Failed to get average processing time", e)
        }
    }

    override suspend fun getSuccessRate(): Double {
        return try {
            dbQuery(database) {
                val total = Screenshots.selectAll().count()
                if (total == 0L) return@dbQuery 0.0

                val completed = Screenshots.select { Screenshots.status eq ScreenshotStatus.COMPLETED.name }.count()
                completed.toDouble() / total.toDouble()
            }
        } catch (e: Exception) {
            logger.error("Error getting success rate", e)
            throw DatabaseException.OperationFailed("Failed to get success rate", e)
        }
    }

    override suspend fun countCreatedToday(): Long {
        return try {
            dbQuery(database) {
                val today = Clock.System.now()
                val startOfDay = today.toLocalDateTime(TimeZone.UTC)
                    .date
                    .atStartOfDayIn(TimeZone.UTC)

                Screenshots.select { Screenshots.createdAt greaterEq startOfDay }.count()
            }
        } catch (e: Exception) {
            logger.error("Error counting screenshots created today", e)
            throw DatabaseException.OperationFailed("Failed to count screenshots created today", e)
        }
    }

    // Retry-related methods implementation
    override suspend fun tryLockJob(jobId: String, workerId: String): ScreenshotJob? {
        return try {
            dbQuery(database) {
                // First try to find and lock the job atomically
                val job = Screenshots.select { Screenshots.id eq jobId }
                    .singleOrNull()
                    ?.let { row -> mapRowToScreenshotJob(row) }
                    ?: return@dbQuery null

                // Check if job is already locked or recently locked
                if (job.isLocked()) {
                    return@dbQuery null
                }

                // Try to acquire lock
                val updatedRows = Screenshots.update({ Screenshots.id eq jobId }) {
                    it[lockedBy] = workerId
                    it[lockedAt] = Clock.System.now()
                }

                if (updatedRows > 0) {
                    job.lock(workerId)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error trying to lock job: $jobId for worker: $workerId", e)
            throw DatabaseException.OperationFailed("Failed to lock job", e)
        }
    }

    override suspend fun findStuckJobs(stuckAfterMinutes: Int, limit: Int): List<ScreenshotJob> {
        return try {
            dbQuery(database) {
                val stuckThreshold = Clock.System.now().minus(stuckAfterMinutes.minutes)
                
                Screenshots.select {
                    (Screenshots.status eq ScreenshotStatus.PROCESSING.name) and
                    (Screenshots.updatedAt less stuckThreshold) and
                    (Screenshots.lockedBy.isNull() or (Screenshots.lockedAt less stuckThreshold.minus(5.minutes)))
                }
                .orderBy(Screenshots.updatedAt, SortOrder.ASC)
                .limit(limit)
                .map { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding stuck jobs", e)
            throw DatabaseException.OperationFailed("Failed to find stuck jobs", e)
        }
    }

    override suspend fun findJobsReadyForRetry(limit: Int): List<ScreenshotJob> {
        return try {
            dbQuery(database) {
                val now = Clock.System.now()
                
                Screenshots.select {
                    (Screenshots.status eq ScreenshotStatus.QUEUED.name) and
                    (Screenshots.nextRetryAt.isNotNull()) and
                    (Screenshots.nextRetryAt lessEq now) and
                    (Screenshots.isRetryable eq true) and
                    (Screenshots.lockedBy.isNull())
                }
                .orderBy(Screenshots.nextRetryAt, SortOrder.ASC)
                .limit(limit)
                .map { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding jobs ready for retry", e)
            throw DatabaseException.OperationFailed("Failed to find jobs ready for retry", e)
        }
    }

    override suspend fun findFailedRetryableJobs(limit: Int): List<ScreenshotJob> {
        return try {
            dbQuery(database) {
                Screenshots.select {
                    (Screenshots.status eq ScreenshotStatus.FAILED.name) and
                    (Screenshots.isRetryable eq true) and
                    (Screenshots.retryCount less Screenshots.maxRetries) and
                    (Screenshots.lockedBy.isNull())
                }
                .orderBy(Screenshots.updatedAt, SortOrder.ASC)
                .limit(limit)
                .map { row -> mapRowToScreenshotJob(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding failed retryable jobs", e)
            throw DatabaseException.OperationFailed("Failed to find failed retryable jobs", e)
        }
    }

    private fun mapRowToScreenshotJob(row: ResultRow): ScreenshotJob {
        val requestDto = try {
            json.decodeFromString(ScreenshotRequestDto.serializer(), row[Screenshots.options])
        } catch (e: Exception) {
            logger.warn("Failed to parse screenshot request: ${row[Screenshots.options]}", e)
            ScreenshotRequestDto("", 1920, 1080, format = ScreenshotFormatDto.PNG)
        }

        return ScreenshotJob(
            id = row[Screenshots.id].value.toString(),
            userId = row[Screenshots.userId].value.toString(),
            apiKeyId = row[Screenshots.apiKeyId].value.toString(),
            request = requestDto.toDomain(),
            status = ScreenshotStatus.valueOf(row[Screenshots.status]),
            resultUrl = row[Screenshots.resultUrl],
            errorMessage = row[Screenshots.errorMessage],
            processingTimeMs = row[Screenshots.processingTimeMs],
            fileSizeBytes = row[Screenshots.fileSizeBytes],
            webhookUrl = row[Screenshots.webhookUrl],
            webhookSent = row[Screenshots.webhookSent],
            createdAt = row[Screenshots.createdAt],
            updatedAt = row[Screenshots.updatedAt],
            completedAt = row[Screenshots.completedAt],
            // Retry fields
            retryCount = row[Screenshots.retryCount],
            maxRetries = row[Screenshots.maxRetries],
            nextRetryAt = row[Screenshots.nextRetryAt],
            lastFailureReason = row[Screenshots.lastFailureReason],
            isRetryable = row[Screenshots.isRetryable],
            retryType = RetryType.valueOf(row[Screenshots.retryType]),
            lockedBy = row[Screenshots.lockedBy],
            lockedAt = row[Screenshots.lockedAt],
            // Page metadata
            metadata = row[Screenshots.metadata]?.let { metadataJson ->
                try {
                    json.decodeFromString(PageMetadata.serializer(), metadataJson)
                } catch (e: Exception) {
                    logger.warn("Failed to parse page metadata: $metadataJson", e)
                    null
                }
            }
        )
    }
}
