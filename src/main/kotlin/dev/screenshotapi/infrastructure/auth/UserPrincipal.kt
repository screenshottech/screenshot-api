package dev.screenshotapi.infrastructure.auth

import dev.screenshotapi.core.domain.entities.Permission
import dev.screenshotapi.core.domain.entities.UserStatus
import dev.screenshotapi.core.domain.entities.UserRole

data class UserPrincipal(
    val userId: String,
    val email: String,
    val name: String?,
    val status: UserStatus,
    val permissions: Set<Permission>,
    val roles: Set<UserRole> = setOf(UserRole.USER),
    val planId: String,
    val creditsRemaining: Int
) {
    // Legacy support - keep for backward compatibility during transition
    val isAdmin: Boolean get() = hasRole(UserRole.ADMIN)

    // Role-based methods
    fun hasRole(role: UserRole): Boolean = roles.contains(role)
    
    fun hasAnyRole(vararg targetRoles: UserRole): Boolean = 
        targetRoles.any { roles.contains(it) }
    
    fun hasAllRoles(vararg targetRoles: UserRole): Boolean = 
        targetRoles.all { roles.contains(it) }

    // Permission methods (enhanced with role-based logic)
    fun hasPermission(permission: Permission): Boolean {
        return permissions.contains(permission) || hasRole(UserRole.ADMIN)
    }

    // Access control methods
    fun canAccessAdmin(): Boolean {
        return status == UserStatus.ACTIVE && 
               hasAnyRole(UserRole.ADMIN, UserRole.BILLING_ADMIN, UserRole.SUPPORT, UserRole.MODERATOR)
    }

    fun canManageUsers(): Boolean {
        return status == UserStatus.ACTIVE && 
               hasAnyRole(UserRole.ADMIN, UserRole.SUPPORT)
    }

    fun canManageBilling(): Boolean {
        return status == UserStatus.ACTIVE && 
               hasAnyRole(UserRole.ADMIN, UserRole.BILLING_ADMIN)
    }

    fun canModerateContent(): Boolean {
        return status == UserStatus.ACTIVE && 
               hasAnyRole(UserRole.ADMIN, UserRole.MODERATOR)
    }

    fun canViewSystemStats(): Boolean {
        return status == UserStatus.ACTIVE && 
               hasAnyRole(UserRole.ADMIN, UserRole.SUPPORT)
    }

    fun canCreateScreenshots(): Boolean {
        return status == UserStatus.ACTIVE &&
                (hasPermission(Permission.SCREENSHOT_CREATE) || hasRole(UserRole.ADMIN)) &&
                creditsRemaining > 0
    }

    fun canAccessResource(resourceUserId: String): Boolean {
        return userId == resourceUserId || hasRole(UserRole.ADMIN) || hasRole(UserRole.SUPPORT)
    }

    fun getHighestRole(): UserRole = 
        roles.maxByOrNull { it.level } ?: UserRole.USER
}
