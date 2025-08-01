package dev.screenshotapi.core.domain.entities

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.*

/**
 * OCR Request Entity Tests
 * GitHub Issue #6: Comprehensive OCR testing and quality assurance
 */
class OcrRequestTest {

    @Test
    fun `should create OCR request with required fields`() {
        // Arrange
        val testId = "ocr-request-123"
        val testUserId = "user-456"
        val testImageBytes = "test image data".toByteArray()
        val testLanguage = "en"
        val testTier = OcrTier.BASIC
        val testUseCase = OcrUseCase.GENERAL
        val testOptions = createBasicOcrOptions()

        // Act
        val request = OcrRequest(
            id = testId,
            userId = testUserId,
            imageBytes = testImageBytes,
            language = testLanguage,
            tier = testTier,
            useCase = testUseCase,
            options = testOptions
        )

        // Assert
        assertEquals(testId, request.id, "Should have correct ID")
        assertEquals(testUserId, request.userId, "Should have correct user ID")
        assertArrayEquals(testImageBytes, request.imageBytes, "Should have correct image bytes")
        assertEquals(testLanguage, request.language, "Should have correct language")
        assertEquals(testTier, request.tier, "Should have correct tier")
        assertEquals(testUseCase, request.useCase, "Should have correct use case")
        assertEquals(testOptions, request.options, "Should have correct options")
        assertNull(request.screenshotJobId, "Should have null screenshot job ID by default")
        assertNull(request.engine, "Should have null engine by default")
        assertNull(request.imageUrl, "Should have null image URL by default")
    }

    @Test
    fun `should create OCR request with optional fields`() {
        // Arrange
        val testScreenshotJobId = "job-789"
        val testEngine = OcrEngine.TESSERACT
        val testImageUrl = "https://example.com/image.png"

        // Act
        val request = OcrRequest(
            id = "ocr-request-456",
            userId = "user-789",
            imageUrl = testImageUrl,
            screenshotJobId = testScreenshotJobId,
            language = "es",
            tier = OcrTier.AI_PREMIUM,
            engine = testEngine,
            useCase = OcrUseCase.PRICE_MONITORING,
            options = createAdvancedOcrOptions()
        )

        // Assert
        assertEquals(testScreenshotJobId, request.screenshotJobId, "Should have correct screenshot job ID")
        assertEquals(testEngine, request.engine, "Should have correct engine")
        assertEquals(testImageUrl, request.imageUrl, "Should have correct image URL")
    }

    @Test
    fun `should create request for different OCR tiers`() {
        // Arrange
        val tierTestCases = listOf(
            OcrTier.BASIC to "Basic tier should be supported",
            OcrTier.LOCAL_AI to "Local AI tier should be supported",
            OcrTier.AI_STANDARD to "AI Standard tier should be supported",
            OcrTier.AI_PREMIUM to "AI Premium tier should be supported",
            OcrTier.AI_ELITE to "AI Elite tier should be supported"
        )

        tierTestCases.forEach { (tier, expectedMessage) ->
            // Act
            val request = OcrRequest(
                id = "ocr-${tier.name.lowercase()}",
                userId = "user-test",
                imageBytes = "test".toByteArray(),
                language = "en",
                tier = tier,
                useCase = OcrUseCase.GENERAL,
                options = createBasicOcrOptions()
            )

            // Assert
            assertEquals(tier, request.tier, expectedMessage)
        }
    }

    @Test
    fun `should create request for different OCR use cases`() {
        // Arrange
        val useCaseTestCases = listOf(
            OcrUseCase.GENERAL to "General use case should be supported",
            OcrUseCase.PRICE_MONITORING to "Price monitoring use case should be supported",
            OcrUseCase.TABLE_EXTRACTION to "Table extraction use case should be supported",
            OcrUseCase.FORM_PROCESSING to "Form processing use case should be supported",
            OcrUseCase.STATUS_MONITORING to "Status monitoring use case should be supported"
        )

        useCaseTestCases.forEach { (useCase, expectedMessage) ->
            // Act
            val request = OcrRequest(
                id = "ocr-${useCase.name.lowercase().replace('_', '-')}",
                userId = "user-test",
                imageBytes = "test".toByteArray(),
                language = "en",
                tier = OcrTier.BASIC,
                useCase = useCase,
                options = createOptionsForUseCase(useCase)
            )

            // Assert
            assertEquals(useCase, request.useCase, expectedMessage)
        }
    }

    @Test
    fun `should create request for different OCR engines`() {
        // Arrange
        val engineTestCases = listOf(
            OcrEngine.PADDLE_OCR to "PaddleOCR engine should be supported",
            OcrEngine.TESSERACT to "Tesseract engine should be supported",
            OcrEngine.GPT4_VISION to "GPT-4 Vision engine should be supported",
            OcrEngine.CLAUDE_VISION to "Claude Vision engine should be supported",
            OcrEngine.GEMINI_VISION to "Gemini Vision engine should be supported"
        )

        engineTestCases.forEach { (engine, expectedMessage) ->
            // Act
            val request = OcrRequest(
                id = "ocr-${engine.name.lowercase().replace('_', '-')}",
                userId = "user-test",
                imageBytes = "test".toByteArray(),
                language = "en",
                tier = OcrTier.BASIC,
                engine = engine,
                useCase = OcrUseCase.GENERAL,
                options = createBasicOcrOptions()
            )

            // Assert
            assertEquals(engine, request.engine, expectedMessage)
        }
    }

    @Test
    fun `should create request for different languages`() {
        // Arrange
        val languageTestCases = listOf(
            "en" to "English should be supported",
            "es" to "Spanish should be supported",
            "fr" to "French should be supported",
            "de" to "German should be supported",
            "zh" to "Chinese should be supported",
            "ja" to "Japanese should be supported",
            "ar" to "Arabic should be supported"
        )

        languageTestCases.forEach { (language, expectedMessage) ->
            // Act
            val request = OcrRequest(
                id = "ocr-$language",
                userId = "user-test",
                imageBytes = "test".toByteArray(),
                language = language,
                tier = OcrTier.BASIC,
                useCase = OcrUseCase.GENERAL,
                options = createBasicOcrOptions()
            )

            // Assert
            assertEquals(language, request.language, expectedMessage)
        }
    }

    @Test
    fun `should handle large image data`() {
        // Arrange
        val largeImageBytes = ByteArray(5 * 1024 * 1024) { it.toByte() } // 5MB test data

        // Act
        val request = OcrRequest(
            id = "ocr-large-image",
            userId = "user-test",
            imageBytes = largeImageBytes,
            language = "en",
            tier = OcrTier.AI_PREMIUM,
            useCase = OcrUseCase.GENERAL,
            options = createAdvancedOcrOptions()
        )

        // Assert
        assertEquals(5 * 1024 * 1024, request.imageBytes?.size, "Should handle large image data")
        assertEquals(OcrTier.AI_PREMIUM, request.tier, "Should use premium tier for large images")
    }

    @Test
    fun `should create request with advanced options`() {
        // Arrange
        val advancedOptions = OcrOptions(
            extractPrices = true,
            extractTables = true,
            extractForms = true,
            confidenceThreshold = 0.85,
            enableStructuredData = true,
            extractEmails = true,
            extractPhones = true,
            extractUrls = true
        )

        // Act
        val request = OcrRequest(
            id = "ocr-advanced",
            userId = "user-test",
            imageBytes = "test".toByteArray(),
            language = "en",
            tier = OcrTier.AI_PREMIUM,
            useCase = OcrUseCase.FORM_PROCESSING,
            options = advancedOptions
        )

        // Assert
        assertTrue(request.options.extractPrices, "Should extract prices")
        assertTrue(request.options.extractTables, "Should extract tables")
        assertTrue(request.options.extractForms, "Should extract forms")
        assertEquals(0.85, request.options.confidenceThreshold, "Should have correct confidence threshold")
        assertTrue(request.options.enableStructuredData, "Should enable structured data")
        assertTrue(request.options.extractEmails, "Should extract emails")
        assertTrue(request.options.extractPhones, "Should extract phones")
        assertTrue(request.options.extractUrls, "Should extract URLs")
    }

    @Test
    fun `should generate unique IDs for concurrent requests`() {
        // Arrange
        val requestIds = mutableSetOf<String>()
        val numberOfRequests = 100

        // Act
        repeat(numberOfRequests) {
            val request = OcrRequest(
                id = UUID.randomUUID().toString(),
                userId = "user-test",
                imageBytes = "test-$it".toByteArray(),
                language = "en",
                tier = OcrTier.BASIC,
                useCase = OcrUseCase.GENERAL,
                options = createBasicOcrOptions()
            )
            requestIds.add(request.id)
        }

        // Assert
        assertEquals(numberOfRequests, requestIds.size, "Should generate unique IDs for all requests")
    }

    @Test
    fun `should support both image bytes and image URL`() {
        // Arrange
        val imageBytes = "test image data".toByteArray()
        val imageUrl = "https://example.com/image.png"

        // Act
        val requestWithBytes = OcrRequest(
            id = "ocr-bytes",
            userId = "user-test",
            imageBytes = imageBytes,
            tier = OcrTier.BASIC
        )

        val requestWithUrl = OcrRequest(
            id = "ocr-url",
            userId = "user-test",
            imageUrl = imageUrl,
            tier = OcrTier.BASIC
        )

        // Assert
        assertNotNull(requestWithBytes.imageBytes, "Should support image bytes")
        assertNull(requestWithBytes.imageUrl, "Should have null URL when using bytes")
        
        assertNotNull(requestWithUrl.imageUrl, "Should support image URL")
        assertNull(requestWithUrl.imageBytes, "Should have null bytes when using URL")
    }

    // ===== HELPER METHODS =====

    private fun createBasicOcrOptions(): OcrOptions {
        return OcrOptions(
            extractPrices = false,
            extractTables = false,
            extractForms = false,
            confidenceThreshold = 0.8,
            enableStructuredData = false
        )
    }

    private fun createAdvancedOcrOptions(): OcrOptions {
        return OcrOptions(
            extractPrices = true,
            extractTables = true,
            extractForms = true,
            confidenceThreshold = 0.9,
            enableStructuredData = true,
            extractEmails = true,
            extractPhones = true,
            extractUrls = true
        )
    }

    private fun createOptionsForUseCase(useCase: OcrUseCase): OcrOptions {
        return when (useCase) {
            OcrUseCase.PRICE_MONITORING -> OcrOptions(
                extractPrices = true,
                enableStructuredData = true,
                confidenceThreshold = 0.8
            )
            OcrUseCase.TABLE_EXTRACTION -> OcrOptions(
                extractTables = true,
                enableStructuredData = true,
                confidenceThreshold = 0.85
            )
            OcrUseCase.FORM_PROCESSING -> OcrOptions(
                extractForms = true,
                enableStructuredData = true,
                confidenceThreshold = 0.9
            )
            else -> createBasicOcrOptions().copy(enableStructuredData = true)
        }
    }
}