package dev.screenshotapi.core.domain.exceptions

sealed class PaymentException(message: String, cause: Throwable? = null) : BusinessException(message, cause) {
    class PaymentFailed(val reason: String) : PaymentException("Payment failed: $reason")
    class SubscriptionNotFound(val subscriptionId: String) : PaymentException("Subscription not found: $subscriptionId")
    class InvoiceProcessingFailed(val invoiceId: String, cause: Throwable) :
        PaymentException("Failed to process invoice: $invoiceId", cause)
    class ConfigurationError(message: String) : PaymentException("Payment gateway configuration error: $message")
    class WebhookVerificationFailed : PaymentException("Webhook signature verification failed")
}
