package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.WebhookConfiguration
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository

class ListWebhooksUseCase(
    private val webhookRepository: WebhookConfigurationRepository
) {
    suspend fun invoke(userId: String): List<WebhookConfiguration> {
        return webhookRepository.findByUserId(userId)
    }
}