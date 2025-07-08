package dev.screenshotapi.core.domain.entities

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * OCR Text Line and Bounding Box Entity Tests
 * GitHub Issue #6: Comprehensive OCR testing and quality assurance
 */
class OcrTextLineTest {

    @Test
    fun `should create OCR text line with all fields`() {
        // Arrange
        val testText = "Sample text line for testing"
        val testConfidence = 0.92
        val testBoundingBox = createTestBoundingBox()
        val testWordCount = 5

        // Act
        val textLine = OcrTextLine(
            text = testText,
            confidence = testConfidence,
            boundingBox = testBoundingBox,
            wordCount = testWordCount
        )

        // Assert
        assertEquals(testText, textLine.text, "Should have correct text")
        assertEquals(testConfidence, textLine.confidence, "Should have correct confidence")
        assertEquals(testBoundingBox, textLine.boundingBox, "Should have correct bounding box")
        assertEquals(testWordCount, textLine.wordCount, "Should have correct word count")
    }

    @Test
    fun `should handle single word text line`() {
        // Arrange
        val singleWord = "Hello"
        val boundingBox = createTestBoundingBox(50, 20, 100, 40)

        // Act
        val textLine = OcrTextLine(
            text = singleWord,
            confidence = 0.95,
            boundingBox = boundingBox,
            wordCount = 1
        )

        // Assert
        assertEquals(singleWord, textLine.text, "Should handle single word")
        assertEquals(1, textLine.wordCount, "Should have word count of 1")
        assertEquals(0.95, textLine.confidence, "Should have high confidence for single word")
    }

    @Test
    fun `should handle multi-word text line`() {
        // Arrange
        val multiWordText = "This is a longer text line with multiple words"
        val wordCount = multiWordText.split(" ").size
        val boundingBox = createTestBoundingBox(10, 100, 500, 120)

        // Act
        val textLine = OcrTextLine(
            text = multiWordText,
            confidence = 0.87,
            boundingBox = boundingBox,
            wordCount = wordCount
        )

        // Assert
        assertEquals(multiWordText, textLine.text, "Should handle multi-word text")
        assertEquals(9, textLine.wordCount, "Should have correct word count for multi-word text")
        assertEquals(0.87, textLine.confidence, "Should have appropriate confidence")
    }

    @Test
    fun `should handle empty text line`() {
        // Arrange
        val emptyText = ""
        val boundingBox = createTestBoundingBox()

        // Act
        val textLine = OcrTextLine(
            text = emptyText,
            confidence = 0.0,
            boundingBox = boundingBox,
            wordCount = 0
        )

        // Assert
        assertEquals("", textLine.text, "Should handle empty text")
        assertEquals(0, textLine.wordCount, "Should have zero word count for empty text")
        assertEquals(0.0, textLine.confidence, "Should have zero confidence for empty text")
    }

    @Test
    fun `should handle special characters and punctuation`() {
        // Arrange
        val specialText = "Price: $19.99 (discount: 20%)"
        val boundingBox = createTestBoundingBox()

        // Act
        val textLine = OcrTextLine(
            text = specialText,
            confidence = 0.88,
            boundingBox = boundingBox,
            wordCount = 4
        )

        // Assert
        assertEquals(specialText, textLine.text, "Should handle special characters")
        assertTrue(textLine.text.contains("$"), "Should preserve currency symbols")
        assertTrue(textLine.text.contains("%"), "Should preserve percentage symbols")
        assertTrue(textLine.text.contains("("), "Should preserve parentheses")
        assertEquals(4, textLine.wordCount, "Should count words correctly with special characters")
    }

    @Test
    fun `should handle low confidence text line`() {
        // Arrange
        val lowConfidenceText = "blurry or unclear text"
        val lowConfidence = 0.35
        val boundingBox = createTestBoundingBox()

        // Act
        val textLine = OcrTextLine(
            text = lowConfidenceText,
            confidence = lowConfidence,
            boundingBox = boundingBox,
            wordCount = 4
        )

        // Assert
        assertEquals(lowConfidenceText, textLine.text, "Should handle low confidence text")
        assertTrue(textLine.confidence < 0.5, "Should have low confidence value")
        assertEquals(4, textLine.wordCount, "Should still count words for low confidence text")
    }

    @Test
    fun `should handle different languages`() {
        // Arrange
        val testCases = listOf(
            "Hello World" to "en",
            "Hola Mundo" to "es", 
            "Bonjour le monde" to "fr",
            "Hallo Welt" to "de",
            "こんにちは世界" to "ja",
            "مرحبا بالعالم" to "ar"
        )

        testCases.forEach { (text, language) ->
            // Act
            val textLine = OcrTextLine(
                text = text,
                confidence = 0.9,
                boundingBox = createTestBoundingBox(),
                wordCount = text.split(" ").size
            )

            // Assert
            assertEquals(text, textLine.text, "Should handle $language text correctly")
            assertTrue(textLine.confidence > 0.8, "Should have good confidence for $language")
        }
    }

    // ===== BOUNDING BOX TESTS =====

    @Test
    fun `should create OCR bounding box with all coordinates`() {
        // Arrange
        val testX1 = 10
        val testY1 = 20
        val testX2 = 200
        val testY2 = 40
        val testWidth = 190
        val testHeight = 20

        // Act
        val boundingBox = OcrBoundingBox(
            x1 = testX1,
            y1 = testY1,
            x2 = testX2,
            y2 = testY2,
            width = testWidth,
            height = testHeight
        )

        // Assert
        assertEquals(testX1, boundingBox.x1, "Should have correct x1 coordinate")
        assertEquals(testY1, boundingBox.y1, "Should have correct y1 coordinate")
        assertEquals(testX2, boundingBox.x2, "Should have correct x2 coordinate")
        assertEquals(testY2, boundingBox.y2, "Should have correct y2 coordinate")
        assertEquals(testWidth, boundingBox.width, "Should have correct width")
        assertEquals(testHeight, boundingBox.height, "Should have correct height")
    }

    @Test
    fun `should create bounding boxes for different text sizes`() {
        // Arrange
        val testCases = listOf(
            // Small text
            Triple(10, 10, 50),   // x1, y1, expected_width
            // Medium text  
            Triple(100, 50, 150),
            // Large text
            Triple(200, 100, 300)
        )

        testCases.forEach { (x1, y1, expectedWidth) ->
            // Act
            val boundingBox = OcrBoundingBox(
                x1 = x1,
                y1 = y1,
                x2 = x1 + expectedWidth,
                y2 = y1 + 20,
                width = expectedWidth,
                height = 20
            )

            // Assert
            assertEquals(expectedWidth, boundingBox.width, "Should have correct width for text size")
            assertEquals(20, boundingBox.height, "Should have consistent height")
            assertTrue(boundingBox.x2 > boundingBox.x1, "X2 should be greater than X1")
            assertTrue(boundingBox.y2 > boundingBox.y1, "Y2 should be greater than Y1")
        }
    }

    @Test
    fun `should handle zero-sized bounding box`() {
        // Arrange & Act
        val boundingBox = OcrBoundingBox(
            x1 = 100,
            y1 = 200,
            x2 = 100,
            y2 = 200,
            width = 0,
            height = 0
        )

        // Assert
        assertEquals(0, boundingBox.width, "Should handle zero width")
        assertEquals(0, boundingBox.height, "Should handle zero height")
        assertEquals(boundingBox.x1, boundingBox.x2, "X coordinates should be equal for zero width")
        assertEquals(boundingBox.y1, boundingBox.y2, "Y coordinates should be equal for zero height")
    }

    @Test
    fun `should handle large document bounding boxes`() {
        // Arrange
        val largeX2 = 2000
        val largeY2 = 3000

        // Act
        val boundingBox = OcrBoundingBox(
            x1 = 0,
            y1 = 0,
            x2 = largeX2,
            y2 = largeY2,
            width = largeX2,
            height = largeY2
        )

        // Assert
        assertEquals(largeX2, boundingBox.width, "Should handle large document width")
        assertEquals(largeY2, boundingBox.height, "Should handle large document height")
        assertTrue(boundingBox.width > 1000, "Should support large document dimensions")
        assertTrue(boundingBox.height > 1000, "Should support large document dimensions")
    }

    // ===== HELPER METHODS =====

    private fun createTestBoundingBox(
        x1: Int = 10,
        y1: Int = 20,
        x2: Int = 200,
        y2: Int = 40
    ): OcrBoundingBox {
        return OcrBoundingBox(
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
            width = x2 - x1,
            height = y2 - y1
        )
    }
}