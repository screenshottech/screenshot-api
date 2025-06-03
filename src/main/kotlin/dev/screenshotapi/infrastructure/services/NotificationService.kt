package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.infrastructure.services.models.WebhookPayload
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory


class NotificationService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }

    suspend fun sendWebhook(webhookUrl: String, job: ScreenshotJob) {
        try {
            logger.info("Sending webhook to $webhookUrl for job ${job.id}")

            val payload = WebhookPayload(
                jobId = job.id,
                status = job.status.name.lowercase(),
                url = job.request.url,
                resultUrl = job.resultUrl,
                errorMessage = job.errorMessage,
                processingTimeMs = job.processingTimeMs,
                completedAt = job.completedAt?.toString()
            )

            val response: HttpResponse = httpClient.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            if (response.status.isSuccess()) {
                logger.info("Webhook sent successfully to $webhookUrl for job ${job.id}")
            } else {
                logger.warn("Webhook failed with status ${response.status} for job ${job.id}")
            }

        } catch (e: Exception) {
            logger.error("Failed to send webhook to $webhookUrl for job ${job.id}", e)
        }
    }

    suspend fun sendEmail(to: String, subject: String, body: String) {
        try {
            logger.info("Sending email to $to: $subject")
            logger.warn("Email service not implemented - email would be sent to $to")
        } catch (e: Exception) {
            logger.error("Failed to send email to $to", e)
        }
    }

    fun close() {
        httpClient.close()
    }
}
