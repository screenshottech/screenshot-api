package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ApiKey(
    val id: String,
    val userId: String,
    val name: String,
    val keyHash: String,
    val keyPrefix: String,
    val permissions: Set<Permission>,
    val rateLimit: Int,
    val usageCount: Long = 0,
    val isActive: Boolean,
    val isDefault: Boolean = false,
    val lastUsed: Instant?,
    val expiresAt: Instant? = null,
    val createdAt: Instant
) {
    fun updateLastUsed(): ApiKey = copy(lastUsed = Clock.System.now())

    fun hasPermission(permission: Permission): Boolean = permissions.contains(permission)
}

enum class Permission {
    SCREENSHOT_CREATE, SCREENSHOT_READ, SCREENSHOT_LIST
}
