package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

data class UsageLog(
    val id: String,
    val userId: String,
    val apiKeyId: String?,
    val screenshotId: String?,
    val action: UsageLogAction,
    val creditsUsed: Int,
    val metadata: Map<String, String>?,
    val ipAddress: String?,
    val userAgent: String?,
    val timestamp: Instant
) {
    companion object {
        fun create(
            userId: String,
            action: UsageLogAction,
            creditsUsed: Int = 0,
            apiKeyId: String? = null,
            screenshotId: String? = null,
            metadata: Map<String, String>? = null,
            ipAddress: String? = null,
            userAgent: String? = null,
            timestamp: Instant = kotlinx.datetime.Clock.System.now()
        ): UsageLog {
            return UsageLog(
                id = generateId(),
                userId = userId,
                apiKeyId = apiKeyId,
                screenshotId = screenshotId,
                action = action,
                creditsUsed = creditsUsed,
                metadata = metadata,
                ipAddress = ipAddress,
                userAgent = userAgent,
                timestamp = timestamp
            )
        }

        private fun generateId(): String {
            return "log_${System.currentTimeMillis()}_${(1000..9999).random()}"
        }
    }
}