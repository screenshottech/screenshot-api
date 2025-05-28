package dev.screenshotapi.infrastructure.auth

import dev.screenshotapi.core.domain.entities.Permission
import dev.screenshotapi.core.domain.entities.UserStatus
import io.ktor.server.auth.*

data class UserPrincipal(
    val userId: String,
    val email: String,
    val name: String?,
    val status: UserStatus,
    val permissions: Set<Permission>,
    val isAdmin: Boolean = false,
    val planId: String,
    val creditsRemaining: Int
) : Principal {

    fun hasPermission(permission: Permission): Boolean {
        return permissions.contains(permission) || isAdmin
    }

    fun canAccessAdmin(): Boolean {
        return isAdmin && status == UserStatus.ACTIVE
    }

    fun canCreateScreenshots(): Boolean {
        return status == UserStatus.ACTIVE &&
                (hasPermission(Permission.SCREENSHOT_CREATE) || isAdmin) &&
                creditsRemaining > 0
    }

    fun canAccessResource(resourceUserId: String): Boolean {
        return userId == resourceUserId || isAdmin
    }
}
