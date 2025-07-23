package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.AnalysisJob
import dev.screenshotapi.core.domain.entities.WebhookEvent
import dev.screenshotapi.core.usecases.webhook.SendWebhookUseCase
import dev.screenshotapi.infrastructure.services.models.WebhookPayload
import dev.screenshotapi.infrastructure.services.models.AnalysisWebhookPayload
import dev.screenshotapi.infrastructure.services.models.toWebhookPayload
import dev.screenshotapi.infrastructure.services.models.toMap
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

            // Convert domain entity to DTO following Clean Architecture pattern
            val webhookPayload = job.toWebhookPayload()
            
            // Convert DTO to Map for webhook use case compatibility
            val eventData = webhookPayload.toMap()

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
     * Send webhook notifications for analysis job events
     */
    suspend fun sendAnalysisWebhook(userId: String, analysisJob: AnalysisJob, event: WebhookEvent) {
        try {
            // Convert domain entity to DTO following Clean Architecture pattern
            val webhookPayload = analysisJob.toWebhookPayload(userId)
            
            // Convert DTO to Map for webhook use case compatibility
            val eventData = webhookPayload.toMap()

            logger.info("Sending analysis webhook notifications for job: ${analysisJob.id}, event: ${event.name}, user: $userId")
            
            val deliveries = sendWebhookUseCase.sendForEvent(event, eventData, userId)
            
            if (deliveries.isNotEmpty()) {
                logger.info("Analysis webhook notifications sent: ${deliveries.size} deliveries for job ${analysisJob.id}")
            } else {
                logger.debug("No webhook configurations found for user $userId and event ${event.name}")
            }

        } catch (e: Exception) {
            logger.error("Failed to send analysis webhook notifications for job ${analysisJob.id}", e)
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
     * Send welcome email to new user
     */
    suspend fun sendWelcomeEmail(user: dev.screenshotapi.core.domain.entities.User, apiKey: String) {
        try {
            logger.info("Sending welcome email to ${user.email} for user ${user.id}")
            // TODO: Integrate with SendWelcomeEmailUseCase when DI is updated
            logger.warn("Welcome email service not fully integrated - would send welcome email to ${user.email}")
        } catch (e: Exception) {
            logger.error("Failed to send welcome email to ${user.email}", e)
        }
    }

    /**
     * Send first screenshot success email
     */
    suspend fun sendFirstScreenshotEmail(user: dev.screenshotapi.core.domain.entities.User, screenshotJob: dev.screenshotapi.core.domain.entities.ScreenshotJob) {
        try {
            logger.info("Sending first screenshot email to ${user.email} for job ${screenshotJob.id}")
            // TODO: Integrate with email service when DI is updated
            logger.warn("First screenshot email service not fully integrated - would send to ${user.email}")
        } catch (e: Exception) {
            logger.error("Failed to send first screenshot email to ${user.email}", e)
        }
    }

    /**
     * Send credit alert email
     */
    suspend fun sendCreditAlertEmail(user: dev.screenshotapi.core.domain.entities.User, usagePercent: Int, creditsUsed: Int, creditsTotal: Int) {
        try {
            logger.info("Sending credit alert email to ${user.email} for user ${user.id} (${usagePercent}% used)")
            // TODO: Integrate with SendCreditAlertUseCase when DI is updated
            logger.warn("Credit alert email service not fully integrated - would send ${usagePercent}% alert to ${user.email}")
        } catch (e: Exception) {
            logger.error("Failed to send credit alert email to ${user.email}", e)
        }
    }

    /**
     * Send upgrade campaign email
     */
    suspend fun sendUpgradeEmail(user: dev.screenshotapi.core.domain.entities.User, recommendedPlan: dev.screenshotapi.core.domain.entities.Plan) {
        try {
            logger.info("Sending upgrade email to ${user.email} for plan ${recommendedPlan.name}")
            // TODO: Integrate with upgrade email use case when DI is updated
            logger.warn("Upgrade email service not fully integrated - would send upgrade suggestion to ${user.email}")
        } catch (e: Exception) {
            logger.error("Failed to send upgrade email to ${user.email}", e)
        }
    }

    /**
     * Send first month transition email
     */
    suspend fun sendFirstMonthTransitionEmail(user: dev.screenshotapi.core.domain.entities.User, totalCreditsUsed: Int) {
        try {
            logger.info("Sending first month transition email to ${user.email} for user ${user.id}")
            // TODO: Integrate with first month transition email use case when DI is updated
            logger.warn("First month transition email service not fully integrated - would send to ${user.email}")
        } catch (e: Exception) {
            logger.error("Failed to send first month transition email to ${user.email}", e)
        }
    }

    /**
     * Legacy email method (placeholder for future implementation)
     */
    suspend fun sendEmail(to: String, subject: String, body: String) {
        try {
            logger.info("Sending email to $to: $subject")
            logger.warn("Email service not implemented - email would be sent to $to")
            // TODO: Implement email service integration (AWS SES, Gmail, etc.)
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
