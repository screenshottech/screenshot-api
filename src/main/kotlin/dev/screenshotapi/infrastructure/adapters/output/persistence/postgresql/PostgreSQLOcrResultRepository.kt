package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.OcrAnalytics
import dev.screenshotapi.core.domain.repositories.OcrErrorSummary
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.OcrResults
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities.Users
import dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.dbQuery
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import kotlinx.datetime.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.slf4j.LoggerFactory

/**
 * PostgreSQL OCR Result Repository
 * Following codebase patterns: dbQuery, entity mapping, error handling
 */
class PostgreSQLOcrResultRepository(private val database: Database) : OcrResultRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(result: OcrResult): OcrResult {
        return try {
            dbQuery(database) {
                val entityId = result.id

                val existingResult = OcrResults.select { OcrResults.id eq entityId }.singleOrNull()

                if (existingResult != null) {
                    // Update existing
                    OcrResults.update({ OcrResults.id eq entityId }) {
                        it[success] = result.success
                        it[extractedText] = result.extractedText
                        it[confidence] = result.confidence
                        it[wordCount] = result.wordCount
                        it[lines] = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(OcrTextLine.serializer()), result.lines)
                        it[processingTime] = result.processingTime
                        it[language] = result.language
                        it[engine] = result.engine.name
                        it[structuredData] = result.structuredData?.let { data -> 
                            json.encodeToString(OcrStructuredData.serializer(), data) 
                        }
                        it[metadata] = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), result.metadata)
                        it[updatedAt] = Clock.System.now()
                    }
                } else {
                    // Insert new
                    OcrResults.insert {
                        it[id] = entityId
                        it[userId] = result.userId
                        it[screenshotJobId] = result.metadata["screenshotJobId"]
                        it[success] = result.success
                        it[extractedText] = result.extractedText
                        it[confidence] = result.confidence
                        it[wordCount] = result.wordCount
                        it[lines] = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(OcrTextLine.serializer()), result.lines)
                        it[processingTime] = result.processingTime
                        it[language] = result.language
                        it[engine] = result.engine.name
                        it[structuredData] = result.structuredData?.let { data -> 
                            json.encodeToString(OcrStructuredData.serializer(), data) 
                        }
                        it[metadata] = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), result.metadata)
                        it[createdAt] = result.createdAt
                        it[updatedAt] = Clock.System.now()
                    }
                }

                result
            }
        } catch (e: Exception) {
            logger.error("Error saving OCR result: ${result.id}", e)
            throw DatabaseException.OperationFailed("Failed to save OCR result", e)
        }
    }

    override suspend fun findById(id: String): OcrResult? {
        return try {
            dbQuery(database) {
                OcrResults.select { OcrResults.id eq id }
                    .singleOrNull()
                    ?.let { row -> mapRowToOcrResult(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding OCR result by id: $id", e)
            throw DatabaseException.OperationFailed("Failed to find OCR result by id", e)
        }
    }

    override suspend fun findByScreenshotJobId(screenshotJobId: String): List<OcrResult> {
        return try {
            dbQuery(database) {
                OcrResults.select { OcrResults.screenshotJobId eq screenshotJobId }
                    .orderBy(OcrResults.createdAt, SortOrder.DESC)
                    .map { row -> mapRowToOcrResult(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding OCR results by screenshot job id: $screenshotJobId", e)
            throw DatabaseException.OperationFailed("Failed to find OCR results by screenshot job id", e)
        }
    }

    override suspend fun findByUserId(userId: String, offset: Int, limit: Int): List<OcrResult> {
        return try {
            dbQuery(database) {
                OcrResults.select { OcrResults.userId eq userId }
                    .orderBy(OcrResults.createdAt, SortOrder.DESC)
                    .limit(limit, offset = offset.toLong())
                    .map { row -> mapRowToOcrResult(row) }
            }
        } catch (e: Exception) {
            logger.error("Error finding OCR results for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to find OCR results for user", e)
        }
    }

    override suspend fun getUserOcrAnalytics(
        userId: String,
        fromDate: Instant,
        toDate: Instant
    ): OcrAnalytics {
        return try {
            dbQuery(database) {
                val results = OcrResults.select { 
                    (OcrResults.userId eq userId) and 
                    (OcrResults.createdAt greaterEq fromDate) and 
                    (OcrResults.createdAt lessEq toDate)
                }.toList()

                calculateAnalytics(results)
            }
        } catch (e: Exception) {
            logger.error("Error getting user OCR analytics: $userId", e)
            throw DatabaseException.OperationFailed("Failed to get user OCR analytics", e)
        }
    }

    override suspend fun getSystemOcrAnalytics(
        fromDate: Instant,
        toDate: Instant
    ): OcrAnalytics {
        return try {
            dbQuery(database) {
                val results = OcrResults.select { 
                    (OcrResults.createdAt greaterEq fromDate) and 
                    (OcrResults.createdAt lessEq toDate)
                }.toList()

                calculateAnalytics(results)
            }
        } catch (e: Exception) {
            logger.error("Error getting system OCR analytics", e)
            throw DatabaseException.OperationFailed("Failed to get system OCR analytics", e)
        }
    }

    override suspend fun deleteOlderThan(date: Instant): Int {
        return try {
            dbQuery(database) {
                OcrResults.deleteWhere { OcrResults.createdAt lessEq date }
            }
        } catch (e: Exception) {
            logger.error("Error deleting OCR results older than: $date", e)
            throw DatabaseException.OperationFailed("Failed to delete old OCR results", e)
        }
    }

    override suspend fun countByUserAndDateRange(
        userId: String,
        fromDate: Instant,
        toDate: Instant
    ): Long {
        return try {
            dbQuery(database) {
                OcrResults.select { 
                    (OcrResults.userId eq userId) and 
                    (OcrResults.createdAt greaterEq fromDate) and 
                    (OcrResults.createdAt lessEq toDate)
                }.count()
            }
        } catch (e: Exception) {
            logger.error("Error counting OCR results for user: $userId", e)
            throw DatabaseException.OperationFailed("Failed to count OCR results", e)
        }
    }

    private fun calculateAnalytics(results: List<ResultRow>): OcrAnalytics {
        val totalRequests = results.size.toLong()
        val successfulRequests = results.count { it[OcrResults.success] }.toLong()
        val failedRequests = totalRequests - successfulRequests

        val averageConfidence = if (results.isNotEmpty()) {
            results.map { it[OcrResults.confidence] }.average()
        } else 0.0

        val averageProcessingTime = if (results.isNotEmpty()) {
            results.map { it[OcrResults.processingTime] }.average()
        } else 0.0

        val totalWordsExtracted = results.sumOf { it[OcrResults.wordCount] }.toLong()

        val engineUsage = results.groupBy { OcrEngine.valueOf(it[OcrResults.engine]) }
            .mapValues { it.value.size.toLong() }

        val tierUsage = emptyMap<OcrTier, Long>() // Would need tier column for real implementation

        val languageUsage = results.groupBy { it[OcrResults.language] }
            .mapValues { it.value.size.toLong() }

        // For now, return empty errors - would need error logging table for real implementation
        val topErrors = emptyList<OcrErrorSummary>()

        return OcrAnalytics(
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            averageConfidence = averageConfidence,
            averageProcessingTime = averageProcessingTime,
            totalWordsExtracted = totalWordsExtracted,
            engineUsage = engineUsage,
            tierUsage = tierUsage,
            languageUsage = languageUsage,
            topErrors = topErrors
        )
    }

    private fun mapRowToOcrResult(row: ResultRow): OcrResult {
        return OcrResult(
            id = row[OcrResults.id].value,
            userId = row[OcrResults.userId].value,
            success = row[OcrResults.success],
            extractedText = row[OcrResults.extractedText],
            confidence = row[OcrResults.confidence],
            wordCount = row[OcrResults.wordCount],
            lines = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(OcrTextLine.serializer()), row[OcrResults.lines]),
            processingTime = row[OcrResults.processingTime],
            language = row[OcrResults.language],
            engine = OcrEngine.valueOf(row[OcrResults.engine]),
            structuredData = row[OcrResults.structuredData]?.let { data ->
                json.decodeFromString(OcrStructuredData.serializer(), data)
            },
            metadata = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), row[OcrResults.metadata] ?: "{}"),
            createdAt = row[OcrResults.createdAt]
        )
    }
}