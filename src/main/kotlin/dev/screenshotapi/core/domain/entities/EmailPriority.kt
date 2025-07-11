package dev.screenshotapi.core.domain.entities

/**
 * Email priority levels for sending and rate limiting
 */
enum class EmailPriority(
    val level: Int,
    val displayName: String,
    val description: String
) {
    LOW(1, "Low", "Non-urgent promotional emails"),
    MEDIUM(2, "Medium", "Standard campaign emails"),
    HIGH(3, "High", "Important alerts and notifications"),
    URGENT(4, "Urgent", "Critical system and security alerts")
}