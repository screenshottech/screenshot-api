package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.*

/**
 * Extract Price Data Use Case Tests
 * GitHub Issue #6: Comprehensive OCR testing and quality assurance
 */
class ExtractPriceDataUseCaseTest {

    private lateinit var extractPriceDataUseCase: ExtractPriceDataUseCase
    private lateinit var mockExtractTextUseCase: ExtractTextUseCase
    private lateinit var mockLogUsageUseCase: LogUsageUseCase

    @BeforeEach
    fun setup() {
        mockExtractTextUseCase = mockk()
        mockLogUsageUseCase = mockk()

        extractPriceDataUseCase = ExtractPriceDataUseCase(
            extractTextUseCase = mockExtractTextUseCase,
            logUsageUseCase = mockLogUsageUseCase
        )
    }

    @Test
    fun `invoke should extract price data successfully from OCR result`() = runTest {
        // Arrange
        val testRequest = createPriceExtractionRequest()
        val ocrResultWithPrices = createOcrResultWithPrices()

        coEvery { mockExtractTextUseCase.invoke(any()) } returns ocrResultWithPrices
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractPriceDataUseCase.invoke(testRequest)

        // Assert
        assertTrue(result.success, "Should successfully extract price data")
        assertNotNull(result.structuredData, "Should have structured data")
        assertEquals(2, result.structuredData!!.prices.size, "Should extract correct number of prices")

        val firstPrice = result.structuredData!!.prices[0]
        assertEquals("$19.99", firstPrice.value, "Should extract correct price value")
        assertEquals(19.99, firstPrice.numericValue, "Should extract correct numeric value")
        assertEquals("USD", firstPrice.currency, "Should extract correct currency")
        assertTrue(firstPrice.confidence >= 0.8, "Should have reasonable confidence for price extraction")

        val secondPrice = result.structuredData!!.prices[1]
        assertEquals("€25.50", secondPrice.value, "Should extract second price value")
        assertEquals(25.50, secondPrice.numericValue, "Should extract second numeric value")
        assertEquals("EUR", secondPrice.currency, "Should extract second currency")

        coVerify { mockExtractTextUseCase.invoke(any()) }
        coVerify { mockLogUsageUseCase(match { it.action == UsageLogAction.OCR_PRICE_EXTRACTION }) }
    }

    @Test
    fun `invoke should handle OCR failure gracefully`() = runTest {
        // Arrange
        val testRequest = createPriceExtractionRequest()
        val failedOcrResult = createFailedOcrResult()

        coEvery { mockExtractTextUseCase.invoke(any()) } returns failedOcrResult
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractPriceDataUseCase.invoke(testRequest)

        // Assert
        assertFalse(result.success, "Should indicate failure when OCR fails")
        assertEquals(0, result.wordCount, "Should have no word count on failure")
        assertEquals("", result.extractedText, "Should have empty text on failure")

        coVerify { mockExtractTextUseCase.invoke(any()) }
    }

    @Test
    fun `invoke should handle OCR result with no prices found`() = runTest {
        // Arrange
        val testRequest = createPriceExtractionRequest()
        val ocrResultWithoutPrices = createOcrResultWithoutPrices()

        coEvery { mockExtractTextUseCase.invoke(any()) } returns ocrResultWithoutPrices
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractPriceDataUseCase.invoke(testRequest)

        // Assert
        assertTrue(result.success, "Should succeed even when no prices found")
        assertNotNull(result.structuredData, "Should have structured data")
        assertTrue(result.structuredData!!.prices.isEmpty(), "Should have empty prices list")
        assertEquals("This is some text without any prices or currency symbols", result.extractedText)

        coVerify { mockExtractTextUseCase.invoke(any()) }
        coVerify { mockLogUsageUseCase(match { it.action == UsageLogAction.OCR_PRICE_EXTRACTION }) }
    }

    @Test
    fun `invoke should configure OCR request for price extraction`() = runTest {
        // Arrange
        val testRequest = createPriceExtractionRequest()
        val expectedOcrResult = createOcrResultWithPrices()

        coEvery { mockExtractTextUseCase.invoke(any()) } returns expectedOcrResult
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        extractPriceDataUseCase.invoke(testRequest)

        // Assert
        coVerify {
            mockExtractTextUseCase.invoke(match { ocrRequest ->
                ocrRequest.useCase == OcrUseCase.PRICE_MONITORING &&
                ocrRequest.options.extractPrices == true &&
                ocrRequest.options.enableStructuredData == true &&
                ocrRequest.userId == testRequest.userId
            })
        }
    }

    @Test
    fun `invoke should handle different currencies`() = runTest {
        // Arrange
        val testRequest = createPriceExtractionRequest()
        val ocrResultWithMultipleCurrencies = createOcrResultWithMultipleCurrencies()

        coEvery { mockExtractTextUseCase.invoke(any()) } returns ocrResultWithMultipleCurrencies
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractPriceDataUseCase.invoke(testRequest)

        // Assert
        assertTrue(result.success, "Should handle multiple currencies")
        assertNotNull(result.structuredData, "Should have structured data")
        assertEquals(4, result.structuredData!!.prices.size, "Should extract all prices with different currencies")

        val currencies = result.structuredData!!.prices.map { it.currency }.toSet()
        assertTrue(currencies.contains("USD"), "Should handle USD currency")
        assertTrue(currencies.contains("EUR"), "Should handle EUR currency")
        assertTrue(currencies.contains("GBP"), "Should handle GBP currency")
        assertTrue(currencies.contains("JPY"), "Should handle JPY currency")
    }

    @Test
    fun `invoke should handle prices with different confidence levels`() = runTest {
        // Arrange
        val testRequest = createPriceExtractionRequest()
        val ocrResultWithVariousConfidences = createOcrResultWithVariousConfidences()

        coEvery { mockExtractTextUseCase.invoke(any()) } returns ocrResultWithVariousConfidences
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractPriceDataUseCase.invoke(testRequest)

        // Assert
        assertTrue(result.success, "Should handle prices with various confidence levels")
        assertNotNull(result.structuredData, "Should have structured data")
        assertEquals(3, result.structuredData!!.prices.size, "Should extract all prices from text regardless of lines")

        val highConfidencePrice = result.structuredData!!.prices.find { it.confidence >= 0.9 }
        val mediumConfidencePrice = result.structuredData!!.prices.find { it.confidence in 0.7..0.89 }
        val defaultConfidencePrice = result.structuredData!!.prices.find { it.confidence == 0.8 }

        assertNotNull(highConfidencePrice, "Should include high confidence price")
        assertNotNull(mediumConfidencePrice, "Should include medium confidence price")
        assertNotNull(defaultConfidencePrice, "Should include default confidence price for text-extracted prices")
    }

    @Test
    fun `invoke should log price extraction attempts`() = runTest {
        // Arrange
        val testRequest = createPriceExtractionRequest()
        val ocrResultWithPrices = createOcrResultWithPrices()

        coEvery { mockExtractTextUseCase.invoke(any()) } returns ocrResultWithPrices
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        extractPriceDataUseCase.invoke(testRequest)

        // Assert
        coVerify {
            mockLogUsageUseCase(match { logRequest ->
                logRequest.action == UsageLogAction.OCR_PRICE_EXTRACTION &&
                logRequest.userId == testRequest.userId &&
                logRequest.metadata?.get("prices_extracted") == "2"
            })
        }
    }

    @Test
    fun `invoke should handle edge case price formats`() = runTest {
        // Arrange
        val testRequest = createPriceExtractionRequest()
        val ocrResultWithEdgeCasePrices = createOcrResultWithEdgeCasePrices()

        coEvery { mockExtractTextUseCase.invoke(any()) } returns ocrResultWithEdgeCasePrices
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractPriceDataUseCase.invoke(testRequest)

        // Assert
        assertTrue(result.success, "Should handle edge case price formats")
        assertNotNull(result.structuredData, "Should have structured data")
        assertTrue(result.structuredData!!.prices.isNotEmpty(), "Should extract prices even with edge case formats")

        // Check for various price formats
        val priceValues = result.structuredData!!.prices.map { it.value }
        assertTrue(priceValues.any { it.contains(".") }, "Should handle decimal prices")
        assertTrue(priceValues.any { it.contains(",") }, "Should handle comma-formatted prices")
    }

    // ===== HELPER METHODS =====

    private fun createPriceExtractionRequest(): OcrRequest {
        return OcrRequest(
            id = UUID.randomUUID().toString(),
            userId = "user-test-123",
            imageBytes = "test image with prices".toByteArray(),
            language = "en",
            tier = OcrTier.AI_STANDARD,
            useCase = OcrUseCase.GENERAL,
            options = OcrOptions(
                extractPrices = false,
                extractTables = false,
                extractForms = false,
                confidenceThreshold = 0.8,
                enableStructuredData = false
            )
        )
    }

    private fun createOcrResultWithPrices(): OcrResult {
        return OcrResult(
            id = UUID.randomUUID().toString(),
            userId = "test-user-id",
            success = true,
            extractedText = "Product A: $19.99\nProduct B: €25.50",
            confidence = 0.92,
            wordCount = 6,
            lines = listOf(createTestOcrTextLine()),
            processingTime = 2.1,
            language = "en",
            engine = OcrEngine.PADDLE_OCR,
            structuredData = OcrStructuredData(
                prices = listOf(
                    OcrPrice(
                        value = "$19.99",
                        numericValue = 19.99,
                        currency = "USD",
                        confidence = 0.94,
                        boundingBox = createTestBoundingBox()
                    ),
                    OcrPrice(
                        value = "€25.50",
                        numericValue = 25.50,
                        currency = "EUR",
                        confidence = 0.91,
                        boundingBox = createTestBoundingBox()
                    )
                )
            ),
            createdAt = Clock.System.now()
        )
    }

    private fun createOcrResultWithMultipleCurrencies(): OcrResult {
        return createOcrResultWithPrices().copy(
            extractedText = "USD: $19.99, EUR: €25.50, GBP: £15.75, JPY: ¥2500",
            structuredData = OcrStructuredData(
                prices = listOf(
                    OcrPrice("$19.99", 19.99, "USD", 0.94, createTestBoundingBox()),
                    OcrPrice("€25.50", 25.50, "EUR", 0.91, createTestBoundingBox()),
                    OcrPrice("£15.75", 15.75, "GBP", 0.89, createTestBoundingBox()),
                    OcrPrice("¥2500", 2500.0, "JPY", 0.88, createTestBoundingBox())
                )
            )
        )
    }

    private fun createOcrResultWithVariousConfidences(): OcrResult {
        return createOcrResultWithPrices().copy(
            extractedText = "Product A: $19.99\nProduct B: €25.50\nProduct C: £12.30",
            lines = listOf(
                OcrTextLine("Product A: $19.99", 0.95, createTestBoundingBox(), 3),
                OcrTextLine("Product B: €25.50", 0.75, createTestBoundingBox(), 3)
                // Note: Only 2 lines provided, so ExtractPriceDataUseCase will extract 2 prices
            ),
            structuredData = null // Let ExtractPriceDataUseCase extract prices from text
        )
    }

    private fun createOcrResultWithEdgeCasePrices(): OcrResult {
        return createOcrResultWithPrices().copy(
            extractedText = "Prices: $1,299.99, €3.450,75, £0.99",
            structuredData = OcrStructuredData(
                prices = listOf(
                    OcrPrice("$1,299.99", 1299.99, "USD", 0.92, createTestBoundingBox()),
                    OcrPrice("€3.450,75", 3450.75, "EUR", 0.87, createTestBoundingBox()),
                    OcrPrice("£0.99", 0.99, "GBP", 0.93, createTestBoundingBox())
                )
            )
        )
    }

    private fun createOcrResultWithoutPrices(): OcrResult {
        return OcrResult(
            id = UUID.randomUUID().toString(),
            userId = "test-user-id",
            success = true,
            extractedText = "This is some text without any prices or currency symbols",
            confidence = 0.88,
            wordCount = 11,
            lines = listOf(createTestOcrTextLine()),
            processingTime = 1.8,
            language = "en",
            engine = OcrEngine.PADDLE_OCR,
            structuredData = OcrStructuredData(prices = emptyList()),
            createdAt = Clock.System.now()
        )
    }

    private fun createFailedOcrResult(): OcrResult {
        return OcrResult(
            id = UUID.randomUUID().toString(),
            userId = "test-user-id",
            success = false,
            extractedText = "",
            confidence = 0.0,
            wordCount = 0,
            lines = emptyList(),
            processingTime = 0.5,
            language = "en",
            engine = OcrEngine.PADDLE_OCR,
            createdAt = Clock.System.now(),
            error = "Image processing failed"
        )
    }

    private fun createTestOcrTextLine(): OcrTextLine {
        return OcrTextLine(
            text = "Sample text line",
            confidence = 0.9,
            boundingBox = createTestBoundingBox(),
            wordCount = 3
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

}
