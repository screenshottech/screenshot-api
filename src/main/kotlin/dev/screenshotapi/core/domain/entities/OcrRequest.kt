package dev.screenshotapi.core.domain.entities

import kotlinx.serialization.Serializable

/**
 * OCR Request - Domain entity for OCR processing requests
 * GitHub Issue #2: OCR Domain Architecture
 */
@Serializable
data class OcrRequest(
    val id: String,
    val userId: String,
    val imageBytes: ByteArray? = null,
    val imageUrl: String? = null,
    val screenshotJobId: String? = null,
    val tier: OcrTier = OcrTier.BASIC,
    val engine: OcrEngine? = null, // null = auto-select based on tier
    val language: String = "en",
    val outputFormat: OcrOutputFormat = OcrOutputFormat.STRUCTURED,
    val useCase: OcrUseCase = OcrUseCase.GENERAL,
    val analysisType: AnalysisType? = null, // null = use tier-based default
    val options: OcrOptions = OcrOptions()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OcrRequest

        if (id != other.id) return false
        if (userId != other.userId) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        if (imageUrl != other.imageUrl) return false
        if (screenshotJobId != other.screenshotJobId) return false
        if (tier != other.tier) return false
        if (engine != other.engine) return false
        if (language != other.language) return false
        if (outputFormat != other.outputFormat) return false
        if (useCase != other.useCase) return false
        if (analysisType != other.analysisType) return false
        if (options != other.options) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        result = 31 * result + (screenshotJobId?.hashCode() ?: 0)
        result = 31 * result + tier.hashCode()
        result = 31 * result + (engine?.hashCode() ?: 0)
        result = 31 * result + language.hashCode()
        result = 31 * result + outputFormat.hashCode()
        result = 31 * result + useCase.hashCode()
        result = 31 * result + (analysisType?.hashCode() ?: 0)
        result = 31 * result + options.hashCode()
        return result
    }
}

@Serializable
data class OcrOptions(
    val enableStructuredData: Boolean = true,
    val extractPrices: Boolean = false,
    val extractEmails: Boolean = false,
    val extractPhones: Boolean = false,
    val extractUrls: Boolean = false,
    val extractTables: Boolean = false,
    val extractForms: Boolean = false,
    val confidenceThreshold: Double = 0.7,
    val enableAngleClassification: Boolean = true,
    val customPrompt: String? = null, // For AI models
    val maxProcessingTime: Int = 30 // seconds
)