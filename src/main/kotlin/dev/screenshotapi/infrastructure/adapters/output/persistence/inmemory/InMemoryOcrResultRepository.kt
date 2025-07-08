package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.OcrAnalytics
import dev.screenshotapi.core.domain.repositories.OcrErrorSummary
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory OCR Result Repository
 * GitHub Issue #3: Implement PaddleOCR service layer with ProcessBuilder
 */
class InMemoryOcrResultRepository : OcrResultRepository {
    
    private val results = ConcurrentHashMap<String, OcrResult>()
    
    override suspend fun save(result: OcrResult): OcrResult {
        results[result.id] = result
        return result
    }
    
    override suspend fun findById(id: String): OcrResult? {
        return results[id]
    }
    
    override suspend fun findByUserId(userId: String, offset: Int, limit: Int): List<OcrResult> {
        return results.values
            .filter { it.metadata["userId"] == userId }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)
    }
    
    override suspend fun findByScreenshotJobId(screenshotJobId: String): List<OcrResult> {
        return results.values
            .filter { it.metadata["screenshotJobId"] == screenshotJobId }
            .sortedByDescending { it.createdAt }
    }
    
    override suspend fun getUserOcrAnalytics(
        userId: String,
        fromDate: Instant,
        toDate: Instant
    ): OcrAnalytics {
        val userResults = results.values
            .filter { 
                it.metadata["userId"] == userId &&
                it.createdAt >= fromDate && 
                it.createdAt <= toDate 
            }
        
        return calculateAnalytics(userResults)
    }
    
    override suspend fun getSystemOcrAnalytics(
        fromDate: Instant,
        toDate: Instant
    ): OcrAnalytics {
        val systemResults = results.values
            .filter { it.createdAt >= fromDate && it.createdAt <= toDate }
        
        return calculateAnalytics(systemResults)
    }
    
    override suspend fun deleteOlderThan(date: Instant): Int {
        val toDelete = results.values
            .filter { it.createdAt < date }
            .map { it.id }
        
        toDelete.forEach { results.remove(it) }
        return toDelete.size
    }
    
    override suspend fun countByUserAndDateRange(
        userId: String,
        fromDate: Instant,
        toDate: Instant
    ): Long {
        return results.values
            .count { 
                it.metadata["userId"] == userId &&
                it.createdAt >= fromDate && 
                it.createdAt <= toDate 
            }
            .toLong()
    }
    
    private fun calculateAnalytics(results: List<OcrResult>): OcrAnalytics {
        val totalRequests = results.size.toLong()
        val successfulRequests = results.count { it.success }.toLong()
        val failedRequests = totalRequests - successfulRequests
        
        val averageConfidence = if (results.isNotEmpty()) {
            results.map { it.confidence }.average()
        } else 0.0
        
        val averageProcessingTime = if (results.isNotEmpty()) {
            results.map { it.processingTime }.average()
        } else 0.0
        
        val totalWordsExtracted = results.sumOf { it.wordCount }.toLong()
        
        // Engine usage statistics
        val engineUsage = results.groupingBy { it.engine }
            .eachCount()
            .mapValues { it.value.toLong() }
        
        // Tier usage statistics (from metadata)
        val tierUsage = results.mapNotNull { result ->
            result.metadata["tier"]?.let { tierName ->
                try {
                    OcrTier.valueOf(tierName)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
        .groupingBy { it }
        .eachCount()
        .mapValues { it.value.toLong() }
        
        // Language usage statistics
        val languageUsage = results.groupingBy { it.language }
            .eachCount()
            .mapValues { it.value.toLong() }
        
        // Error analysis for failed requests
        val failedResults = results.filter { !it.success }
        val errorGroups = failedResults.groupBy { 
            it.metadata["error"] ?: "Unknown error" 
        }
        
        val topErrors = errorGroups.map { (errorType, errors) ->
            OcrErrorSummary(
                errorType = errorType,
                count = errors.size.toLong(),
                percentage = if (totalRequests > 0) {
                    (errors.size.toDouble() / totalRequests * 100)
                } else 0.0
            )
        }
        .sortedByDescending { it.count }
        .take(10)
        
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
}