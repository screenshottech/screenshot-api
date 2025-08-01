package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.WebhookConfiguration
import dev.screenshotapi.core.domain.entities.WebhookEvent
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository
import kotlinx.datetime.Clock

class UpdateWebhookUseCase(
    private val webhookRepository: WebhookConfigurationRepository
) {
    suspend fun invoke(
        webhookId: String,
        userId: String,
        url: String? = null,
        events: Set<WebhookEvent>? = null,
        description: String? = null,
        isActive: Boolean? = null
    ): WebhookConfiguration {
        val existingWebhook = webhookRepository.findById(webhookId)
            ?: throw ResourceNotFoundException("Webhook", webhookId)
        
        if (existingWebhook.userId != userId) {
            throw ValidationException.UnauthorizedAccess("webhook", webhookId)
        }
        
        url?.let { validateWebhookUrl(it) }
        
        val updatedWebhook = existingWebhook.copy(
            url = url ?: existingWebhook.url,
            events = events ?: existingWebhook.events,
            description = description ?: existingWebhook.description,
            isActive = isActive ?: existingWebhook.isActive,
            updatedAt = Clock.System.now()
        )
        
        return webhookRepository.update(updatedWebhook)
    }
    
    private fun validateWebhookUrl(url: String) {
        when {
            !url.startsWith("https://") && !url.startsWith("http://") -> {
                throw ValidationException.InvalidFormat("url", "must start with http:// or https://")
            }
            url.startsWith("http://") && !url.contains("localhost") && !url.contains("127.0.0.1") -> {
                throw ValidationException.Custom("HTTP webhooks are only allowed for localhost", "url")
            }
            url.length > 2048 -> {
                throw ValidationException.InvalidRange("url", max = 2048)
            }
        }
    }
}