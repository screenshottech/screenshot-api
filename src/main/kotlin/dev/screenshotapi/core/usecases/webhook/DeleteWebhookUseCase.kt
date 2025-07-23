package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository

class DeleteWebhookUseCase(
    private val webhookRepository: WebhookConfigurationRepository
) {
    suspend fun invoke(webhookId: String, userId: String): Boolean {
        val webhook = webhookRepository.findById(webhookId)
            ?: throw ResourceNotFoundException("Webhook", webhookId)
        
        if (webhook.userId != userId) {
            throw ValidationException.UnauthorizedAccess("webhook", webhookId)
        }
        
        return webhookRepository.delete(webhookId)
    }
}