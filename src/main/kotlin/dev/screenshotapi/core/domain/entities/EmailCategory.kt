package dev.screenshotapi.core.domain.entities

/**
 * Categories of emails for organization and filtering
 */
enum class EmailCategory(
    val displayName: String,
    val description: String
) {
    ONBOARDING("Onboarding", "Welcome and initial user activation emails"),
    USAGE_ALERT("Usage Alert", "Credit usage and limit notifications"),
    LIFECYCLE("Lifecycle", "User lifecycle transition emails"),
    CONVERSION("Conversion", "Upgrade and conversion campaigns"),
    RETENTION("Retention", "User retention and re-engagement emails"),
    TRANSACTIONAL("Transactional", "Payment and account transaction emails"),
    ADMINISTRATIVE("Administrative", "System and security notifications")
}