package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.ports.output.CachePort
import dev.screenshotapi.infrastructure.config.AnalysisConfig
import dev.screenshotapi.infrastructure.config.ProcessingConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Analysis Cache Service - Intelligent caching for analysis results
 * 
 * Features:
 * - Content-based cache keys (URL + analysis type + language)
 * - TTL-based expiration with type-specific durations
 * - Cache hit/miss metrics
 * - Smart invalidation strategies
 * - Cost optimization through result reuse
 */
class AnalysisCacheService(
    private val cachePort: CachePort,
    private val analysisConfig: AnalysisConfig,
    private val metricsService: MetricsService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Get cached analysis result if available
     */
    suspend fun getCachedResult(request: AnalysisCacheRequest): AnalysisResult? {
        if (!analysisConfig.processing.enableResultCaching) {
            return null
        }
        
        val cacheKey = generateCacheKey(request)
        val startTime = System.currentTimeMillis()
        
        return try {
            val cachedData = cachePort.get(cacheKey, String::class)
            val lookupTime = System.currentTimeMillis() - startTime
            
            if (cachedData != null) {
                val cachedResult = json.decodeFromString<CachedAnalysisResult>(cachedData)
                
                // Check if cache entry is still valid
                if (isCacheEntryValid(cachedResult, request.analysisType)) {
                    val analysisResult = cachedResult.toAnalysisResult()
                    
                    // Record cache hit metrics
                    metricsService.incrementCounter("analysis_cache_hit", mapOf(
                        "type" to request.analysisType.name,
                        "language" to request.language
                    ))
                    metricsService.recordHistogram("analysis_cache_lookup_time", lookupTime)
                    
                    logger.info(
                        "Cache HIT: key=$cacheKey, type=${request.analysisType}, " +
                        "age=${getCacheAge(cachedResult)}min, lookupTime=${lookupTime}ms"
                    )
                    
                    return analysisResult
                } else {
                    // Cache entry expired, remove it
                    cachePort.remove(cacheKey)
                    logger.debug("Cache entry expired and removed: key=$cacheKey")
                }
            }
            
            // Record cache miss
            metricsService.incrementCounter("analysis_cache_miss", mapOf(
                "type" to request.analysisType.name,
                "language" to request.language,
                "reason" to if (cachedData == null) "not_found" else "expired"
            ))
            metricsService.recordHistogram("analysis_cache_lookup_time", lookupTime)
            
            logger.debug("Cache MISS: key=$cacheKey, type=${request.analysisType}")
            null
            
        } catch (e: Exception) {
            logger.error("Error retrieving from cache: key=$cacheKey", e)
            
            metricsService.incrementCounter("analysis_cache_error", mapOf(
                "type" to request.analysisType.name,
                "operation" to "get",
                "error" to e::class.simpleName.orEmpty()
            ))
            
            null
        }
    }
    
    /**
     * Store analysis result in cache
     */
    suspend fun cacheResult(request: AnalysisCacheRequest, result: AnalysisResult) {
        if (!analysisConfig.processing.enableResultCaching) {
            return
        }
        
        // Only cache successful results
        if (result !is AnalysisResult.Success) {
            logger.debug("Not caching failed analysis result: ${result.jobId}")
            return
        }
        
        val cacheKey = generateCacheKey(request)
        val ttl = getTtlForAnalysisType(request.analysisType)
        val startTime = System.currentTimeMillis()
        
        try {
            val cachedResult = CachedAnalysisResult.fromAnalysisResult(result, request)
            val serializedData = json.encodeToString(CachedAnalysisResult.serializer(), cachedResult)
            
            cachePort.put(cacheKey, serializedData, ttl)
            
            val cacheTime = System.currentTimeMillis() - startTime
            
            // Record cache set metrics
            metricsService.incrementCounter("analysis_cache_set", mapOf(
                "type" to request.analysisType.name,
                "language" to request.language
            ))
            metricsService.recordHistogram("analysis_cache_set_time", cacheTime)
            metricsService.recordHistogram("analysis_cache_data_size", serializedData.length.toLong())
            
            logger.info(
                "Cached analysis result: key=$cacheKey, type=${request.analysisType}, " +
                "ttl=${ttl.inWholeMinutes}min, size=${serializedData.length}bytes, time=${cacheTime}ms"
            )
            
        } catch (e: Exception) {
            logger.error("Error storing in cache: key=$cacheKey", e)
            
            metricsService.incrementCounter("analysis_cache_error", mapOf(
                "type" to request.analysisType.name,
                "operation" to "set",
                "error" to e::class.simpleName.orEmpty()
            ))
        }
    }
    
    /**
     * Generate content-based cache key
     */
    private fun generateCacheKey(request: AnalysisCacheRequest): String {
        // Create a hash of the content that affects the analysis result
        val contentToHash = listOf(
            request.screenshotUrl,
            request.analysisType.name,
            request.language,
            request.options?.toString() ?: ""
        ).joinToString("|")
        
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(contentToHash.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16) // Use first 16 characters for shorter keys
        
        return "analysis:${request.analysisType.name.lowercase()}:$hash"
    }
    
    /**
     * Get TTL based on analysis type and configuration
     */
    private fun getTtlForAnalysisType(analysisType: AnalysisType): Duration {
        return when (analysisType) {
            AnalysisType.BASIC_OCR -> 6.hours        // OCR results rarely change
            AnalysisType.UX_ANALYSIS -> 2.hours      // UX could change more frequently
            AnalysisType.CONTENT_SUMMARY -> 1.hours  // Content might update more often
            AnalysisType.GENERAL -> 4.hours          // General analysis moderate caching
            AnalysisType.CUSTOM -> 30.minutes        // Custom analysis shorter cache due to variability
        }
    }
    
    /**
     * Check if cache entry is still valid
     */
    private fun isCacheEntryValid(cachedResult: CachedAnalysisResult, analysisType: AnalysisType): Boolean {
        val now = Clock.System.now()
        val age = now - cachedResult.cachedAt
        val maxAge = getTtlForAnalysisType(analysisType)
        
        return age < maxAge
    }
    
    /**
     * Get cache age in minutes
     */
    private fun getCacheAge(cachedResult: CachedAnalysisResult): Long {
        val now = Clock.System.now()
        return (now - cachedResult.cachedAt).inWholeMinutes
    }
    
    /**
     * Invalidate cache entries for a specific screenshot URL
     */
    suspend fun invalidateByScreenshotUrl(screenshotUrl: String) {
        // Since we use content-based keys, we'd need to track reverse mappings
        // For now, log the invalidation request
        logger.info("Cache invalidation requested for screenshot: $screenshotUrl")
        
        metricsService.incrementCounter("analysis_cache_invalidation_request", mapOf(
            "reason" to "screenshot_url_change"
        ))
        
        // TODO: Implement reverse mapping if needed for more precise invalidation
    }
    
    /**
     * Clear all cached analysis results (admin operation)
     */
    suspend fun clearAllCache() {
        try {
            // This would require a way to enumerate all analysis cache keys
            // For now, log the clear request
            logger.warn("Analysis cache clear requested - this requires manual implementation")
            
            metricsService.incrementCounter("analysis_cache_clear_all")
            
        } catch (e: Exception) {
            logger.error("Error clearing analysis cache", e)
        }
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): AnalysisCacheStats {
        // These would come from metrics aggregation in a real implementation
        return AnalysisCacheStats(
            totalRequests = 0, // Would be calculated from metrics
            cacheHits = 0,     // Would be calculated from metrics
            cacheMisses = 0,   // Would be calculated from metrics
            hitRate = 0.0,     // Would be calculated from hits/total
            avgLookupTimeMs = 0.0,
            totalCachedResults = 0,
            cacheSize = 0L
        )
    }
    
    /**
     * Check if caching is enabled for a specific analysis type
     */
    fun isCachingEnabledFor(analysisType: AnalysisType): Boolean {
        return analysisConfig.processing.enableResultCaching
    }
}

/**
 * Request object for cache operations
 */
data class AnalysisCacheRequest(
    val screenshotUrl: String,
    val analysisType: AnalysisType,
    val language: String = "en",
    val options: Map<String, String>? = null
)

/**
 * Cached analysis result with metadata
 */
@Serializable
data class CachedAnalysisResult(
    val jobId: String,
    val analysisType: AnalysisType,
    val processingTimeMs: Long,
    val tokensUsed: Int,
    val costUsd: Double,
    val timestamp: Instant,
    val cachedAt: Instant,
    val data: CachedAnalysisData,
    val confidence: Double,
    val metadata: Map<String, String> = emptyMap(),
    val screenshotUrl: String,
    val language: String
) {
    companion object {
        fun fromAnalysisResult(result: AnalysisResult.Success, request: AnalysisCacheRequest): CachedAnalysisResult {
            return CachedAnalysisResult(
                jobId = result.jobId,
                analysisType = result.analysisType,
                processingTimeMs = result.processingTimeMs,
                tokensUsed = result.tokensUsed,
                costUsd = result.costUsd,
                timestamp = result.timestamp,
                cachedAt = Clock.System.now(),
                data = CachedAnalysisData.fromAnalysisData(result.data),
                confidence = result.confidence,
                metadata = result.metadata,
                screenshotUrl = request.screenshotUrl,
                language = request.language
            )
        }
    }
    
    fun toAnalysisResult(): AnalysisResult.Success {
        return AnalysisResult.Success(
            jobId = jobId,
            analysisType = analysisType,
            processingTimeMs = processingTimeMs,
            tokensUsed = tokensUsed,
            costUsd = costUsd,
            timestamp = timestamp,
            data = data.toAnalysisData(),
            confidence = confidence,
            metadata = metadata + mapOf(
                "cached" to "true",
                "cachedAt" to cachedAt.toString(),
                "cacheAge" to "${(Clock.System.now() - cachedAt).inWholeMinutes}min"
            )
        )
    }
}

/**
 * Serializable version of AnalysisData
 */
@Serializable
data class CachedAnalysisData(
    val type: String,
    val content: Map<String, String>
) {
    companion object {
        fun fromAnalysisData(data: AnalysisData): CachedAnalysisData {
            return when (data) {
                is AnalysisData.OcrData -> CachedAnalysisData(
                    type = "ocr",
                    content = mapOf(
                        "text" to data.text,
                        "language" to data.language,
                        "lineCount" to data.lines.size.toString()
                    )
                )
                is AnalysisData.UxAnalysisData -> CachedAnalysisData(
                    type = "ux",
                    content = mapOf(
                        "accessibilityScore" to data.accessibilityScore.toString(),
                        "issueCount" to data.issues.size.toString(),
                        "readabilityScore" to data.readabilityScore.toString()
                    )
                )
                is AnalysisData.ContentSummaryData -> CachedAnalysisData(
                    type = "content",
                    content = mapOf(
                        "summary" to data.summary,
                        "sentiment" to data.sentiment.overall,
                        "topicCount" to data.topics.size.toString()
                    )
                )
                is AnalysisData.GeneralData -> CachedAnalysisData(
                    type = "general",
                    content = data.results.mapValues { it.value.toString() }
                )
            }
        }
    }
    
    fun toAnalysisData(): AnalysisData {
        return when (type) {
            "ocr" -> AnalysisData.OcrData(
                text = content["text"] ?: "",
                lines = emptyList(), // TODO: Could cache lines if needed
                language = content["language"] ?: "en",
                structuredData = emptyMap()
            )
            "ux" -> AnalysisData.UxAnalysisData(
                accessibilityScore = content["accessibilityScore"]?.toDoubleOrNull() ?: 0.0,
                issues = emptyList(), // TODO: Could cache issues if needed
                recommendations = emptyList(),
                colorContrast = emptyMap(),
                readabilityScore = content["readabilityScore"]?.toDoubleOrNull() ?: 0.0
            )
            "content" -> AnalysisData.ContentSummaryData(
                summary = content["summary"] ?: "",
                keyPoints = emptyList(),
                entities = emptyList(),
                sentiment = SentimentAnalysis(content["sentiment"] ?: "neutral", 0.0),
                topics = emptyList()
            )
            else -> AnalysisData.GeneralData(
                results = content
            )
        }
    }
}

/**
 * Cache statistics for monitoring
 */
data class AnalysisCacheStats(
    val totalRequests: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val hitRate: Double,
    val avgLookupTimeMs: Double,
    val totalCachedResults: Long,
    val cacheSize: Long
)