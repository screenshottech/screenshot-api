package dev.screenshotapi.infrastructure.adapters.output.persistence.inmemory

import dev.screenshotapi.core.domain.entities.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import java.util.*

/**
 * In-Memory OCR Result Repository Tests
 * GitHub Issue #6: Comprehensive OCR testing and quality assurance
 */
class InMemoryOcrResultRepositoryTest {

    private lateinit var repository: InMemoryOcrResultRepository

    @BeforeEach
    fun setup() {
        repository = InMemoryOcrResultRepository()
    }

    // ===== SAVE TESTS =====

    @Test
    fun `save should store OCR result successfully`() = runTest {
        // Arrange
        val testOcrResult = createTestOcrResult()

        // Act
        val result = repository.save(testOcrResult)

        // Assert
        assertEquals(testOcrResult, result, "Should return the saved OCR result")
        assertNotNull(repository.findById(testOcrResult.id), "Should be able to find saved result by ID")
    }

    @Test
    fun `save should update existing OCR result`() = runTest {
        // Arrange
        val originalResult = createTestOcrResult()
        val updatedResult = originalResult.copy(
            extractedText = "Updated text content",
            confidence = 0.95
        )

        // Act
        repository.save(originalResult)
        val result = repository.save(updatedResult)

        // Assert
        assertEquals(updatedResult, result, "Should return updated OCR result")
        assertEquals("Updated text content", result.extractedText, "Should have updated text")
        assertEquals(0.95, result.confidence, "Should have updated confidence")
        
        val foundResult = repository.findById(originalResult.id)
        assertEquals(updatedResult.extractedText, foundResult?.extractedText, "Should persist updated text")
    }

    // ===== FIND BY ID TESTS =====

    @Test
    fun `findById should return OCR result when exists`() = runTest {
        // Arrange
        val testOcrResult = createTestOcrResult()
        repository.save(testOcrResult)

        // Act
        val result = repository.findById(testOcrResult.id)

        // Assert
        assertNotNull(result, "Should find existing OCR result")
        assertEquals(testOcrResult.id, result!!.id, "Should return correct result")
        assertEquals(testOcrResult.extractedText, result.extractedText, "Should return correct content")
    }

    @Test
    fun `findById should return null when result does not exist`() = runTest {
        // Arrange
        val nonExistentId = "non-existent-id"

        // Act
        val result = repository.findById(nonExistentId)

        // Assert
        assertNull(result, "Should return null for non-existent ID")
    }

    // ===== FIND BY USER ID TESTS =====

    @Test
    fun `findByUserId should return results for specific user`() = runTest {
        // Arrange
        val userId1 = "user-123"
        val userId2 = "user-456"
        val user1Results = listOf(
            createTestOcrResult(userId = userId1, screenshotJobId = "job-1"),
            createTestOcrResult(userId = userId1, screenshotJobId = "job-2")
        )
        val user2Result = createTestOcrResult(userId = userId2, screenshotJobId = "job-3")

        user1Results.forEach { repository.save(it) }
        repository.save(user2Result)

        // Act
        val results = repository.findByUserId(userId1)

        // Assert
        assertEquals(2, results.size, "Should return correct number of results for user")
        assertTrue(results.all { it.metadata["userId"] == userId1 }, "All results should belong to user")
        assertFalse(results.any { it.metadata["userId"] == userId2 }, "Should not include other users' results")
    }

    @Test
    fun `findByUserId should return empty list for user with no results`() = runTest {
        // Arrange
        val userWithResults = "user-with-results"
        val userWithoutResults = "user-without-results"
        repository.save(createTestOcrResult(userId = userWithResults))

        // Act
        val results = repository.findByUserId(userWithoutResults)

        // Assert
        assertTrue(results.isEmpty(), "Should return empty list for user with no results")
    }

    @Test
    fun `findByUserId should respect pagination parameters`() = runTest {
        // Arrange
        val userId = "user-123"
        val totalResults = 10
        repeat(totalResults) { index ->
            repository.save(createTestOcrResult(userId = userId, text = "Text $index"))
        }

        // Act
        val firstPage = repository.findByUserId(userId, offset = 0, limit = 3)
        val secondPage = repository.findByUserId(userId, offset = 3, limit = 3)
        val thirdPage = repository.findByUserId(userId, offset = 6, limit = 10)

        // Assert
        assertEquals(3, firstPage.size, "First page should have 3 results")
        assertEquals(3, secondPage.size, "Second page should have 3 results")
        assertEquals(4, thirdPage.size, "Third page should have remaining 4 results")
        
        // Verify no overlap between pages
        val allIds = (firstPage + secondPage + thirdPage).map { it.id }.toSet()
        assertEquals(totalResults, allIds.size, "Should have no duplicate results across pages")
    }

    @Test
    fun `findByUserId should sort results by creation time descending`() = runTest {
        // Arrange
        val userId = "user-123"
        val now = Clock.System.now()
        val results = listOf(
            createTestOcrResult(userId = userId, text = "Oldest", createdAt = now.minus(2.hours)),
            createTestOcrResult(userId = userId, text = "Middle", createdAt = now.minus(1.hours)),
            createTestOcrResult(userId = userId, text = "Newest", createdAt = now)
        )
        results.forEach { repository.save(it) }

        // Act
        val sortedResults = repository.findByUserId(userId)

        // Assert
        assertEquals("Newest", sortedResults[0].extractedText, "Newest result should be first")
        assertEquals("Middle", sortedResults[1].extractedText, "Middle result should be second")
        assertEquals("Oldest", sortedResults[2].extractedText, "Oldest result should be last")
    }

    // ===== FIND BY SCREENSHOT JOB ID TESTS =====

    @Test
    fun `findByScreenshotJobId should return results for specific job`() = runTest {
        // Arrange
        val jobId1 = "job-123"
        val jobId2 = "job-456"
        val job1Results = listOf(
            createTestOcrResult(screenshotJobId = jobId1, text = "Job 1 Result 1"),
            createTestOcrResult(screenshotJobId = jobId1, text = "Job 1 Result 2")
        )
        val job2Result = createTestOcrResult(screenshotJobId = jobId2, text = "Job 2 Result")

        job1Results.forEach { repository.save(it) }
        repository.save(job2Result)

        // Act
        val results = repository.findByScreenshotJobId(jobId1)

        // Assert
        assertEquals(2, results.size, "Should return correct number of results for job")
        assertTrue(results.all { it.metadata["screenshotJobId"] == jobId1 }, "All results should belong to job")
        assertTrue(results.any { it.extractedText == "Job 1 Result 1" }, "Should include first job result")
        assertTrue(results.any { it.extractedText == "Job 1 Result 2" }, "Should include second job result")
    }

    @Test
    fun `findByScreenshotJobId should return empty list for non-existent job`() = runTest {
        // Arrange
        val existingJobId = "existing-job"
        val nonExistentJobId = "non-existent-job"
        repository.save(createTestOcrResult(screenshotJobId = existingJobId))

        // Act
        val results = repository.findByScreenshotJobId(nonExistentJobId)

        // Assert
        assertTrue(results.isEmpty(), "Should return empty list for non-existent job")
    }

    // ===== USER ANALYTICS TESTS =====

    @Test
    fun `getUserOcrAnalytics should return correct analytics for user`() = runTest {
        // Arrange
        val userId = "user-123"
        val now = Clock.System.now()
        val fromDate = now.minus(7.days)
        val toDate = now
        
        val userResults = listOf(
            createTestOcrResult(userId = userId, success = true, confidence = 0.95, wordCount = 10, engine = OcrEngine.PADDLE_OCR, createdAt = now),
            createTestOcrResult(userId = userId, success = true, confidence = 0.88, wordCount = 15, engine = OcrEngine.TESSERACT, createdAt = now),
            createTestOcrResult(userId = userId, success = false, confidence = 0.0, wordCount = 0, engine = OcrEngine.PADDLE_OCR, createdAt = now),
            createTestOcrResult(userId = userId, success = true, confidence = 0.92, wordCount = 8, engine = OcrEngine.PADDLE_OCR, createdAt = now)
        )
        userResults.forEach { repository.save(it) }

        // Add result for different user to ensure filtering
        repository.save(createTestOcrResult(userId = "other-user", success = true))

        // Act
        val analytics = repository.getUserOcrAnalytics(userId, fromDate, toDate)

        // Assert
        assertEquals(4, analytics.totalRequests, "Should count all requests for user")
        assertEquals(3, analytics.successfulRequests, "Should count successful requests")
        assertEquals(1, analytics.failedRequests, "Should count failed requests")
        assertEquals(33, analytics.totalWordsExtracted, "Should sum word counts (10+15+0+8)")
        
        // Calculate expected average confidence: (0.95 + 0.88 + 0.0 + 0.92) / 4 = 0.6875
        assertEquals(0.6875, analytics.averageConfidence, 0.001, "Should calculate correct average confidence")
        
        // Engine usage
        assertEquals(3, analytics.engineUsage[OcrEngine.PADDLE_OCR], "Should count PaddleOCR usage")
        assertEquals(1, analytics.engineUsage[OcrEngine.TESSERACT], "Should count Tesseract usage")
    }

    @Test
    fun `getUserOcrAnalytics should filter by date range`() = runTest {
        // Arrange
        val userId = "user-123"
        val now = Clock.System.now()
        val fromDate = now.minus(2.days)
        val toDate = now
        
        val withinRangeResult = createTestOcrResult(userId = userId, createdAt = now.minus(1.days))
        val outsideRangeResult = createTestOcrResult(userId = userId, createdAt = now.minus(5.days))
        
        repository.save(withinRangeResult)
        repository.save(outsideRangeResult)

        // Act
        val analytics = repository.getUserOcrAnalytics(userId, fromDate, toDate)

        // Assert
        assertEquals(1, analytics.totalRequests, "Should only count results within date range")
    }

    // ===== SYSTEM ANALYTICS TESTS =====

    @Test
    fun `getSystemOcrAnalytics should return analytics for all users`() = runTest {
        // Arrange
        val now = Clock.System.now()
        val fromDate = now.minus(7.days)
        val toDate = now
        
        val allResults = listOf(
            createTestOcrResult(userId = "user-1", success = true, wordCount = 10, createdAt = now),
            createTestOcrResult(userId = "user-2", success = true, wordCount = 20, createdAt = now),
            createTestOcrResult(userId = "user-3", success = false, wordCount = 0, createdAt = now)
        )
        allResults.forEach { repository.save(it) }

        // Act
        val analytics = repository.getSystemOcrAnalytics(fromDate, toDate)

        // Assert
        assertEquals(3, analytics.totalRequests, "Should count all system requests")
        assertEquals(2, analytics.successfulRequests, "Should count all successful requests")
        assertEquals(1, analytics.failedRequests, "Should count all failed requests")
        assertEquals(30, analytics.totalWordsExtracted, "Should sum all word counts")
    }

    // ===== DELETE OLDER THAN TESTS =====

    @Test
    fun `deleteOlderThan should remove old results`() = runTest {
        // Arrange
        val now = Clock.System.now()
        val cutoffDate = now.minus(7.days)
        
        val oldResult = createTestOcrResult(createdAt = now.minus(10.days))
        val recentResult = createTestOcrResult(createdAt = now.minus(3.days))
        
        repository.save(oldResult)
        repository.save(recentResult)

        // Act
        val deletedCount = repository.deleteOlderThan(cutoffDate)

        // Assert
        assertEquals(1, deletedCount, "Should delete 1 old result")
        assertNull(repository.findById(oldResult.id), "Old result should be deleted")
        assertNotNull(repository.findById(recentResult.id), "Recent result should remain")
    }

    // ===== COUNT BY USER AND DATE RANGE TESTS =====

    @Test
    fun `countByUserAndDateRange should return correct count`() = runTest {
        // Arrange
        val userId = "user-123"
        val now = Clock.System.now()
        val fromDate = now.minus(7.days)
        val toDate = now
        
        // Within range
        repository.save(createTestOcrResult(userId = userId, createdAt = now.minus(3.days)))
        repository.save(createTestOcrResult(userId = userId, createdAt = now.minus(1.days)))
        
        // Outside range
        repository.save(createTestOcrResult(userId = userId, createdAt = now.minus(10.days)))
        
        // Different user
        repository.save(createTestOcrResult(userId = "other-user", createdAt = now.minus(2.days)))

        // Act
        val count = repository.countByUserAndDateRange(userId, fromDate, toDate)

        // Assert
        assertEquals(2, count, "Should count only user's results within date range")
    }

    // ===== HELPER METHODS =====

    private fun createTestOcrResult(
        id: String = UUID.randomUUID().toString(),
        userId: String = "user-123",
        screenshotJobId: String = "job-456",
        text: String = "Test extracted text",
        success: Boolean = true,
        confidence: Double = 0.9,
        wordCount: Int = 3,
        engine: OcrEngine = OcrEngine.PADDLE_OCR,
        createdAt: Instant = Clock.System.now()
    ): OcrResult {
        return OcrResult(
            id = id,
            success = success,
            extractedText = text,
            confidence = confidence,
            wordCount = wordCount,
            lines = listOf(createTestOcrTextLine()),
            processingTime = 1.5,
            language = "en",
            engine = engine,
            metadata = mapOf(
                "userId" to userId,
                "screenshotJobId" to screenshotJobId
            ),
            createdAt = createdAt
        )
    }

    private fun createTestOcrTextLine(): OcrTextLine {
        return OcrTextLine(
            text = "Sample text line",
            confidence = 0.9,
            boundingBox = OcrBoundingBox(
                x1 = 10,
                y1 = 20,
                x2 = 200,
                y2 = 40,
                width = 190,
                height = 20
            ),
            wordCount = 3
        )
    }
}