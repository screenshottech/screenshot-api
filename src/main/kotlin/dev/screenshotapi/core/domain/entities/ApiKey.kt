package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ApiKey(
    val id: String,
    val userId: String,
    val keyHash: String,
    val name: String,
    val permissions: Set<Permission>,
    val rateLimit: Int,
    val lastUsed: Instant?,
    val isActive: Boolean,
    val createdAt: Instant,
    val usageCount: Long = 0
) {
    fun updateLastUsed(): ApiKey = copy(lastUsed = Clock.System.now())

    fun hasPermission(permission: Permission): Boolean = permissions.contains(permission)
}

enum class Permission {
    SCREENSHOT_CREATE, SCREENSHOT_READ, SCREENSHOT_LIST
}
