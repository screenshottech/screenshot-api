package dev.screenshotapi.core.domain.entities

import kotlinx.serialization.Serializable

/**
 * OCR Engine Types - Different OCR engines available
 * GitHub Issue #2: OCR Domain Architecture
 */
@Serializable
enum class OcrEngine {
    PADDLE_OCR,     // PaddleOCR + MCP (primary for MVP)
    TESSERACT,      // Tesseract fallback
    EASY_OCR,       // EasyOCR alternative
    GPT4_VISION,    // GPT-4 Vision API (premium)
    CLAUDE_VISION,  // Claude 3.5 Sonnet Vision (premium)
    GEMINI_VISION,  // Gemini 1.5 Flash (cost-efficient AI)
    LLAMA_VISION    // Llama 3.2 Vision (local AI)
}

/**
 * OCR Tier - Service level tiers for different accuracy/cost needs
 */
@Serializable
enum class OcrTier {
    BASIC,          // 2 credits - PaddleOCR/Tesseract
    LOCAL_AI,       // 2 credits - Llama 3.2 Vision
    AI_STANDARD,    // 3 credits - Gemini Flash
    AI_PREMIUM,     // 5 credits - GPT-4 Vision
    AI_ELITE        // 5 credits - Claude Vision
}

/**
 * OCR Output Format - Different output format preferences
 */
@Serializable
enum class OcrOutputFormat {
    TEXT,           // Plain text only
    JSON,           // Raw OCR results
    STRUCTURED,     // Structured data with confidence
    MARKDOWN        // Formatted markdown
}

/**
 * OCR Use Case - Specific business use cases for optimization
 */
@Serializable
enum class OcrUseCase {
    GENERAL,            // General text extraction
    PRICE_MONITORING,   // E-commerce price extraction
    STATUS_MONITORING,  // Status page monitoring
    JOB_POSTING,        // Job posting analysis
    PRODUCT_LAUNCH,     // Product launch detection
    SOCIAL_MENTION,     // Social media mentions
    FORM_PROCESSING,    // Form field extraction
    TABLE_EXTRACTION    // Table data extraction
}