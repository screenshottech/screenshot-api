package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * OCR Result Entity - Core domain entity for OCR processing results
 * GitHub Issue #2: OCR Domain Architecture
 */
@Serializable
data class OcrResult(
    val id: String,
    val userId: String,
    val success: Boolean,
    val extractedText: String,
    val confidence: Double,
    val wordCount: Int,
    val lines: List<OcrTextLine>,
    val processingTime: Double, // seconds
    val language: String,
    val engine: OcrEngine,
    val structuredData: OcrStructuredData? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Instant,
    val error: String? = null
)

@Serializable
data class OcrTextLine(
    val text: String,
    val confidence: Double,
    val boundingBox: OcrBoundingBox,
    val wordCount: Int
)

@Serializable
data class OcrBoundingBox(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val width: Int,
    val height: Int
)

@Serializable
data class OcrStructuredData(
    val prices: List<OcrPrice> = emptyList(),
    val products: List<OcrProduct> = emptyList(),
    val emails: List<OcrEmail> = emptyList(),
    val phones: List<OcrPhone> = emptyList(),
    val urls: List<OcrUrl> = emptyList(),
    val tables: List<OcrTable> = emptyList(),
    val forms: List<OcrForm> = emptyList()
)

@Serializable
data class OcrPrice(
    val value: String,
    val numericValue: Double?,
    val currency: String?,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)

@Serializable
data class OcrProduct(
    val name: String,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)

@Serializable
data class OcrEmail(
    val email: String,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)

@Serializable
data class OcrPhone(
    val phone: String,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)

@Serializable
data class OcrUrl(
    val url: String,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)

@Serializable
data class OcrTable(
    val rows: List<OcrTableRow>,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)

@Serializable
data class OcrTableRow(
    val cells: List<OcrTableCell>
)

@Serializable
data class OcrTableCell(
    val text: String,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)

@Serializable
data class OcrForm(
    val fields: List<OcrFormField>,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)

@Serializable
data class OcrFormField(
    val label: String,
    val value: String,
    val fieldType: String,
    val confidence: Double,
    val boundingBox: OcrBoundingBox
)