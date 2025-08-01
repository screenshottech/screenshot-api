package dev.screenshotapi.core.services

import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.entities.OcrResult
import dev.screenshotapi.infrastructure.utils.JsonSerializationUtils

class AnalysisResultFormatter {

    fun formatResult(analysisType: AnalysisType, ocrResult: OcrResult): String {
        val resultMap = mutableMapOf<String, Any>(
            "extractedText" to ocrResult.extractedText,
            "confidence" to ocrResult.confidence,
            "wordCount" to ocrResult.wordCount,
            "language" to ocrResult.language,
            "engine" to ocrResult.engine.name,
            "analysisType" to analysisType.name
        )

        ocrResult.structuredData?.let { structured ->
            if (structured.prices.isNotEmpty()) {
                resultMap["prices"] = structured.prices.map { price ->
                    mapOf(
                        "value" to price.value,
                        "numericValue" to price.numericValue,
                        "currency" to price.currency,
                        "confidence" to price.confidence
                    )
                }
            }
        }

        if (ocrResult.metadata.isNotEmpty()) {
            resultMap["metadata"] = ocrResult.metadata
        }

        // Use centralized JSON serialization utility
        return JsonSerializationUtils.mapToJsonString(resultMap)
    }
}