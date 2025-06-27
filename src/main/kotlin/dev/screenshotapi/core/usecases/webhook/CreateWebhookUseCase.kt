package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.WebhookConfiguration
import dev.screenshotapi.core.domain.entities.WebhookEvent
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.WebhookConfigurationRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import kotlinx.datetime.Clock
import java.security.SecureRandom
import java.util.Base64

class CreateWebhookUseCase(
    private val webhookRepository: WebhookConfigurationRepository,
    private val userRepository: UserRepository
) {
    companion object {
        const val MAX_WEBHOOKS_PER_USER = 10
        const val SECRET_LENGTH = 32
    }
    
    suspend fun invoke(
        userId: String,
        url: String,
        events: Set<WebhookEvent>,
        description: String? = null
    ): WebhookConfiguration {
        val user = userRepository.findById(userId)
            ?: throw ValidationException("User not found: $userId")
        
        val existingCount = webhookRepository.countByUserId(userId)
        if (existingCount >= MAX_WEBHOOKS_PER_USER) {
            throw ValidationException("Maximum number of webhooks ($MAX_WEBHOOKS_PER_USER) reached")
        }
        
        validateWebhookUrl(url)
        val secret = generateSecureSecret()
        
        val webhook = WebhookConfiguration(
            userId = userId,
            url = url,
            secret = secret,
            events = events,
            description = description,
            createdAt = Clock.System.now()
        )
        
        return webhookRepository.save(webhook)
    }
    
    private fun validateWebhookUrl(url: String) {
        when {
            !url.startsWith("https://") && !url.startsWith("http://") -> {
                throw ValidationException("Webhook URL must start with http:// or https://")
            }
            url.startsWith("http://") && !url.contains("localhost") && !url.contains("127.0.0.1") -> {
                throw ValidationException("HTTP webhooks are only allowed for localhost")
            }
            url.length > 2048 -> {
                throw ValidationException("Webhook URL is too long (max 2048 characters)")
            }
        }
    }
    
    private fun generateSecureSecret(): String {
        val bytes = ByteArray(SECRET_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}