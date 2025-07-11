package dev.screenshotapi.infrastructure.adapters.output.email

import dev.screenshotapi.core.domain.services.EmailProvider
import dev.screenshotapi.core.domain.services.EmailDeliveryResult
import dev.screenshotapi.core.domain.services.BulkEmailRequest
import dev.screenshotapi.core.domain.services.EmailProviderInfo
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Mock implementation of EmailProvider for testing and development
 */
class MockEmailProvider : EmailProvider {
    private val logger = LoggerFactory.getLogger(MockEmailProvider::class.java)
    
    override suspend fun sendEmail(
        to: String,
        subject: String,
        htmlContent: String,
        textContent: String?
    ): EmailDeliveryResult {
        logger.info("MOCK_EMAIL_SEND: Sending email to $to with subject '$subject'")
        logger.debug("MOCK_EMAIL_CONTENT: HTML length: ${htmlContent.length}, Text length: ${textContent?.length ?: 0}")
        
        // Simulate email sending with mock message ID
        val messageId = "mock_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
        
        return EmailDeliveryResult.success(
            messageId = messageId,
            providerMessageId = "mock_provider_$messageId"
        )
    }
    
    override suspend fun sendTemplateEmail(
        to: String,
        templateId: String,
        templateData: Map<String, Any>,
        subject: String?
    ): EmailDeliveryResult {
        logger.info("MOCK_EMAIL_TEMPLATE_SEND: Sending template email to $to with template '$templateId'")
        logger.debug("MOCK_EMAIL_TEMPLATE_DATA: ${templateData.keys.joinToString(", ")}")
        
        // Simulate template email sending
        val messageId = "mock_template_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
        
        return EmailDeliveryResult.success(
            messageId = messageId,
            providerMessageId = "mock_template_provider_$messageId"
        )
    }
    
    override suspend fun sendBulkEmails(emails: List<BulkEmailRequest>): List<EmailDeliveryResult> {
        logger.info("MOCK_EMAIL_BULK_SEND: Sending ${emails.size} bulk emails")
        
        return emails.map { email ->
            val messageId = "mock_bulk_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
            logger.debug("MOCK_EMAIL_BULK_ITEM: Sending to ${email.to} with subject '${email.subject}'")
            
            EmailDeliveryResult.success(
                messageId = messageId,
                providerMessageId = "mock_bulk_provider_$messageId"
            )
        }
    }
    
    override fun validateEmailAddress(email: String): Boolean {
        // Simple email validation
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        return email.matches(emailRegex.toRegex())
    }
    
    override fun getProviderInfo(): EmailProviderInfo {
        return EmailProviderInfo(
            name = "Mock Email Provider",
            version = "1.0.0",
            supportsBulkSending = true,
            supportsTemplates = true,
            supportsTracking = false,
            maxRecipientsPerEmail = 1,
            maxEmailsPerHour = 1000,
            maxEmailsPerDay = 10000
        )
    }
}