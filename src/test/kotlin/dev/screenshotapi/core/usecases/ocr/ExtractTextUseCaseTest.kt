package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.exceptions.OcrException
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.services.OcrService
import dev.screenshotapi.core.usecases.billing.DeductCreditsRequest
import dev.screenshotapi.core.usecases.billing.DeductCreditsResponse
import dev.screenshotapi.core.usecases.billing.DeductCreditsUseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase.Request as LogUsageRequest

/**
 * Extract Text Use Case Tests
 * GitHub Issue #6: Comprehensive OCR testing and quality assurance
 */
class ExtractTextUseCaseTest {

    private lateinit var extractTextUseCase: ExtractTextUseCase
    private lateinit var mockOcrService: OcrService
    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockDeductCreditsUseCase: DeductCreditsUseCase
    private lateinit var mockLogUsageUseCase: LogUsageUseCase

    @BeforeEach
    fun setup() {
        mockOcrService = mockk()
        mockUserRepository = mockk()
        mockDeductCreditsUseCase = mockk()
        mockLogUsageUseCase = mockk(relaxed = true)

        extractTextUseCase = ExtractTextUseCase(
            ocrService = mockOcrService,
            userRepository = mockUserRepository,
            deductCreditsUseCase = mockDeductCreditsUseCase,
            logUsageUseCase = mockLogUsageUseCase
        )
    }

    @Test
    fun `invoke should extract text successfully for basic OCR request`() = runTest {
        // Arrange
        val testRequest = createBasicOcrRequest()
        val testUser = createTestUser()
        val expectedOcrResult = createSuccessfulOcrResult(testRequest.id)

        coEvery { mockUserRepository.findById(testRequest.userId) } returns testUser
        coEvery { mockOcrService.extractText(testRequest) } returns expectedOcrResult
        coEvery { mockDeductCreditsUseCase(any()) } returns DeductCreditsResponse("user-test-123", 2, 98, Clock.System.now())
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractTextUseCase.invoke(testRequest)

        // Assert
        assertTrue(result.success, "Should indicate successful text extraction")
        assertEquals(expectedOcrResult.id, result.id, "Should return correct OCR result ID")
        assertEquals(expectedOcrResult.extractedText, result.extractedText, "Should return extracted text")
        assertEquals(expectedOcrResult.confidence, result.confidence, "Should return confidence score")

        coVerify { mockUserRepository.findById(testRequest.userId) }
        coVerify { mockOcrService.extractText(testRequest) }
        coVerify { mockDeductCreditsUseCase(any()) }
        coVerify(exactly = 2) { mockLogUsageUseCase(any()) } // OCR_CREATED and OCR_COMPLETED
    }

    @Test
    fun `invoke should throw exception when user not found`() = runTest {
        // Arrange
        val testRequest = createBasicOcrRequest()

        coEvery { mockUserRepository.findById(testRequest.userId) } returns null

        // Act & Assert
        val exception = assertThrows<OcrException.ConfigurationException> {
            extractTextUseCase.invoke(testRequest)
        }

        assertTrue(exception.message!!.contains("User not found"), "Should indicate user not found")

        coVerify { mockUserRepository.findById(testRequest.userId) }
        coVerify(exactly = 0) { mockOcrService.extractText(any()) }
        coVerify(exactly = 0) { mockDeductCreditsUseCase(any()) }
    }

    @Test
    fun `invoke should handle OCR service failure gracefully`() = runTest {
        // Arrange
        val testRequest = createBasicOcrRequest()
        val testUser = createTestUser()
        val serviceException = RuntimeException("OCR service temporarily unavailable")

        coEvery { mockUserRepository.findById(testRequest.userId) } returns testUser
        coEvery { mockOcrService.extractText(testRequest) } throws serviceException
        coEvery { mockDeductCreditsUseCase(any()) } returns DeductCreditsResponse("user-test-123", 2, 98, Clock.System.now())
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act & Assert
        val exception = assertThrows<OcrException.ProcessingException> {
            extractTextUseCase.invoke(testRequest)
        }

        assertTrue(exception.message!!.contains("Unexpected error during OCR processing"), "Should wrap service error")

        coVerify { mockUserRepository.findById(testRequest.userId) }
        coVerify { mockOcrService.extractText(testRequest) }
        coVerify { mockDeductCreditsUseCase(any()) }
        coVerify { mockLogUsageUseCase(match { it.action == UsageLogAction.OCR_CREATED }) }
        coVerify { mockLogUsageUseCase(match { it.action == UsageLogAction.OCR_FAILED }) }
    }

    @Test
    fun `invoke should deduct correct credits for different tiers`() = runTest {
        // Arrange
        val tierTestCases = listOf(
            OcrTier.BASIC to 2,
            OcrTier.LOCAL_AI to 2,
            OcrTier.AI_STANDARD to 3,
            OcrTier.AI_PREMIUM to 5,
            OcrTier.AI_ELITE to 5
        )

        tierTestCases.forEach { (tier, expectedCredits) ->
            // Arrange
            val ocrRequest = createBasicOcrRequest().copy(tier = tier)
            val testUser = createTestUser()
            val expectedOcrResult = createSuccessfulOcrResult(ocrRequest.id)

            val creditSlot = slot<DeductCreditsRequest>()

            coEvery { mockUserRepository.findById(ocrRequest.userId) } returns testUser
            coEvery { mockOcrService.extractText(ocrRequest) } returns expectedOcrResult
            coEvery { mockDeductCreditsUseCase(capture(creditSlot)) } returns DeductCreditsResponse("user-test-123", 2, 98, Clock.System.now())
            coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

            // Act
            extractTextUseCase.invoke(ocrRequest)

            // Assert
            assertEquals(expectedCredits, creditSlot.captured.amount,
                "Should deduct $expectedCredits credits for $tier tier")
            assertEquals(CreditDeductionReason.OCR, creditSlot.captured.reason,
                "Should use OCR as deduction reason")
            assertEquals(ocrRequest.id, creditSlot.captured.jobId,
                "Should include job ID in credit deduction")
        }
    }

    @Test
    fun `invoke should handle insufficient credits`() = runTest {
        // Arrange
        val testRequest = createBasicOcrRequest()
        val testUser = createTestUser(creditsRemaining = 1) // Less than required 2 credits
        val insufficientCreditsException = RuntimeException("Insufficient credits")

        coEvery { mockUserRepository.findById(testRequest.userId) } returns testUser
        coEvery { mockDeductCreditsUseCase(any()) } throws insufficientCreditsException

        // Act & Assert
        val exception = assertThrows<OcrException.InsufficientCreditsException> {
            extractTextUseCase.invoke(testRequest)
        }

        assertTrue(exception.message!!.contains("Insufficient credits for BASIC OCR"), "Should include tier in message")
        assertTrue(exception.message!!.contains("requires 2"), "Should show required credits in message")
        assertTrue(exception.message!!.contains("available 1"), "Should show available credits in message")

        coVerify { mockUserRepository.findById(testRequest.userId) }
        coVerify { mockDeductCreditsUseCase(any()) }
        coVerify(exactly = 0) { mockOcrService.extractText(any()) }
    }

    @Test
    fun `invoke should log all OCR lifecycle events`() = runTest {
        // Arrange
        val testRequest = createBasicOcrRequest()
        val testUser = createTestUser()
        val expectedOcrResult = createSuccessfulOcrResult(testRequest.id)

        val logSlots = mutableListOf<LogUsageRequest>()

        coEvery { mockUserRepository.findById(testRequest.userId) } returns testUser
        coEvery { mockOcrService.extractText(testRequest) } returns expectedOcrResult
        coEvery { mockDeductCreditsUseCase(any()) } returns DeductCreditsResponse("user-test-123", 2, 98, Clock.System.now())
        coEvery { mockLogUsageUseCase(capture(logSlots)) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        extractTextUseCase.invoke(testRequest)

        // Assert
        assertEquals(2, logSlots.size, "Should log 2 events")

        val createLog = logSlots.find { it.action == UsageLogAction.OCR_CREATED }
        assertNotNull(createLog, "Should log OCR creation")
        assertEquals(testRequest.userId, createLog!!.userId, "Should log correct user ID")
        assertEquals(2, createLog.creditsUsed, "Creation should log required credits")

        val completeLog = logSlots.find { it.action == UsageLogAction.OCR_COMPLETED }
        assertNotNull(completeLog, "Should log OCR completion")
        assertEquals(0, completeLog!!.creditsUsed, "Completion log should not duplicate credit count")
    }

    @Test
    fun `invoke should handle premium tier OCR requests`() = runTest {
        // Arrange
        val premiumRequest = createPremiumOcrRequest()
        val testUser = createTestUser(creditsRemaining = 100)
        val premiumResult = createSuccessfulOcrResult(premiumRequest.id, confidence = 0.95, engine = OcrEngine.GPT4_VISION)

        coEvery { mockUserRepository.findById(premiumRequest.userId) } returns testUser
        coEvery { mockOcrService.extractText(premiumRequest) } returns premiumResult
        coEvery { mockDeductCreditsUseCase(any()) } returns DeductCreditsResponse("user-test-123", 2, 98, Clock.System.now())
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractTextUseCase.invoke(premiumRequest)

        // Assert
        assertTrue(result.success, "Should handle premium tier requests")
        assertTrue(result.confidence >= 0.9, "Premium tier should have high confidence")
        assertEquals(OcrEngine.GPT4_VISION, result.engine, "Should use appropriate engine")

        coVerify {
            mockDeductCreditsUseCase(match { it.amount == 5 }) // Premium tier costs 5 credits
        }
    }

    @Test
    fun `invoke should handle structured data extraction requests`() = runTest {
        // Arrange
        val structuredRequest = createStructuredDataOcrRequest()
        val testUser = createTestUser()
        val structuredResult = createOcrResultWithStructuredData(structuredRequest.id)

        coEvery { mockUserRepository.findById(structuredRequest.userId) } returns testUser
        coEvery { mockOcrService.extractText(structuredRequest) } returns structuredResult
        coEvery { mockDeductCreditsUseCase(any()) } returns DeductCreditsResponse("user-test-123", 2, 98, Clock.System.now())
        coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

        // Act
        val result = extractTextUseCase.invoke(structuredRequest)

        // Assert
        assertTrue(result.success, "Should handle structured data extraction")
        assertNotNull(result.structuredData, "Should include structured data")
        assertTrue(result.structuredData!!.prices.isNotEmpty(), "Should extract prices")
        assertTrue(structuredRequest.options.extractPrices, "Should have price extraction enabled")

        coVerify { mockOcrService.extractText(structuredRequest) }
    }

    @Test
    fun `invoke should handle different OCR engines`() = runTest {
        // Arrange
        val engineTestCases = listOf(
            OcrEngine.PADDLE_OCR to "PaddleOCR should be supported",
            OcrEngine.TESSERACT to "Tesseract should be supported",
            OcrEngine.GPT4_VISION to "GPT-4 Vision should be supported"
        )

        for ((engine, expectedMessage) in engineTestCases) {
            // Arrange
            val engineRequest = createOcrRequestWithEngine(engine)
            val testUser = createTestUser()
            val engineResult = createSuccessfulOcrResult(engineRequest.id, engine = engine)

            coEvery { mockUserRepository.findById(engineRequest.userId) } returns testUser
            coEvery { mockOcrService.extractText(engineRequest) } returns engineResult
            coEvery { mockDeductCreditsUseCase(any()) } returns DeductCreditsResponse("user-test-123", 2, 98, Clock.System.now())
            coEvery { mockLogUsageUseCase(any()) } returns LogUsageUseCase.Response("log-123", true)

            // Act
            val result = extractTextUseCase.invoke(engineRequest)

            // Assert
            assertTrue(result.success, expectedMessage)
            assertEquals(engine, result.engine, "Should use correct OCR engine")

            coVerify { mockOcrService.extractText(engineRequest) }
        }
    }

    // ===== HELPER METHODS =====

    private fun createTestUser(
        id: String = "user-test-123",
        creditsRemaining: Int = 100
    ): User {
        return User(
            id = id,
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hash",
            planId = "plan-basic",
            creditsRemaining = creditsRemaining,
            status = UserStatus.ACTIVE,
            roles = setOf(UserRole.USER),
            authProvider = "local",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
    }

    private fun createBasicOcrRequest(): OcrRequest {
        return OcrRequest(
            id = UUID.randomUUID().toString(),
            userId = "user-test-123",
            imageBytes = "test image data".toByteArray(),
            language = "en",
            tier = OcrTier.BASIC,
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

    private fun createPremiumOcrRequest(): OcrRequest {
        return createBasicOcrRequest().copy(
            tier = OcrTier.AI_PREMIUM,
            options = OcrOptions(
                extractPrices = true,
                extractTables = true,
                extractForms = true,
                confidenceThreshold = 0.9,
                enableStructuredData = true
            )
        )
    }

    private fun createStructuredDataOcrRequest(): OcrRequest {
        return createBasicOcrRequest().copy(
            useCase = OcrUseCase.PRICE_MONITORING,
            options = OcrOptions(
                extractPrices = true,
                confidenceThreshold = 0.85,
                enableStructuredData = true
            )
        )
    }

    private fun createOcrRequestWithEngine(engine: OcrEngine): OcrRequest {
        return createBasicOcrRequest().copy(engine = engine)
    }

    private fun createSuccessfulOcrResult(
        requestId: String,
        confidence: Double = 0.9,
        engine: OcrEngine = OcrEngine.PADDLE_OCR
    ): OcrResult {
        return OcrResult(
            id = requestId,
            success = true,
            extractedText = "Extracted text content for testing",
            confidence = confidence,
            wordCount = 5,
            lines = listOf(createTestOcrTextLine()),
            processingTime = 1.5,
            language = "en",
            engine = engine,
            createdAt = Clock.System.now(),
            metadata = mapOf(
                "userId" to "user-test-123",
                "tier" to "BASIC"
            )
        )
    }

    private fun createOcrResultWithStructuredData(requestId: String): OcrResult {
        return createSuccessfulOcrResult(requestId).copy(
            structuredData = OcrStructuredData(
                prices = listOf(
                    OcrPrice(
                        value = "$19.99",
                        numericValue = 19.99,
                        currency = "USD",
                        confidence = 0.92,
                        boundingBox = createTestBoundingBox()
                    )
                )
            )
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
