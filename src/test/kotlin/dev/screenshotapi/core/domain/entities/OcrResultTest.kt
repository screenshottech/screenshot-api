package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * OCR Result Entity Tests
 * GitHub Issue #6: Comprehensive OCR testing and quality assurance
 */
class OcrResultTest {

    @Test
    fun `should create OCR result with minimal required fields`() {
        // Arrange
        val testId = "ocr-result-123"
        val testText = "Extracted text content"
        val testConfidence = 0.95
        val testWordCount = 3
        val testLines = listOf(createTestOcrTextLine())
        val testProcessingTime = 1.5
        val testLanguage = "en"
        val testEngine = OcrEngine.PADDLE_OCR
        val testCreatedAt = Clock.System.now()

        // Act
        val result = OcrResult(
            id = testId,
            userId = "test-user-id",
            success = true,
            extractedText = testText,
            confidence = testConfidence,
            wordCount = testWordCount,
            lines = testLines,
            processingTime = testProcessingTime,
            language = testLanguage,
            engine = testEngine,
            createdAt = testCreatedAt
        )

        // Assert
        assertEquals(testId, result.id, "Should have correct ID")
        assertTrue(result.success, "Should indicate success")
        assertEquals(testText, result.extractedText, "Should have correct extracted text")
        assertEquals(testConfidence, result.confidence, "Should have correct confidence")
        assertEquals(testWordCount, result.wordCount, "Should have correct word count")
        assertEquals(testLines, result.lines, "Should have correct lines")
        assertEquals(testProcessingTime, result.processingTime, "Should have correct processing time")
        assertEquals(testLanguage, result.language, "Should have correct language")
        assertEquals(testEngine, result.engine, "Should have correct engine")
        assertEquals(testCreatedAt, result.createdAt, "Should have correct creation time")
        assertNull(result.structuredData, "Should have null structured data by default")
        assertEquals(emptyMap<String, String>(), result.metadata, "Should have empty metadata by default")
        assertNull(result.error, "Should have null error by default")
    }

    @Test
    fun `should create OCR result with optional fields`() {
        // Arrange
        val testStructuredData = createTestStructuredData()
        val testMetadata = mapOf(
            "userId" to "user-123",
            "screenshotJobId" to "job-456",
            "confidence_threshold" to "0.8"
        )
        val testError = "Processing warning: low quality image"

        // Act
        val result = OcrResult(
            id = "ocr-result-123",
            userId = "test-user-id",
            success = true,
            extractedText = "Test text",
            confidence = 0.85,
            wordCount = 2,
            lines = listOf(createTestOcrTextLine()),
            processingTime = 2.0,
            language = "es",
            engine = OcrEngine.TESSERACT,
            structuredData = testStructuredData,
            metadata = testMetadata,
            createdAt = Clock.System.now(),
            error = testError
        )

        // Assert
        assertEquals(testStructuredData, result.structuredData, "Should have correct structured data")
        assertEquals(testMetadata, result.metadata, "Should have correct metadata")
        assertEquals(testError, result.error, "Should have correct error message")
    }

    @Test
    fun `should create failed OCR result`() {
        // Arrange
        val testId = "ocr-failed-123"
        val testError = "Image processing failed: unsupported format"
        val testCreatedAt = Clock.System.now()

        // Act
        val result = OcrResult(
            id = testId,
            userId = "test-user-id",
            success = false,
            extractedText = "",
            confidence = 0.0,
            wordCount = 0,
            lines = emptyList(),
            processingTime = 0.5,
            language = "en",
            engine = OcrEngine.PADDLE_OCR,
            createdAt = testCreatedAt,
            error = testError
        )

        // Assert
        assertFalse(result.success, "Should indicate failure")
        assertEquals("", result.extractedText, "Should have empty extracted text")
        assertEquals(0.0, result.confidence, "Should have zero confidence")
        assertEquals(0, result.wordCount, "Should have zero word count")
        assertTrue(result.lines.isEmpty(), "Should have empty lines")
        assertEquals(testError, result.error, "Should have correct error message")
    }

    @Test
    fun `should handle high confidence results`() {
        // Arrange
        val highConfidenceLines = listOf(
            createTestOcrTextLine("High quality text", 0.98),
            createTestOcrTextLine("Perfect recognition", 0.99),
            createTestOcrTextLine("Clear content", 0.97)
        )

        // Act
        val result = OcrResult(
            id = "ocr-high-confidence",
            userId = "test-user-id",
            success = true,
            extractedText = "High quality text Perfect recognition Clear content",
            confidence = 0.98,
            wordCount = 8,
            lines = highConfidenceLines,
            processingTime = 1.2,
            language = "en",
            engine = OcrEngine.PADDLE_OCR,
            createdAt = Clock.System.now()
        )

        // Assert
        assertTrue(result.confidence >= 0.95, "Should have high confidence")
        assertEquals(3, result.lines.size, "Should have correct number of lines")
        assertTrue(result.lines.all { it.confidence >= 0.95 }, "All lines should have high confidence")
    }

    @Test
    fun `should handle low confidence results`() {
        // Arrange
        val lowConfidenceLines = listOf(
            createTestOcrTextLine("blurry text", 0.45),
            createTestOcrTextLine("unclear words", 0.38),
            createTestOcrTextLine("poor quality", 0.52)
        )

        // Act
        val result = OcrResult(
            id = "ocr-low-confidence",
            userId = "test-user-id",
            success = true,
            extractedText = "blurry text unclear words poor quality",
            confidence = 0.45,
            wordCount = 6,
            lines = lowConfidenceLines,
            processingTime = 3.5,
            language = "en",
            engine = OcrEngine.TESSERACT,
            createdAt = Clock.System.now(),
            error = "Low confidence recognition"
        )

        // Assert
        assertTrue(result.confidence < 0.6, "Should have low confidence")
        assertEquals(3, result.lines.size, "Should have correct number of lines")
        assertTrue(result.lines.all { it.confidence < 0.6 }, "All lines should have low confidence")
        assertNotNull(result.error, "Should have error message for low confidence")
    }

    @Test
    fun `should handle metadata with user and job information`() {
        // Arrange
        val testMetadata = mapOf(
            "userId" to "user-789",
            "screenshotJobId" to "job-abc123",
            "apiKeyId" to "key-xyz789",
            "tier" to "PREMIUM",
            "engine_version" to "2.1.0",
            "preprocessing" to "true"
        )

        // Act
        val result = OcrResult(
            id = "ocr-with-metadata",
            userId = "test-user-id",
            success = true,
            extractedText = "Text with metadata",
            confidence = 0.87,
            wordCount = 3,
            lines = listOf(createTestOcrTextLine()),
            processingTime = 1.8,
            language = "en",
            engine = OcrEngine.PADDLE_OCR,
            metadata = testMetadata,
            createdAt = Clock.System.now()
        )

        // Assert
        assertEquals("user-789", result.metadata["userId"], "Should have correct user ID")
        assertEquals("job-abc123", result.metadata["screenshotJobId"], "Should have correct job ID")
        assertEquals("key-xyz789", result.metadata["apiKeyId"], "Should have correct API key ID")
        assertEquals("PREMIUM", result.metadata["tier"], "Should have correct tier")
        assertEquals("2.1.0", result.metadata["engine_version"], "Should have correct engine version")
        assertEquals("true", result.metadata["preprocessing"], "Should have preprocessing flag")
    }

    // ===== HELPER METHODS =====

    private fun createTestOcrTextLine(
        text: String = "Sample text line",
        confidence: Double = 0.9
    ): OcrTextLine {
        return OcrTextLine(
            text = text,
            confidence = confidence,
            boundingBox = createTestBoundingBox(),
            wordCount = text.split(" ").size
        )
    }

    private fun createTestBoundingBox(): OcrBoundingBox {
        return OcrBoundingBox(
            x1 = 10,
            y1 = 20,
            x2 = 200,
            y2 = 40,
            width = 190,
            height = 20
        )
    }

    private fun createTestStructuredData(): OcrStructuredData {
        return OcrStructuredData(
            prices = listOf(
                OcrPrice(
                    value = "$19.99",
                    numericValue = 19.99,
                    currency = "USD",
                    confidence = 0.92,
                    boundingBox = createTestBoundingBox()
                )
            ),
            products = listOf(
                OcrProduct(
                    name = "Test Product",
                    confidence = 0.88,
                    boundingBox = createTestBoundingBox()
                )
            ),
            emails = listOf(
                OcrEmail(
                    email = "test@example.com",
                    confidence = 0.95,
                    boundingBox = createTestBoundingBox()
                )
            )
        )
    }
}
