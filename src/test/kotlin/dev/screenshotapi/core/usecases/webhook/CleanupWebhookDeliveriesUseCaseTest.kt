package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.WebhookDeliveryStatus
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.days

class CleanupWebhookDeliveriesUseCaseTest {

    private lateinit var cleanupWebhookDeliveriesUseCase: CleanupWebhookDeliveriesUseCase
    private lateinit var webhookDeliveryRepository: WebhookDeliveryRepository

    @BeforeEach
    fun setup() {
        webhookDeliveryRepository = mockk()
        cleanupWebhookDeliveriesUseCase = CleanupWebhookDeliveriesUseCase(webhookDeliveryRepository)
    }

    // ===== BASIC CLEANUP TESTS =====

    @Test
    fun `cleanupOldDeliveries should delete deliveries older than retention period`() = runTest {
        // Arrange
        val retentionDays = 30
        val batchSize = 1000
        val expectedCutoffDate = Clock.System.now().minus(retentionDays.days)
        
        // First batch returns 1000 deletions (full batch), second batch returns 500 (partial batch, stops)
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, batchSize) 
        } returnsMany listOf(1000, 500)

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupOldDeliveries(retentionDays, batchSize)

        // Assert
        assertEquals(1500, result, "Should return total number of deleted deliveries")
        coVerify(exactly = 2) {
            webhookDeliveryRepository.deleteOldDeliveries(
                before = match { it <= expectedCutoffDate.plus(1.days) }, // Allow for small time differences
                status = null,
                limit = batchSize
            )
        }
    }

    @Test
    fun `cleanupOldDeliveries should handle multiple batches correctly`() = runTest {
        // Arrange
        val retentionDays = 7
        val batchSize = 100
        
        // Simulate multiple batches: 100, 100, 50 (stops at 50 because 50 < 100)
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, batchSize) 
        } returnsMany listOf(100, 100, 50)

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupOldDeliveries(retentionDays, batchSize)

        // Assert
        assertEquals(250, result, "Should sum all batches: 100 + 100 + 50")
        coVerify(exactly = 3) {
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, batchSize)
        }
    }

    @Test
    fun `cleanupOldDeliveries should handle zero deletions gracefully`() = runTest {
        // Arrange
        val retentionDays = 30
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, any()) 
        } returns 0

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupOldDeliveries(retentionDays)

        // Assert
        assertEquals(0, result, "Should return 0 when no deliveries to delete")
        coVerify(exactly = 1) {
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, any())
        }
    }

    // ===== FAILED DELIVERIES CLEANUP TESTS =====

    @Test
    fun `cleanupFailedDeliveries should only delete failed deliveries`() = runTest {
        // Arrange
        val retentionDays = 7
        val batchSize = 500
        val expectedDeletedCount = 150
        
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), WebhookDeliveryStatus.FAILED, batchSize) 
        } returns expectedDeletedCount

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupFailedDeliveries(retentionDays, batchSize)

        // Assert
        assertEquals(expectedDeletedCount, result, "Should return number of failed deliveries deleted")
        coVerify(exactly = 1) {
            webhookDeliveryRepository.deleteOldDeliveries(
                before = any(),
                status = WebhookDeliveryStatus.FAILED,
                limit = batchSize
            )
        }
    }

    @Test
    fun `cleanupFailedDeliveries should use correct default retention period`() = runTest {
        // Arrange
        val expectedDefaultRetentionDays = 7
        val expectedCutoffDate = Clock.System.now().minus(expectedDefaultRetentionDays.days)
        
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), WebhookDeliveryStatus.FAILED, any()) 
        } returns 42

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupFailedDeliveries() // Using defaults

        // Assert
        assertEquals(42, result)
        coVerify {
            webhookDeliveryRepository.deleteOldDeliveries(
                before = match { cutoff ->
                    val timeDiff = kotlin.math.abs(cutoff.epochSeconds - expectedCutoffDate.epochSeconds)
                    timeDiff <= 60 // Allow up to 60 seconds difference for test execution time
                },
                status = WebhookDeliveryStatus.FAILED,
                limit = 1000 // Default batch size
            )
        }
    }

    // ===== PARAMETERIZED TESTS FOR DIFFERENT RETENTION PERIODS =====

    @ParameterizedTest
    @ValueSource(ints = [1, 7, 30, 90])
    fun `cleanupOldDeliveries should work with different retention periods`(retentionDays: Int) = runTest {
        // Arrange
        val expectedDeletedCount = retentionDays * 10 // Simulate more deletions for longer periods
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, any()) 
        } returns expectedDeletedCount

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupOldDeliveries(retentionDays)

        // Assert
        assertEquals(expectedDeletedCount, result)
        coVerify {
            webhookDeliveryRepository.deleteOldDeliveries(
                before = match { cutoff ->
                    val expectedCutoff = Clock.System.now().minus(retentionDays.days)
                    val timeDiff = kotlin.math.abs(cutoff.epochSeconds - expectedCutoff.epochSeconds)
                    timeDiff <= 60 // Allow for test execution time
                },
                status = null,
                limit = 1000
            )
        }
    }

    // ===== BATCH SIZE TESTS =====

    @ParameterizedTest
    @ValueSource(ints = [100, 500, 1000, 2000])
    fun `cleanupOldDeliveries should respect different batch sizes`(batchSize: Int) = runTest {
        // Arrange
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, batchSize) 
        } returns 0

        // Act
        cleanupWebhookDeliveriesUseCase.cleanupOldDeliveries(30, batchSize)

        // Assert
        coVerify {
            webhookDeliveryRepository.deleteOldDeliveries(
                before = any(),
                status = null,
                limit = batchSize
            )
        }
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    fun `cleanupOldDeliveries should handle repository exceptions gracefully`() = runTest {
        // Arrange
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, any()) 
        } throws RuntimeException("Database connection failed")

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupOldDeliveries(30)

        // Assert
        assertEquals(0, result, "Should return 0 on exception")
    }

    @Test
    fun `cleanupFailedDeliveries should handle repository exceptions gracefully`() = runTest {
        // Arrange
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), WebhookDeliveryStatus.FAILED, any()) 
        } throws RuntimeException("Database timeout")

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupFailedDeliveries(7)

        // Assert
        assertEquals(0, result, "Should return 0 on exception")
    }

    // ===== EDGE CASES =====

    @Test
    fun `cleanupOldDeliveries should handle very large batch returns`() = runTest {
        // Arrange
        val largeBatchSize = 10000
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, largeBatchSize) 
        } returnsMany listOf(largeBatchSize, largeBatchSize, 5000, 0)

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupOldDeliveries(30, largeBatchSize)

        // Assert
        assertEquals(25000, result, "Should handle large numbers correctly: 10000 + 10000 + 5000")
    }

    @Test
    fun `cleanupOldDeliveries should stop when batch returns less than batch size`() = runTest {
        // Arrange
        val batchSize = 1000
        coEvery { 
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, batchSize) 
        } returnsMany listOf(1000, 1000, 500) // Third batch < batchSize, should stop

        // Act
        val result = cleanupWebhookDeliveriesUseCase.cleanupOldDeliveries(30, batchSize)

        // Assert
        assertEquals(2500, result, "Should stop after partial batch")
        coVerify(exactly = 3) {
            webhookDeliveryRepository.deleteOldDeliveries(any(), null, batchSize)
        }
    }

    // ===== HELPER METHODS =====
}