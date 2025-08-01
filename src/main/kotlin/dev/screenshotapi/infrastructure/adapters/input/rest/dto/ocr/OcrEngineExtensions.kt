package dev.screenshotapi.infrastructure.adapters.input.rest.dto.ocr

import dev.screenshotapi.core.domain.entities.OcrEngine
import dev.screenshotapi.core.domain.entities.OcrTier

/**
 * OCR Engine Extensions - Infrastructure layer display information
 * GitHub Issue #5: Create OCR API endpoints and documentation
 */

fun OcrEngine.getDisplayName(): String = when (this) {
    OcrEngine.PADDLE_OCR -> "PaddleOCR"
    OcrEngine.TESSERACT -> "Tesseract"
    OcrEngine.EASY_OCR -> "EasyOCR"
    OcrEngine.GPT4_VISION -> "GPT-4 Vision"
    OcrEngine.CLAUDE_VISION -> "Claude Vision"
    OcrEngine.GEMINI_VISION -> "Gemini Vision"
    OcrEngine.LLAMA_VISION -> "Llama 3.2 Vision"
}

fun OcrEngine.getDescription(): String = when (this) {
    OcrEngine.PADDLE_OCR -> "Free, high-performance OCR engine with multilingual support"
    OcrEngine.TESSERACT -> "Google's open-source OCR engine"
    OcrEngine.EASY_OCR -> "Easy-to-use OCR with deep learning"
    OcrEngine.GPT4_VISION -> "OpenAI's GPT-4 with vision capabilities for advanced text extraction"
    OcrEngine.CLAUDE_VISION -> "Anthropic's Claude with vision for accurate text analysis"
    OcrEngine.GEMINI_VISION -> "Google's Gemini with multimodal capabilities"
    OcrEngine.LLAMA_VISION -> "Meta's Llama 3.2 with vision, running locally"
}

fun OcrEngine.getSupportedLanguages(): List<String> = when (this) {
    OcrEngine.PADDLE_OCR -> listOf(
        "en", "ch", "ta", "te", "ka", "ja", "ko", 
        "hi", "ar", "fr", "es", "pt", "de", "it", "ru"
    )
    OcrEngine.TESSERACT -> listOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh"
    )
    OcrEngine.EASY_OCR -> listOf(
        "en", "zh", "ja", "ko", "th", "vi", "ar", "hi", "ta", "te"
    )
    OcrEngine.GPT4_VISION -> listOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "ar", "hi"
    )
    OcrEngine.CLAUDE_VISION -> listOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh"
    )
    OcrEngine.GEMINI_VISION -> listOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "hi", "ar"
    )
    OcrEngine.LLAMA_VISION -> listOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh"
    )
}

fun OcrEngine.getSupportedTiers(): List<String> = when (this) {
    OcrEngine.PADDLE_OCR -> listOf("BASIC")
    OcrEngine.TESSERACT -> listOf("BASIC")
    OcrEngine.EASY_OCR -> listOf("BASIC")
    OcrEngine.GPT4_VISION -> listOf("AI_STANDARD", "AI_PREMIUM")
    OcrEngine.CLAUDE_VISION -> listOf("AI_PREMIUM", "AI_ELITE")
    OcrEngine.GEMINI_VISION -> listOf("AI_STANDARD", "AI_PREMIUM")
    OcrEngine.LLAMA_VISION -> listOf("LOCAL_AI")
}

fun OcrEngine.supportsStructuredData(): Boolean = when (this) {
    OcrEngine.PADDLE_OCR -> false
    OcrEngine.TESSERACT -> false
    OcrEngine.EASY_OCR -> false
    OcrEngine.GPT4_VISION -> true
    OcrEngine.CLAUDE_VISION -> true
    OcrEngine.GEMINI_VISION -> true
    OcrEngine.LLAMA_VISION -> true
}

fun OcrEngine.supportsTables(): Boolean = when (this) {
    OcrEngine.PADDLE_OCR -> false
    OcrEngine.TESSERACT -> false
    OcrEngine.EASY_OCR -> false
    OcrEngine.GPT4_VISION -> true
    OcrEngine.CLAUDE_VISION -> true
    OcrEngine.GEMINI_VISION -> true
    OcrEngine.LLAMA_VISION -> true
}

fun OcrEngine.supportsForms(): Boolean = when (this) {
    OcrEngine.PADDLE_OCR -> false
    OcrEngine.TESSERACT -> false
    OcrEngine.EASY_OCR -> false
    OcrEngine.GPT4_VISION -> true
    OcrEngine.CLAUDE_VISION -> true
    OcrEngine.GEMINI_VISION -> true
    OcrEngine.LLAMA_VISION -> true
}

fun OcrEngine.supportsHandwriting(): Boolean = when (this) {
    OcrEngine.PADDLE_OCR -> false
    OcrEngine.TESSERACT -> false
    OcrEngine.EASY_OCR -> false
    OcrEngine.GPT4_VISION -> true
    OcrEngine.CLAUDE_VISION -> true
    OcrEngine.GEMINI_VISION -> true
    OcrEngine.LLAMA_VISION -> true
}

fun OcrEngine.isLocal(): Boolean = when (this) {
    OcrEngine.PADDLE_OCR -> true
    OcrEngine.TESSERACT -> true
    OcrEngine.EASY_OCR -> true
    OcrEngine.GPT4_VISION -> false
    OcrEngine.CLAUDE_VISION -> false
    OcrEngine.GEMINI_VISION -> false
    OcrEngine.LLAMA_VISION -> true
}

fun OcrEngine.requiresApiKey(): Boolean = when (this) {
    OcrEngine.PADDLE_OCR -> false
    OcrEngine.TESSERACT -> false
    OcrEngine.EASY_OCR -> false
    OcrEngine.GPT4_VISION -> true
    OcrEngine.CLAUDE_VISION -> true
    OcrEngine.GEMINI_VISION -> true
    OcrEngine.LLAMA_VISION -> false
}

fun OcrEngine.getAverageAccuracy(): Double = when (this) {
    OcrEngine.PADDLE_OCR -> 0.92
    OcrEngine.TESSERACT -> 0.88
    OcrEngine.EASY_OCR -> 0.90
    OcrEngine.GPT4_VISION -> 0.96
    OcrEngine.CLAUDE_VISION -> 0.97
    OcrEngine.GEMINI_VISION -> 0.95
    OcrEngine.LLAMA_VISION -> 0.93
}

fun OcrEngine.getAverageProcessingTime(): Double = when (this) {
    OcrEngine.PADDLE_OCR -> 2.5
    OcrEngine.TESSERACT -> 3.0
    OcrEngine.EASY_OCR -> 2.8
    OcrEngine.GPT4_VISION -> 8.0
    OcrEngine.CLAUDE_VISION -> 6.5
    OcrEngine.GEMINI_VISION -> 7.0
    OcrEngine.LLAMA_VISION -> 4.0
}

fun OcrEngine.getMaxImageSize(): Long = when (this) {
    OcrEngine.PADDLE_OCR -> 10 * 1024 * 1024 // 10MB
    OcrEngine.TESSERACT -> 8 * 1024 * 1024   // 8MB
    OcrEngine.EASY_OCR -> 10 * 1024 * 1024   // 10MB
    OcrEngine.GPT4_VISION -> 20 * 1024 * 1024 // 20MB
    OcrEngine.CLAUDE_VISION -> 20 * 1024 * 1024 // 20MB
    OcrEngine.GEMINI_VISION -> 20 * 1024 * 1024 // 20MB
    OcrEngine.LLAMA_VISION -> 15 * 1024 * 1024  // 15MB
}