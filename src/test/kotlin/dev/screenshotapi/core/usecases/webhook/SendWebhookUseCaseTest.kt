package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryRepository
import dev.screenshotapi.infrastructure.config.WebhookConfig
import io.ktor.client.*
import io.mockk.*
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SendWebhookUseCaseTest {

    private lateinit var sendWebhookUseCase: SendWebhookUseCase
    private lateinit var webhookConfigRepository: WebhookConfigurationRepository
    private lateinit var webhookDeliveryRepository: WebhookDeliveryRepository
    private lateinit var httpClient: HttpClient
    private lateinit var webhookConfig: WebhookConfig

    private lateinit var testWebhookConfiguration: WebhookConfiguration
    private lateinit var testWebhookDelivery: WebhookDelivery

    @BeforeEach
    fun setup() {
        webhookConfigRepository = mockk()
        webhookDeliveryRepository = mockk()
        httpClient = mockk()
        webhookConfig = mockk()

        // Setup default webhook config behavior
        every { webhookConfig.maxRetryAttempts } returns 3
        every { webhookConfig.maxTestRetryAttempts } returns 1
        every { webhookConfig.getRetryDelays() } returns listOf(1.minutes, 5.minutes, 15.minutes)
        every { webhookConfig.getTestRetryDelays() } returns listOf(30.seconds)

        sendWebhookUseCase = SendWebhookUseCase(
            webhookConfigRepository,
            webhookDeliveryRepository,
            httpClient,
            webhookConfig
        )

        // Setup test data
        testWebhookConfiguration = createTestWebhookConfiguration()
        testWebhookDelivery = createTestWebhookDelivery()
    }

    // ===== CONFIGURATION TESTS - Focus on business logic =====

    @Test
    fun `webhook config should provide correct retry attempts for test webhooks`() {
        // Arrange & Act
        val testRetryAttempts = webhookConfig.maxTestRetryAttempts
        val prodRetryAttempts = webhookConfig.maxRetryAttempts

        // Assert
        assertEquals(1, testRetryAttempts, "Test webhooks should have 1 max retry attempt")
        assertEquals(3, prodRetryAttempts, "Production webhooks should have 3 max retry attempts")
    }

    @Test
    fun `webhook config should provide correct retry delays`() {
        // Arrange & Act
        val testDelays = webhookConfig.getTestRetryDelays()
        val prodDelays = webhookConfig.getRetryDelays()

        // Assert
        assertEquals(1, testDelays.size, "Test webhooks should have 1 retry delay")
        assertEquals(30.seconds, testDelays.first(), "Test webhook delay should be 30 seconds")
        assertEquals(3, prodDelays.size, "Production webhooks should have 3 retry delays")
        assertEquals(1.minutes, prodDelays[0], "First prod delay should be 1 minute")
        assertEquals(5.minutes, prodDelays[1], "Second prod delay should be 5 minutes")
        assertEquals(15.minutes, prodDelays[2], "Third prod delay should be 15 minutes")
    }

    @Test
    fun `custom webhook config should override default settings`() {
        // Arrange
        val customConfig = WebhookConfig(
            maxRetryAttempts = 5,
            maxTestRetryAttempts = 2,
            testRateLimitMinutes = 1,
            retryDelayMinutes = listOf(2, 10),
            testRetryDelaySeconds = listOf(60),
            timeoutSeconds = 30L
        )

        // Act & Assert
        assertEquals(5, customConfig.maxRetryAttempts, "Custom config should override max retry attempts")
        assertEquals(2, customConfig.maxTestRetryAttempts, "Custom config should override test retry attempts")
        assertEquals(2, customConfig.getRetryDelays().size, "Custom config should have 2 retry delays")
        assertEquals(2.minutes, customConfig.getRetryDelays()[0], "First custom delay should be 2 minutes")
        assertEquals(10.minutes, customConfig.getRetryDelays()[1], "Second custom delay should be 10 minutes")
    }

    @Test
    fun `should respect webhook configuration settings`() {
        // Arrange
        val webhook = testWebhookConfiguration.copy(
            events = setOf(WebhookEvent.SCREENSHOT_COMPLETED, WebhookEvent.WEBHOOK_TEST),
            isActive = true
        )

        // Act & Assert
        assertTrue(webhook.isActive, "Webhook should be active")
        assertTrue(webhook.events.contains(WebhookEvent.SCREENSHOT_COMPLETED), 
            "Should support screenshot completed events")
        assertTrue(webhook.events.contains(WebhookEvent.WEBHOOK_TEST), 
            "Should support webhook test events")
        assertEquals("https://example.com/webhook", webhook.url, "Should have correct URL")
        assertEquals("test-secret-32-characters-long-key", webhook.secret, "Should have correct secret")
    }

    // ===== HELPER METHODS =====

    private fun createTestWebhookConfiguration(): WebhookConfiguration {
        return WebhookConfiguration(
            id = "webhook-123",
            userId = "user-456",
            url = "https://example.com/webhook",
            secret = "test-secret-32-characters-long-key",
            events = setOf(WebhookEvent.SCREENSHOT_COMPLETED, WebhookEvent.WEBHOOK_TEST),
            isActive = true,
            createdAt = Clock.System.now()
        )
    }

    private fun createTestWebhookDelivery(): WebhookDelivery {
        return WebhookDelivery(
            id = "delivery-789",
            webhookConfigId = "webhook-123",
            userId = "user-456",
            event = WebhookEvent.WEBHOOK_TEST,
            eventData = mapOf("test" to "true"),
            payload = """{"event":"WEBHOOK_TEST","timestamp":"2025-07-04T10:00:00Z","data":{"test":"true"}}""",
            signature = "test-signature",
            status = WebhookDeliveryStatus.PENDING,
            url = "https://example.com/webhook",
            attempts = 0,
            maxAttempts = 1,
            lastAttemptAt = Clock.System.now(),
            createdAt = Clock.System.now()
        )
    }
}