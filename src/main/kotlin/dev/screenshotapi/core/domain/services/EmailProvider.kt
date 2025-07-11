package dev.screenshotapi.core.domain.services

import dev.screenshotapi.core.domain.entities.EmailType

/**
 * Interface for email service providers (AWS SES, Gmail SMTP, etc.)
 * This follows the hexagonal architecture pattern as an output port
 */
interface EmailProvider {
    /**
     * Send a basic email
     */
    suspend fun sendEmail(
        to: String,
        subject: String,
        htmlContent: String,
        textContent: String? = null
    ): EmailDeliveryResult
    
    /**
     * Send email using a template
     */
    suspend fun sendTemplateEmail(
        to: String,
        templateId: String,
        templateData: Map<String, Any>,
        subject: String? = null
    ): EmailDeliveryResult
    
    /**
     * Send bulk emails (batch processing)
     */
    suspend fun sendBulkEmails(
        emails: List<BulkEmailRequest>
    ): List<EmailDeliveryResult>
    
    /**
     * Validate email address format
     */
    fun validateEmailAddress(email: String): Boolean
    
    /**
     * Get provider-specific configuration
     */
    fun getProviderInfo(): EmailProviderInfo
}

/**
 * Request for bulk email sending
 */
data class BulkEmailRequest(
    val to: String,
    val subject: String,
    val htmlContent: String,
    val textContent: String? = null,
    val templateId: String? = null,
    val templateData: Map<String, Any> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Result of email delivery attempt
 */
data class EmailDeliveryResult(
    val success: Boolean,
    val messageId: String?,
    val providerMessageId: String? = null,
    val error: String? = null,
    val statusCode: Int? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        fun success(messageId: String, providerMessageId: String? = null): EmailDeliveryResult {
            return EmailDeliveryResult(
                success = true,
                messageId = messageId,
                providerMessageId = providerMessageId
            )
        }
        
        fun failure(error: String, statusCode: Int? = null): EmailDeliveryResult {
            return EmailDeliveryResult(
                success = false,
                messageId = null,
                error = error,
                statusCode = statusCode
            )
        }
    }
}

/**
 * Information about the email provider
 */
data class EmailProviderInfo(
    val name: String,
    val version: String,
    val supportsBulkSending: Boolean,
    val supportsTemplates: Boolean,
    val supportsTracking: Boolean,
    val maxRecipientsPerEmail: Int,
    val maxEmailsPerHour: Int,
    val maxEmailsPerDay: Int
)

/**
 * Email delivery status for tracking
 */
enum class EmailDeliveryStatus {
    SENT,
    DELIVERED,
    OPENED,
    CLICKED,
    BOUNCED,
    COMPLAINED,
    UNSUBSCRIBED,
    FAILED
}

/**
 * Email tracking event
 */
data class EmailTrackingEvent(
    val messageId: String,
    val status: EmailDeliveryStatus,
    val timestamp: kotlinx.datetime.Instant,
    val metadata: Map<String, String> = emptyMap()
)