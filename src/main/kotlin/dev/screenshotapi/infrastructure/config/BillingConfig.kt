package dev.screenshotapi.infrastructure.config

/**
 * Configuration for billing and payment processing.
 * Following the same patterns as other config classes in the project.
 */
data class BillingConfig(
    val stripe: StripeConfig,
    val defaultPaymentProvider: String = "stripe",
    val enabledPaymentProviders: List<String> = listOf("stripe")
) {
    companion object {
        fun load(): BillingConfig {
            return BillingConfig(
                stripe = StripeConfig.load()
            )
        }
    }
}

data class StripeConfig(
    val secretKey: String,
    val publishableKey: String,
    val webhookSecret: String,
    val apiVersion: String = "2024-09-30",
    val connectTimeout: Int = 30_000, // 30 seconds
    val readTimeout: Int = 80_000,    // 80 seconds  
    val maxNetworkRetries: Int = 2
) {
    companion object {
        fun load(): StripeConfig {
            return StripeConfig(
                secretKey = System.getenv("STRIPE_SECRET_KEY") 
                    ?: "sk_test_placeholder_key",
                publishableKey = System.getenv("STRIPE_PUBLISHABLE_KEY") 
                    ?: "pk_test_placeholder_key",
                webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET") 
                    ?: "whsec_placeholder_secret"
            )
        }
    }
}