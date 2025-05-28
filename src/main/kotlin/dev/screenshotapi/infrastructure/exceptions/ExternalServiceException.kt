package dev.screenshotapi.infrastructure.exceptions

sealed class ExternalServiceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class StripeException(val stripeError: String, cause: Throwable? = null) :
        ExternalServiceException("Stripe error: $stripeError", cause)

    class WebhookException(val service: String, val endpoint: String, cause: Throwable) :
        ExternalServiceException("Webhook failed for $service at $endpoint", cause)

    class EmailServiceException(val recipient: String, cause: Throwable) :
        ExternalServiceException("Failed to send email to: $recipient", cause)

    class NotificationServiceException(val type: String, cause: Throwable) :
        ExternalServiceException("Notification service failed for type: $type", cause)
}
