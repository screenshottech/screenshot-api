package dev.screenshotapi.infrastructure.config

import dev.screenshotapi.core.domain.entities.EmailType
import dev.screenshotapi.core.domain.entities.EmailCategory

/**
 * Email configuration following the established configuration patterns
 */
data class EmailConfig(
    val enabled: Boolean,
    val provider: String,
    val apiKey: String,
    val fromAddress: String,
    val fromName: String,
    val replyTo: String,
    val templateStorage: String,
    val templatePath: String,
    val welcomeEnabled: Boolean,
    val creditAlertsEnabled: Boolean,
    val upgradeCampaignsEnabled: Boolean,
    val rateLimitPerUserHour: Int,
    val rateLimitGlobalHour: Int,
    val trackingEnabled: Boolean,
    val clickTrackingEnabled: Boolean,
    val openTrackingEnabled: Boolean,
    val debugMode: Boolean,
    val testRecipient: String,
    val dashboardUrl: String,
    val docsUrl: String,
    val upgradeUrl: String,
    
    // App Branding
    val appName: String,
    val appLogo: String,
    val teamName: String,
    val apiBaseUrl: String,
    
    
    // AWS SES specific
    val awsAccessKeyId: String?,
    val awsSecretAccessKey: String?,
    val awsSesRegion: String?,
    val awsSesConfigurationSet: String?,
    
    // Gmail SMTP specific
    val gmailUsername: String?,
    val gmailAppPassword: String?,
    val smtpHost: String?,
    val smtpPort: Int?,
    val smtpAuth: Boolean,
    val smtpStartTls: Boolean,
    val smtpSslTrust: String?
) {
    companion object {
        fun load(): EmailConfig {
            return EmailConfig(
                enabled = System.getenv("EMAIL_ENABLED")?.toBoolean() ?: true,
                provider = System.getenv("EMAIL_SERVICE_PROVIDER") ?: "aws_ses",
                apiKey = System.getenv("EMAIL_API_KEY") ?: "",
                fromAddress = System.getenv("EMAIL_FROM_ADDRESS") ?: "hello@screenshotapi.dev",
                fromName = System.getenv("EMAIL_FROM_NAME") ?: "Screenshot API Team",
                replyTo = System.getenv("EMAIL_REPLY_TO") ?: "support@screenshotapi.dev",
                templateStorage = System.getenv("EMAIL_TEMPLATE_STORAGE") ?: "local",
                templatePath = System.getenv("EMAIL_TEMPLATE_PATH") ?: "./src/main/resources/email-templates",
                welcomeEnabled = System.getenv("EMAIL_WELCOME_ENABLED")?.toBoolean() ?: true,
                creditAlertsEnabled = System.getenv("EMAIL_CREDIT_ALERTS_ENABLED")?.toBoolean() ?: true,
                upgradeCampaignsEnabled = System.getenv("EMAIL_UPGRADE_CAMPAIGNS_ENABLED")?.toBoolean() ?: true,
                rateLimitPerUserHour = System.getenv("EMAIL_RATE_LIMIT_PER_USER_HOUR")?.toInt() ?: 5,
                rateLimitGlobalHour = System.getenv("EMAIL_RATE_LIMIT_GLOBAL_HOUR")?.toInt() ?: 1000,
                trackingEnabled = System.getenv("EMAIL_TRACKING_ENABLED")?.toBoolean() ?: true,
                clickTrackingEnabled = System.getenv("EMAIL_CLICK_TRACKING_ENABLED")?.toBoolean() ?: true,
                openTrackingEnabled = System.getenv("EMAIL_OPEN_TRACKING_ENABLED")?.toBoolean() ?: true,
                debugMode = System.getenv("EMAIL_DEBUG_MODE")?.toBoolean() ?: false,
                testRecipient = System.getenv("EMAIL_TEST_RECIPIENT") ?: "",
                dashboardUrl = System.getenv("EMAIL_DASHBOARD_URL") ?: "https://dashboard.screenshotapi.dev",
                docsUrl = System.getenv("EMAIL_DOCS_URL") ?: "https://docs.screenshotapi.dev",
                upgradeUrl = System.getenv("EMAIL_UPGRADE_URL") ?: "https://dashboard.screenshotapi.dev/billing",
                
                // App Branding
                appName = System.getenv("EMAIL_APP_NAME") ?: "Screenshot API",
                appLogo = System.getenv("EMAIL_APP_LOGO") ?: "📸",
                teamName = System.getenv("EMAIL_TEAM_NAME") ?: "Screenshot API Team",
                apiBaseUrl = System.getenv("EMAIL_API_BASE_URL") ?: "https://api.screenshotapi.dev",
                
                
                // AWS SES specific
                awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID"),
                awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY"),
                awsSesRegion = System.getenv("AWS_SES_REGION") ?: "us-east-1",
                awsSesConfigurationSet = System.getenv("AWS_SES_CONFIGURATION_SET"),
                
                // Gmail SMTP specific
                gmailUsername = System.getenv("GMAIL_SMTP_USERNAME"),
                gmailAppPassword = System.getenv("GMAIL_SMTP_APP_PASSWORD"),
                smtpHost = System.getenv("GMAIL_SMTP_HOST") ?: "smtp.gmail.com",
                smtpPort = System.getenv("GMAIL_SMTP_PORT")?.toInt() ?: 587,
                smtpAuth = System.getenv("GMAIL_SMTP_AUTH")?.toBoolean() ?: true,
                smtpStartTls = System.getenv("GMAIL_SMTP_START_TLS")?.toBoolean() ?: true,
                smtpSslTrust = System.getenv("GMAIL_SMTP_SSL_TRUST") ?: "smtp.gmail.com"
            )
        }
    }
    
    /**
     * Get effective API key based on provider
     */
    fun getEffectiveApiKey(): String {
        return when (provider.lowercase()) {
            "aws_ses" -> awsAccessKeyId ?: apiKey
            "gmail" -> gmailAppPassword ?: apiKey
            else -> apiKey
        }
    }
    
    /**
     * Check if provider is properly configured
     */
    fun isProviderConfigured(): Boolean {
        return when (provider.lowercase()) {
            "aws_ses" -> (awsAccessKeyId?.isNotBlank() == true && awsSecretAccessKey?.isNotBlank() == true)
            "gmail" -> (gmailUsername?.isNotBlank() == true && gmailAppPassword?.isNotBlank() == true)
            else -> apiKey.isNotBlank()
        }
    }
    
    /**
     * Get template ID for email type (for providers that support template IDs)
     */
    fun getTemplateId(emailType: EmailType): String? {
        return when (provider.lowercase()) {
            "aws_ses" -> when (emailType) {
                EmailType.WELCOME -> "welcome-template"
                EmailType.CREDIT_ALERT_50,
                EmailType.CREDIT_ALERT_80,
                EmailType.CREDIT_ALERT_90 -> "credit-alert-template"
                EmailType.UPGRADE_CAMPAIGN -> "upgrade-template"
                else -> null
            }
            else -> null
        }
    }
    
    /**
     * Check if email features are enabled
     */
    fun isEmailTypeEnabled(emailType: EmailType): Boolean {
        if (!enabled) return false
        
        return when (emailType.category) {
            EmailCategory.ONBOARDING -> welcomeEnabled
            EmailCategory.USAGE_ALERT -> creditAlertsEnabled
            EmailCategory.CONVERSION -> upgradeCampaignsEnabled
            EmailCategory.TRANSACTIONAL -> true // Always enabled
            EmailCategory.ADMINISTRATIVE -> true // Always enabled
            else -> true
        }
    }
}