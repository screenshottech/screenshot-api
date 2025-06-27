package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.WebhookConfiguration
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository
import kotlinx.datetime.Clock
import java.security.SecureRandom
import java.util.Base64

class RegenerateWebhookSecretUseCase(
    private val webhookRepository: WebhookConfigurationRepository
) {
    companion object {
        const val SECRET_LENGTH = 32
    }

    suspend fun invoke(webhookId: String, userId: String): WebhookConfiguration {
        val existingWebhook = webhookRepository.findById(webhookId)
            ?: throw ResourceNotFoundException("Webhook", webhookId)
        
        if (existingWebhook.userId != userId) {
            throw ValidationException("Unauthorized access to webhook: $webhookId")
        }
        
        val newSecret = generateSecureSecret()
        
        val updatedWebhook = existingWebhook.copy(
            secret = newSecret,
            updatedAt = Clock.System.now()
        )
        
        return webhookRepository.update(updatedWebhook)
    }
    
    private fun generateSecureSecret(): String {
        val bytes = ByteArray(SECRET_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}