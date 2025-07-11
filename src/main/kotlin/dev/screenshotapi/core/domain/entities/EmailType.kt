package dev.screenshotapi.core.domain.entities

/**
 * Enum representing different types of emails that can be sent by the system.
 * This is a core business concept that defines the various email campaigns and notifications.
 */
enum class EmailType(
    val displayName: String,
    val description: String,
    val category: EmailCategory,
    val priority: EmailPriority = EmailPriority.MEDIUM
) {
    // Onboarding emails
    WELCOME(
        "Welcome Email",
        "Welcome new users and drive first API call",
        EmailCategory.ONBOARDING,
        EmailPriority.HIGH
    ),
    
    FIRST_SCREENSHOT(
        "First Screenshot Success",
        "Celebrate first successful screenshot and encourage continued usage",
        EmailCategory.ONBOARDING,
        EmailPriority.HIGH
    ),
    
    // Usage alerts
    CREDIT_ALERT_50(
        "Credit Alert - 50% Used",
        "Positive reinforcement and usage awareness at 50% credit usage",
        EmailCategory.USAGE_ALERT,
        EmailPriority.MEDIUM
    ),
    
    CREDIT_ALERT_80(
        "Credit Alert - 80% Used",
        "Upgrade suggestion with urgency at 80% credit usage",
        EmailCategory.USAGE_ALERT,
        EmailPriority.HIGH
    ),
    
    CREDIT_ALERT_90(
        "Credit Alert - 90% Used",
        "Final warning with immediate upgrade CTA at 90% credit usage",
        EmailCategory.USAGE_ALERT,
        EmailPriority.HIGH
    ),
    
    // Lifecycle emails
    FIRST_MONTH_TRANSITION(
        "First Month Transition",
        "Explain credit reduction from 300 to 100 credits after first month",
        EmailCategory.LIFECYCLE,
        EmailPriority.HIGH
    ),
    
    UPGRADE_CAMPAIGN(
        "Upgrade Campaign",
        "Smart upgrade recommendations based on usage patterns",
        EmailCategory.CONVERSION,
        EmailPriority.MEDIUM
    ),
    
    // Retention emails
    CREDITS_EXHAUSTED(
        "Credits Exhausted",
        "Credits have been exhausted, encourage upgrade or wait for reset",
        EmailCategory.RETENTION,
        EmailPriority.HIGH
    ),
    
    DORMANT_USER(
        "Dormant User Re-engagement",
        "Re-engage users who haven't used the service in a while",
        EmailCategory.RETENTION,
        EmailPriority.LOW
    ),
    
    // Transactional emails
    PAYMENT_SUCCESSFUL(
        "Payment Successful",
        "Confirmation of successful payment and plan upgrade",
        EmailCategory.TRANSACTIONAL,
        EmailPriority.HIGH
    ),
    
    PAYMENT_FAILED(
        "Payment Failed",
        "Payment failure notification with retry instructions",
        EmailCategory.TRANSACTIONAL,
        EmailPriority.HIGH
    ),
    
    // Administrative emails
    ACCOUNT_SUSPENDED(
        "Account Suspended",
        "Account suspension notification with resolution steps",
        EmailCategory.ADMINISTRATIVE,
        EmailPriority.HIGH
    ),
    
    SECURITY_ALERT(
        "Security Alert",
        "Security-related notifications and alerts",
        EmailCategory.ADMINISTRATIVE,
        EmailPriority.HIGH
    );

    companion object {
        /**
         * Get email type by name, case-insensitive
         */
        fun fromString(value: String): EmailType? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }
        
        /**
         * Get all email types
         */
        fun getAllTypes(): List<EmailType> {
            return values().toList()
        }
        
        /**
         * Get email types by category
         */
        fun getByCategory(category: EmailCategory): List<EmailType> {
            return values().filter { it.category == category }
        }
        
        /**
         * Get email types by priority
         */
        fun getByPriority(priority: EmailPriority): List<EmailType> {
            return values().filter { it.priority == priority }
        }
        
        /**
         * Get credit alert types in order
         */
        fun getCreditAlertTypes(): List<EmailType> {
            return listOf(CREDIT_ALERT_50, CREDIT_ALERT_80, CREDIT_ALERT_90)
        }
        
        /**
         * Get onboarding email types in order
         */
        fun getOnboardingTypes(): List<EmailType> {
            return listOf(WELCOME, FIRST_SCREENSHOT)
        }
        
        /**
         * Check if email type is a credit alert
         */
        fun isCreditAlert(emailType: EmailType): Boolean {
            return emailType in getCreditAlertTypes()
        }
        
        /**
         * Check if email type is transactional (should not be suppressed)
         */
        fun isTransactional(emailType: EmailType): Boolean {
            return emailType.category == EmailCategory.TRANSACTIONAL ||
                   emailType.category == EmailCategory.ADMINISTRATIVE
        }
        
        /**
         * Get credit threshold for alert emails
         */
        fun getCreditThreshold(emailType: EmailType): Int? {
            return when (emailType) {
                CREDIT_ALERT_50 -> 50
                CREDIT_ALERT_80 -> 80
                CREDIT_ALERT_90 -> 90
                else -> null
            }
        }
    }
}