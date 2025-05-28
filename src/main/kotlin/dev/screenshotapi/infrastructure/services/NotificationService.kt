package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import org.slf4j.LoggerFactory

class NotificationService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun sendWebhook(webhookUrl: String, job: ScreenshotJob) {
        try {
            // TODO: Implementar envío real de webhook
            logger.info("Sending webhook to $webhookUrl for job ${job.id}")

            // Aquí iría la lógica real de HTTP POST al webhook

        } catch (e: Exception) {
            logger.error("Failed to send webhook to $webhookUrl for job ${job.id}", e)
        }
    }

    suspend fun sendEmail(to: String, subject: String, body: String) {
        try {
            // TODO: Implementar envío real de email
            logger.info("Sending email to $to: $subject")
        } catch (e: Exception) {
            logger.error("Failed to send email to $to", e)
        }
    }
}
