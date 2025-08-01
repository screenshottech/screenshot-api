package dev.screenshotapi.infrastructure.adapters.input.rest.dto.ocr

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.OcrAnalytics
import kotlinx.serialization.Serializable

/**
 * OCR Response DTOs - Infrastructure layer serialization
 * GitHub Issue #5: Create OCR API endpoints and documentation
 */

@Serializable
data class OcrResultDto(
    val id: String,
    val success: Boolean,
    val extractedText: String,
    val confidence: Double,
    val wordCount: Int,
    val lines: List<OcrTextLineDto>,
    val processingTime: Double,
    val language: String,
    val engine: String,
    val structuredData: OcrStructuredDataDto? = null,
    val createdAt: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class OcrTextLineDto(
    val text: String,
    val confidence: Double,
    val boundingBox: OcrBoundingBoxDto,
    val wordCount: Int
)

@Serializable
data class OcrBoundingBoxDto(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val page: Int
)

@Serializable
data class OcrStructuredDataDto(
    val tables: List<OcrTableDto> = emptyList(),
    val forms: List<OcrFormDto> = emptyList(),
    val prices: List<OcrPriceDto> = emptyList()
)

@Serializable
data class OcrTableDto(
    val headers: List<String>,
    val rows: List<List<String>>,
    val confidence: Double,
    val boundingBox: OcrBoundingBoxDto
)

@Serializable
data class OcrFormDto(
    val fields: List<OcrFormFieldDto>,
    val confidence: Double,
    val boundingBox: OcrBoundingBoxDto
)

@Serializable
data class OcrFormFieldDto(
    val label: String,
    val value: String,
    val confidence: Double,
    val fieldType: String, // TEXT, EMAIL, PHONE, DATE, etc.
    val boundingBox: OcrBoundingBoxDto
)

@Serializable
data class OcrPriceDto(
    val value: String,
    val numericValue: Double?,
    val currency: String?,
    val confidence: Double,
    val boundingBox: OcrBoundingBoxDto
)

@Serializable
data class OcrResultListResponseDto(
    val results: List<OcrResultDto>,
    val pagination: PaginationDto
)

@Serializable
data class PaginationDto(
    val offset: Int,
    val limit: Int,
    val total: Int,
    val hasMore: Boolean
)

@Serializable
data class OcrAnalyticsDto(
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val successRate: Double,
    val averageConfidence: Double,
    val averageProcessingTime: Double,
    val totalWordsExtracted: Long,
    val engineUsage: Map<String, Long>,
    val tierUsage: Map<String, Long>,
    val languageUsage: Map<String, Long>,
    val topErrors: List<OcrErrorSummaryDto>
)

@Serializable
data class OcrErrorSummaryDto(
    val errorType: String,
    val count: Long,
    val percentage: Double
)

@Serializable
data class OcrEnginesResponseDto(
    val engines: List<OcrEngineInfoDto>
)

@Serializable
data class OcrEngineInfoDto(
    val engine: String,
    val displayName: String,
    val description: String,
    val supportedLanguages: List<String>,
    val supportedTiers: List<String>,
    val capabilities: OcrCapabilitiesDto,
    val averageAccuracy: Double,
    val averageProcessingTime: Double,
    val maxImageSize: Long
)

@Serializable
data class OcrCapabilitiesDto(
    val supportsStructuredData: Boolean,
    val supportsTables: Boolean,
    val supportsForms: Boolean,
    val supportsHandwriting: Boolean,
    val isLocal: Boolean,
    val requiresApiKey: Boolean
)

@Serializable
data class OcrBulkResultDto(
    val jobId: String,
    val status: String, // PROCESSING, COMPLETED, FAILED
    val results: List<OcrImageResultDto>,
    val totalImages: Int,
    val processedImages: Int,
    val failedImages: Int,
    val createdAt: String,
    val completedAt: String? = null
)

@Serializable
data class OcrImageResultDto(
    val id: String, // User-provided identifier
    val success: Boolean,
    val result: OcrResultDto? = null,
    val error: String? = null
)

// Extension functions for mapping domain entities to DTOs
fun OcrResult.toDto(): OcrResultDto = OcrResultDto(
    id = id,
    success = success,
    extractedText = extractedText,
    confidence = confidence,
    wordCount = wordCount,
    lines = lines.map { it.toDto() },
    processingTime = processingTime,
    language = language,
    engine = engine.name,
    structuredData = structuredData?.toDto(),
    createdAt = createdAt.toString(),
    metadata = metadata
)

fun OcrTextLine.toDto(): OcrTextLineDto = OcrTextLineDto(
    text = text,
    confidence = confidence,
    boundingBox = boundingBox.toDto(),
    wordCount = wordCount
)

fun OcrBoundingBox.toDto(): OcrBoundingBoxDto = OcrBoundingBoxDto(
    x = x1,
    y = y1,
    width = width,
    height = height,
    rotation = 0, // Not supported in current domain model
    page = 0      // Not supported in current domain model
)

fun OcrStructuredData.toDto(): OcrStructuredDataDto = OcrStructuredDataDto(
    tables = tables.map { it.toDto() },
    forms = forms.map { it.toDto() },
    prices = prices.map { it.toDto() }
)

fun OcrTable.toDto(): OcrTableDto = OcrTableDto(
    headers = emptyList(), // Domain model doesn't have headers concept yet
    rows = rows.map { row -> row.cells.map { cell -> cell.text } },
    confidence = confidence,
    boundingBox = boundingBox.toDto()
)

fun OcrForm.toDto(): OcrFormDto = OcrFormDto(
    fields = fields.map { it.toDto() },
    confidence = confidence,
    boundingBox = boundingBox.toDto()
)

fun OcrFormField.toDto(): OcrFormFieldDto = OcrFormFieldDto(
    label = label,
    value = value,
    confidence = confidence,
    fieldType = fieldType,
    boundingBox = boundingBox.toDto()
)

fun OcrPrice.toDto(): OcrPriceDto = OcrPriceDto(
    value = value,
    numericValue = numericValue,
    currency = currency,
    confidence = confidence,
    boundingBox = boundingBox.toDto()
)

fun OcrAnalytics.toDto(): OcrAnalyticsDto = OcrAnalyticsDto(
    totalRequests = totalRequests,
    successfulRequests = successfulRequests,
    failedRequests = failedRequests,
    successRate = if (totalRequests > 0) {
        (successfulRequests.toDouble() / totalRequests.toDouble()) * 100
    } else 0.0,
    averageConfidence = averageConfidence,
    averageProcessingTime = averageProcessingTime,
    totalWordsExtracted = totalWordsExtracted,
    engineUsage = engineUsage.mapKeys { it.key.name },
    tierUsage = tierUsage.mapKeys { it.key.name },
    languageUsage = languageUsage,
    topErrors = topErrors.map { OcrErrorSummaryDto(it.errorType, it.count, it.percentage) }
)