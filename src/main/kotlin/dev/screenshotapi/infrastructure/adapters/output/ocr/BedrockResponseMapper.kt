package dev.screenshotapi.infrastructure.adapters.output.ocr

import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.entities.OcrEngine
import dev.screenshotapi.core.domain.entities.OcrResult
import dev.screenshotapi.core.domain.entities.OcrTextLine
import dev.screenshotapi.core.domain.entities.OcrBoundingBox
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class BedrockResponseMapper {
    
    private val logger = LoggerFactory.getLogger(BedrockResponseMapper::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    fun mapBedrockResponse(
        bedrockResponse: BedrockResponse,
        analysisType: AnalysisType,
        processingTime: Long
    ): OcrResult {
        return try {
            val content = bedrockResponse.content
            val confidence = calculateConfidence(bedrockResponse.outputTokens)
            
            OcrResult(
                id = "",
                userId = "",
                success = true,
                extractedText = content,
                confidence = confidence,
                wordCount = content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size,
                lines = extractTextLines(content, analysisType),
                processingTime = processingTime / 1000.0, // convert to seconds
                language = "en",
                engine = OcrEngine.CLAUDE_VISION,
                structuredData = null,
                metadata = mapOf(
                    "analysisType" to analysisType.name,
                    "modelId" to bedrockResponse.model,
                    "inputTokens" to bedrockResponse.inputTokens.toString(),
                    "outputTokens" to bedrockResponse.outputTokens.toString(),
                    "processingTimeMs" to processingTime.toString()
                ),
                createdAt = kotlinx.datetime.Clock.System.now(),
                error = null
            )
        } catch (e: Exception) {
            logger.error("Failed to map Bedrock response", e)
            createErrorResult(e.message ?: "Unknown error", processingTime)
        }
    }
    
    private fun extractContent(jsonResponse: JsonObject): String {
        return jsonResponse["content"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?: throw IllegalArgumentException("No content found in response")
    }
    
    private fun calculateConfidence(outputTokens: Int): Double {
        return (outputTokens.coerceIn(10, 1000) / 1000.0) * 0.95
    }
    
    private fun extractTextLines(content: String, analysisType: AnalysisType): List<OcrTextLine> {
        return if (analysisType == AnalysisType.BASIC_OCR) {
            content.split("\n")
                .filter { it.isNotBlank() }
                .map { line ->
                    OcrTextLine(
                        text = line.trim(),
                        confidence = 0.90,
                        boundingBox = OcrBoundingBox(0, 0, 0, 0, 0, 0),
                        wordCount = line.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                    )
                }
        } else {
            listOf(
                OcrTextLine(
                    text = content,
                    confidence = 0.95,
                    boundingBox = OcrBoundingBox(0, 0, 0, 0, 0, 0),
                    wordCount = content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                )
            )
        }
    }
    
    private fun createErrorResult(errorMessage: String, processingTime: Long): OcrResult {
        return OcrResult(
            id = "",
            userId = "",
            success = false,
            extractedText = "",
            confidence = 0.0,
            wordCount = 0,
            lines = emptyList(),
            processingTime = processingTime / 1000.0, // convert to seconds
            language = "en",
            engine = OcrEngine.CLAUDE_VISION,
            structuredData = null,
            metadata = mapOf(
                "error" to errorMessage,
                "processingTimeMs" to processingTime.toString()
            ),
            createdAt = kotlinx.datetime.Clock.System.now(),
            error = errorMessage
        )
    }
}