package dev.screenshotapi.core.domain.services

import dev.screenshotapi.core.domain.entities.OcrRequest
import dev.screenshotapi.core.domain.entities.OcrResult
import dev.screenshotapi.core.domain.entities.OcrEngine
import dev.screenshotapi.core.domain.entities.OcrTier

/**
 * OCR Service Interface - Port for OCR processing
 * GitHub Issue #2: OCR Domain Architecture
 */
interface OcrService {
    
    /**
     * Extract text from image using specified or auto-selected OCR engine
     */
    suspend fun extractText(request: OcrRequest): OcrResult
    
    /**
     * Check if specific OCR engine is available
     */
    suspend fun isEngineAvailable(engine: OcrEngine): Boolean
    
    /**
     * Get recommended engine for given tier
     */
    fun getRecommendedEngine(tier: OcrTier): OcrEngine
    
    /**
     * Get processing capabilities for engine
     */
    fun getEngineCapabilities(engine: OcrEngine): OcrEngineCapabilities
}

/**
 * OCR Engine Capabilities
 */
data class OcrEngineCapabilities(
    val engine: OcrEngine,
    val supportedLanguages: List<String>,
    val supportsStructuredData: Boolean,
    val supportsTables: Boolean,
    val supportsForms: Boolean,
    val supportsHandwriting: Boolean,
    val averageAccuracy: Double,
    val averageProcessingTime: Double, // seconds
    val costPerRequest: Double, // in USD
    val maxImageSize: Long, // bytes
    val isLocal: Boolean,
    val requiresApiKey: Boolean
)