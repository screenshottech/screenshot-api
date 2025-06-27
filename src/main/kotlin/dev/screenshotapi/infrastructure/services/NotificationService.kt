package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.WebhookEvent
import dev.screenshotapi.core.usecases.webhook.SendWebhookUseCase
import dev.screenshotapi.infrastructure.services.models.WebhookPayload
import org.slf4j.LoggerFactory

class NotificationService(
    private val sendWebhookUseCase: SendWebhookUseCase
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Send webhook notifications for screenshot job events
     */
    suspend fun notifyScreenshotEvent(job: ScreenshotJob) {
        try {
            val event = when (job.status.name) {
                "COMPLETED" -> WebhookEvent.SCREENSHOT_COMPLETED
                "FAILED" -> WebhookEvent.SCREENSHOT_FAILED
                else -> return // Only notify on completion or failure
            }

            val eventData = mapOf(
                "jobId" to job.id,
                "status" to job.status.name.lowercase(),
                "url" to job.request.url,
                "resultUrl" to (job.resultUrl ?: ""),
                "errorMessage" to (job.errorMessage ?: ""),
                "processingTimeMs" to (job.processingTimeMs ?: 0),
                "completedAt" to (job.completedAt?.toString() ?: ""),
                "createdAt" to job.createdAt.toString(),
                "userId" to job.userId,
                "format" to job.request.format.name,
                "width" to job.request.width,
                "height" to job.request.height
            )

            logger.info("Sending webhook notifications for job: ${job.id}, event: ${event.name}, user: ${job.userId}")
            
            val deliveries = sendWebhookUseCase.sendForEvent(event, eventData, job.userId)
            
            if (deliveries.isNotEmpty()) {
                logger.info("Webhook notifications sent: ${deliveries.size} deliveries for job ${job.id}")
            } else {
                logger.debug("No webhook configurations found for user ${job.userId} and event ${event.name}")
            }

        } catch (e: Exception) {
            logger.error("Failed to send webhook notifications for job ${job.id}", e)
        }
    }

    /**
     * Legacy method for backward compatibility - deprecated
     */
    @Deprecated("Use notifyScreenshotEvent instead", ReplaceWith("notifyScreenshotEvent(job)"))
    suspend fun sendWebhook(webhookUrl: String, job: ScreenshotJob) {
        logger.warn("Legacy sendWebhook method called - this is deprecated. Use webhook configurations instead.")
        // For backward compatibility, we could still support this but it's not recommended
    }

    /**
     * Send email notifications (placeholder for future implementation)
     */
    suspend fun sendEmail(to: String, subject: String, body: String) {
        try {
            logger.info("Sending email to $to: $subject")
            logger.warn("Email service not implemented - email would be sent to $to")
            // TODO: Implement email service integration (SendGrid, AWS SES, etc.)
        } catch (e: Exception) {
            logger.error("Failed to send email to $to", e)
        }
    }

    /**
     * Send user registration notifications
     */
    suspend fun notifyUserRegistration(userId: String, email: String) {
        try {
            val eventData = mapOf(
                "userId" to userId,
                "email" to email,
                "registeredAt" to kotlinx.datetime.Clock.System.now().toString()
            )

            logger.info("Sending user registration notifications for user: $userId")
            
            val deliveries = sendWebhookUseCase.sendForEvent(WebhookEvent.USER_REGISTERED, eventData)
            
            if (deliveries.isNotEmpty()) {
                logger.info("User registration notifications sent: ${deliveries.size} deliveries")
            }

        } catch (e: Exception) {
            logger.error("Failed to send user registration notifications for user $userId", e)
        }
    }

    /**
     * Send payment notifications
     */
    suspend fun notifyPaymentEvent(userId: String, paymentData: Map<String, Any>) {
        try {
            logger.info("Sending payment notifications for user: $userId")
            
            val deliveries = sendWebhookUseCase.sendForEvent(WebhookEvent.PAYMENT_PROCESSED, paymentData, userId)
            
            if (deliveries.isNotEmpty()) {
                logger.info("Payment notifications sent: ${deliveries.size} deliveries")
            }

        } catch (e: Exception) {
            logger.error("Failed to send payment notifications for user $userId", e)
        }
    }
}
