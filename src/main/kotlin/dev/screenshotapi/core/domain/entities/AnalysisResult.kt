package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

sealed class AnalysisResult {
    abstract val jobId: String
    abstract val analysisType: AnalysisType
    abstract val processingTimeMs: Long
    abstract val tokensUsed: Int
    abstract val costUsd: Double
    abstract val timestamp: Instant
    
    data class Success(
        override val jobId: String,
        override val analysisType: AnalysisType,
        override val processingTimeMs: Long,
        override val tokensUsed: Int,
        override val costUsd: Double,
        override val timestamp: Instant,
        val data: AnalysisData,
        val confidence: Double,
        val metadata: Map<String, String> = emptyMap()
    ) : AnalysisResult()
    
    data class Failure(
        override val jobId: String,
        override val analysisType: AnalysisType,
        override val processingTimeMs: Long,
        override val tokensUsed: Int,
        override val costUsd: Double,
        override val timestamp: Instant,
        val error: AnalysisError,
        val retryable: Boolean = true
    ) : AnalysisResult()
    
    data class Partial(
        override val jobId: String,
        override val analysisType: AnalysisType,
        override val processingTimeMs: Long,
        override val tokensUsed: Int,
        override val costUsd: Double,
        override val timestamp: Instant,
        val data: AnalysisData,
        val confidence: Double,
        val missingElements: List<String>,
        val warnings: List<String> = emptyList()
    ) : AnalysisResult()
}

sealed class AnalysisData {
    data class OcrData(
        val text: String,
        val lines: List<OcrTextLine>,
        val language: String,
        val structuredData: Map<String, Any> = emptyMap()
    ) : AnalysisData()
    
    data class UxAnalysisData(
        val accessibilityScore: Double,
        val issues: List<UxIssue>,
        val recommendations: List<String>,
        val colorContrast: Map<String, Double>,
        val readabilityScore: Double
    ) : AnalysisData()
    
    data class ContentSummaryData(
        val summary: String,
        val keyPoints: List<String>,
        val entities: List<ExtractedEntity>,
        val sentiment: SentimentAnalysis,
        val topics: List<String>
    ) : AnalysisData()
    
    data class GeneralData(
        val results: Map<String, Any>
    ) : AnalysisData()
}

data class AnalysisError(
    val code: String,
    val message: String,
    val category: ErrorCategory,
    val details: Map<String, String> = emptyMap()
)

enum class ErrorCategory {
    VALIDATION,
    PROCESSING,
    EXTERNAL_SERVICE,
    RATE_LIMIT,
    AUTHENTICATION,
    CONFIGURATION,
    UNKNOWN
}

data class UxIssue(
    val severity: IssueSeverity,
    val category: String,
    val description: String,
    val element: String? = null,
    val recommendation: String
)

enum class IssueSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

data class ExtractedEntity(
    val type: String,
    val value: String,
    val confidence: Double
)

data class SentimentAnalysis(
    val overall: String,
    val score: Double,
    val breakdown: Map<String, Double> = emptyMap()
)