package dev.screenshotapi.core.domain.repositories

import dev.screenshotapi.core.domain.entities.OcrResult
import dev.screenshotapi.core.domain.entities.OcrEngine
import dev.screenshotapi.core.domain.entities.OcrTier
import kotlinx.datetime.Instant

/**
 * OCR Result Repository - Port for OCR result persistence
 * GitHub Issue #2: OCR Domain Architecture
 */
interface OcrResultRepository {
    
    /**
     * Save OCR result
     */
    suspend fun save(result: OcrResult): OcrResult
    
    /**
     * Find OCR result by ID
     */
    suspend fun findById(id: String): OcrResult?
    
    /**
     * Find OCR results by user ID with pagination
     */
    suspend fun findByUserId(
        userId: String,
        offset: Int = 0,
        limit: Int = 50
    ): List<OcrResult>
    
    /**
     * Find OCR results by screenshot job ID
     */
    suspend fun findByScreenshotJobId(screenshotJobId: String): List<OcrResult>
    
    /**
     * Get OCR analytics for user
     */
    suspend fun getUserOcrAnalytics(
        userId: String,
        fromDate: Instant,
        toDate: Instant
    ): OcrAnalytics
    
    /**
     * Get system-wide OCR analytics
     */
    suspend fun getSystemOcrAnalytics(
        fromDate: Instant,
        toDate: Instant
    ): OcrAnalytics
    
    /**
     * Delete OCR results older than specified date
     */
    suspend fun deleteOlderThan(date: Instant): Int
    
    /**
     * Count OCR results by user and date range
     */
    suspend fun countByUserAndDateRange(
        userId: String,
        fromDate: Instant,
        toDate: Instant
    ): Long
}

/**
 * OCR Analytics Data
 */
data class OcrAnalytics(
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val averageConfidence: Double,
    val averageProcessingTime: Double,
    val totalWordsExtracted: Long,
    val engineUsage: Map<OcrEngine, Long>,
    val tierUsage: Map<OcrTier, Long>,
    val languageUsage: Map<String, Long>,
    val topErrors: List<OcrErrorSummary>
)

/**
 * OCR Error Summary
 */
data class OcrErrorSummary(
    val errorType: String,
    val count: Long,
    val percentage: Double
)