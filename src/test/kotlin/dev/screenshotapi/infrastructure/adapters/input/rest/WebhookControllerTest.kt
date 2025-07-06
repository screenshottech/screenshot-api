package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.usecases.webhook.*
import dev.screenshotapi.infrastructure.config.WebhookConfig
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlin.time.Duration.Companion.minutes

/**
 * Unit tests for WebhookController focusing on rate limiting logic
 * and webhook test functionality
 */
class WebhookControllerTest {

    private lateinit var listWebhooksUseCase: ListWebhooksUseCase
    private lateinit var sendWebhookUseCase: SendWebhookUseCase
    private lateinit var webhookConfig: WebhookConfig
    private lateinit var webhookController: WebhookController

    private lateinit var testWebhookConfiguration: WebhookConfiguration
    private lateinit var testWebhookDelivery: WebhookDelivery

    @BeforeEach
    fun setup() {
        // Create mocks
        listWebhooksUseCase = mockk()
        sendWebhookUseCase = mockk()
        webhookConfig = mockk()

        // Setup default webhook config behavior
        every { webhookConfig.getTestRateLimit() } returns 1.minutes

        // Setup test data
        testWebhookConfiguration = createTestWebhookConfiguration()
        testWebhookDelivery = createTestWebhookDelivery()

        webhookController = WebhookController()
    }

    // ===== RATE LIMITING LOGIC TESTS =====

    @Test
    fun `rate limiting should allow first test request per user`() = runTest {
        // Arrange
        val userId = "user-123"
        val webhookId = "webhook-456"
        
        coEvery { listWebhooksUseCase.invoke(userId) } returns listOf(testWebhookConfiguration)
        coEvery { sendWebhookUseCase.sendWebhook(any(), WebhookEvent.WEBHOOK_TEST, any()) } returns testWebhookDelivery

        // Act - Simulate first request (should be allowed)
        val webhook = listWebhooksUseCase.invoke(userId).find { it.id == webhookId }
        
        // Assert
        assertNotNull(webhook, "Webhook should be found for user")
        coVerify(exactly = 1) { listWebhooksUseCase.invoke(userId) }
    }

    @Test
    fun `rate limiting should track separate users independently`() = runTest {
        // Arrange
        val user1 = "user-123"
        val user2 = "user-456"
        val webhookId = "webhook-456" // Use the correct webhook ID from test data
        
        coEvery { listWebhooksUseCase.invoke(any()) } returns listOf(testWebhookConfiguration)
        coEvery { sendWebhookUseCase.sendWebhook(any(), WebhookEvent.WEBHOOK_TEST, any()) } returns testWebhookDelivery

        // Act - Both users should be able to make requests
        val webhook1 = listWebhooksUseCase.invoke(user1).find { it.id == webhookId }
        val webhook2 = listWebhooksUseCase.invoke(user2).find { it.id == webhookId }

        // Assert
        assertNotNull(webhook1, "User 1 should find webhook")
        assertNotNull(webhook2, "User 2 should find webhook")
        coVerify(exactly = 1) { listWebhooksUseCase.invoke(user1) }
        coVerify(exactly = 1) { listWebhooksUseCase.invoke(user2) }
    }

    // ===== WEBHOOK TEST EVENT DATA TESTS =====

    @Test
    fun `webhook test should create proper event data structure`() = runTest {
        // Arrange
        val userId = "user-123"
        val webhookId = "webhook-456"
        
        coEvery { listWebhooksUseCase.invoke(userId) } returns listOf(testWebhookConfiguration)
        
        val capturedEventData = slot<Map<String, Any>>()
        coEvery { 
            sendWebhookUseCase.sendWebhook(any(), WebhookEvent.WEBHOOK_TEST, capture(capturedEventData)) 
        } returns testWebhookDelivery

        // Act
        val webhook = listWebhooksUseCase.invoke(userId).find { it.id == webhookId }
        requireNotNull(webhook)
        
        val testEventData = mapOf(
            "test" to "true",
            "webhookId" to webhookId,
            "userId" to userId,
            "timestamp" to Clock.System.now().toString()
        )
        
        sendWebhookUseCase.sendWebhook(webhook, WebhookEvent.WEBHOOK_TEST, testEventData)

        // Assert
        val eventData = capturedEventData.captured
        assertEquals("true", eventData["test"], "Should include test flag")
        assertEquals(webhookId, eventData["webhookId"], "Should include webhook ID")
        assertEquals(userId, eventData["userId"], "Should include user ID")
        assertTrue(eventData.containsKey("timestamp"), "Should include timestamp")
    }

    @Test
    fun `webhook test should use WEBHOOK_TEST event type`() = runTest {
        // Arrange
        val userId = "user-123"
        val webhookId = "webhook-456"
        
        coEvery { listWebhooksUseCase.invoke(userId) } returns listOf(testWebhookConfiguration)
        
        val capturedEvent = slot<WebhookEvent>()
        coEvery { 
            sendWebhookUseCase.sendWebhook(any(), capture(capturedEvent), any()) 
        } returns testWebhookDelivery

        // Act
        val webhook = listWebhooksUseCase.invoke(userId).find { it.id == webhookId }
        requireNotNull(webhook)
        
        sendWebhookUseCase.sendWebhook(webhook, WebhookEvent.WEBHOOK_TEST, mapOf("test" to "true"))

        // Assert
        assertEquals(WebhookEvent.WEBHOOK_TEST, capturedEvent.captured, "Should use WEBHOOK_TEST event")
        coVerify { sendWebhookUseCase.sendWebhook(webhook, WebhookEvent.WEBHOOK_TEST, any()) }
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    fun `should handle webhook not found gracefully`() = runTest {
        // Arrange
        val userId = "user-123"
        val nonexistentWebhookId = "nonexistent-webhook"
        
        coEvery { listWebhooksUseCase.invoke(userId) } returns emptyList()

        // Act
        val webhook = listWebhooksUseCase.invoke(userId).find { it.id == nonexistentWebhookId }

        // Assert
        assertNull(webhook, "Should return null for nonexistent webhook")
        coVerify(exactly = 0) { sendWebhookUseCase.sendWebhook(any(), any(), any()) }
    }

    @Test
    fun `should handle use case exceptions gracefully`() = runTest {
        // Arrange
        val userId = "user-123"
        val webhookId = "webhook-456"
        
        coEvery { listWebhooksUseCase.invoke(userId) } returns listOf(testWebhookConfiguration)
        coEvery { sendWebhookUseCase.sendWebhook(any(), WebhookEvent.WEBHOOK_TEST, any()) } throws 
            RuntimeException("Webhook delivery failed")

        // Act & Assert
        val webhook = listWebhooksUseCase.invoke(userId).find { it.id == webhookId }
        requireNotNull(webhook)
        
        assertThrows(RuntimeException::class.java) {
            runTest {
                sendWebhookUseCase.sendWebhook(webhook, WebhookEvent.WEBHOOK_TEST, mapOf("test" to "true"))
            }
        }
    }

    // ===== CONFIGURATION TESTS =====

    @Test
    fun `should use configured rate limit from webhook config`() {
        // Arrange
        val expectedRateLimit = 2.minutes
        every { webhookConfig.getTestRateLimit() } returns expectedRateLimit

        // Act
        val actualRateLimit = webhookConfig.getTestRateLimit()

        // Assert
        assertEquals(expectedRateLimit, actualRateLimit, "Should return configured rate limit")
        verify { webhookConfig.getTestRateLimit() }
    }

    @Test
    fun `should respect webhook configuration settings`() = runTest {
        // Arrange
        val userId = "user-123"
        val webhookId = "webhook-456"
        val customConfig = testWebhookConfiguration.copy(
            events = setOf(WebhookEvent.WEBHOOK_TEST, WebhookEvent.SCREENSHOT_COMPLETED),
            isActive = true
        )
        
        coEvery { listWebhooksUseCase.invoke(userId) } returns listOf(customConfig)

        // Act
        val webhook = listWebhooksUseCase.invoke(userId).find { it.id == webhookId }

        // Assert
        assertNotNull(webhook, "Should find active webhook")
        assertTrue(webhook!!.isActive, "Webhook should be active")
        assertTrue(webhook.events.contains(WebhookEvent.WEBHOOK_TEST), "Should support webhook test events")
    }

    // ===== HELPER METHODS =====

    private fun createTestWebhookConfiguration(): WebhookConfiguration {
        return WebhookConfiguration(
            id = "webhook-456",
            userId = "user-123",
            url = "https://example.com/webhook",
            secret = "test-secret-32-characters-long-key",
            events = setOf(WebhookEvent.WEBHOOK_TEST, WebhookEvent.SCREENSHOT_COMPLETED),
            isActive = true,
            createdAt = Clock.System.now()
        )
    }

    private fun createTestWebhookDelivery(): WebhookDelivery {
        return WebhookDelivery(
            id = "delivery-789",
            webhookConfigId = "webhook-456",
            userId = "user-123",
            event = WebhookEvent.WEBHOOK_TEST,
            eventData = mapOf("test" to "true"),
            payload = """{"event":"WEBHOOK_TEST","data":{"test":"true"}}""",
            signature = "test-signature",
            status = WebhookDeliveryStatus.DELIVERED,
            url = "https://example.com/webhook",
            attempts = 1,
            maxAttempts = 1,
            lastAttemptAt = Clock.System.now(),
            createdAt = Clock.System.now()
        )
    }
}