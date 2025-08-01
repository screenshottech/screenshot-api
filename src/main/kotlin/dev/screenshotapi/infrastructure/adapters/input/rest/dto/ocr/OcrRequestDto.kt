package dev.screenshotapi.infrastructure.adapters.input.rest.dto.ocr

import dev.screenshotapi.core.domain.entities.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.util.*

/**
 * OCR Request DTOs - Infrastructure layer serialization
 * GitHub Issue #5: Create OCR API endpoints and documentation
 */

@Serializable
data class OcrExtractRequestDto(
    val imageData: String, // Base64 encoded image data
    val language: String = "en",
    val tier: String = "BASIC", // BASIC, LOCAL_AI, AI_STANDARD, AI_PREMIUM, AI_ELITE
    val analysisType: String? = null, // BASIC_OCR, UX_ANALYSIS, CONTENT_SUMMARY, GENERAL
    val engine: String? = null, // Optional engine specification
    val extractPrices: Boolean = false,
    val extractTables: Boolean = false,
    val extractForms: Boolean = false,
    val confidenceThreshold: Double = 0.8,
    val preprocessImage: Boolean = true,
    val enhanceContrast: Boolean = false,
    val deskewImage: Boolean = false,
    val removeNoise: Boolean = false,
    val apiKeyId: String? = null // For API key ownership validation
) {
    fun toDomainRequest(userId: String): OcrRequest {
        // Decode base64 image data
        val imageBytes = try {
            java.util.Base64.getDecoder().decode(imageData)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid base64 image data")
        }

        // Parse tier
        val ocrTier = try {
            OcrTier.valueOf(tier.uppercase())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid OCR tier: $tier")
        }

        // Parse engine if provided
        val ocrEngine = engine?.let {
            try {
                OcrEngine.valueOf(it.uppercase())
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid OCR engine: $it")
            }
        }

        // Parse analysis type if provided
        val ocrAnalysisType = analysisType?.let {
            try {
                AnalysisType.valueOf(it.uppercase())
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid analysis type: $it")
            }
        }

        // Determine use case
        val useCase = when {
            extractPrices -> OcrUseCase.PRICE_MONITORING
            extractTables -> OcrUseCase.TABLE_EXTRACTION
            extractForms -> OcrUseCase.FORM_PROCESSING
            else -> OcrUseCase.GENERAL
        }

        return OcrRequest(
            id = UUID.randomUUID().toString(),
            userId = userId,
            screenshotJobId = null,
            imageBytes = imageBytes,
            language = language,
            tier = ocrTier,
            engine = ocrEngine,
            useCase = useCase,
            analysisType = ocrAnalysisType,
            options = OcrOptions(
                extractPrices = extractPrices,
                extractTables = extractTables,
                extractForms = extractForms,
                confidenceThreshold = confidenceThreshold,
                enableStructuredData = extractPrices || extractTables || extractForms
            )
        )
    }
}

@Serializable
data class OcrBulkExtractRequestDto(
    val images: List<OcrImageDto>,
    val language: String = "en",
    val tier: String = "BASIC",
    val analysisType: String? = null, // BASIC_OCR, UX_ANALYSIS, CONTENT_SUMMARY, GENERAL
    val engine: String? = null,
    val extractPrices: Boolean = false,
    val extractTables: Boolean = false,
    val extractForms: Boolean = false,
    val confidenceThreshold: Double = 0.8,
    val preprocessImage: Boolean = true,
    val apiKeyId: String? = null
)

@Serializable
data class OcrImageDto(
    val id: String, // User-provided identifier
    val imageData: String, // Base64 encoded image data
    val filename: String? = null
)