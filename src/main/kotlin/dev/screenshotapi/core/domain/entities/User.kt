package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class User(
    val id: String,
    val email: String,
    val name: String? = null,
    val passwordHash: String? = null,
    val planId: String,
    val planName: String = "Free",
    val creditsRemaining: Int,
    val status: UserStatus = UserStatus.ACTIVE,
    val roles: Set<UserRole> = setOf(UserRole.USER),
    val stripeCustomerId: String? = null,
    val lastActivity: Instant? = null,
    val authProvider: String = "local",
    val externalId: String? = null,
    val firstScreenshotCompletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun hasCredits(amount: Int): Boolean = creditsRemaining >= amount

    fun deductCredits(amount: Int): User =
        copy(creditsRemaining = (creditsRemaining - amount).coerceAtLeast(0))

    fun addCredits(amount: Int): User =
        copy(creditsRemaining = creditsRemaining + amount)

    fun updateLastActivity(): User =
        copy(lastActivity = Clock.System.now())

    fun hasCompletedFirstScreenshot(): Boolean = firstScreenshotCompletedAt != null

    fun markFirstScreenshotCompleted(): User =
        copy(firstScreenshotCompletedAt = Clock.System.now())

    // Role-based methods
    fun hasRole(role: UserRole): Boolean = roles.contains(role)

    fun hasAnyRole(vararg targetRoles: UserRole): Boolean =
        targetRoles.any { roles.contains(it) }

    fun hasAllRoles(vararg targetRoles: UserRole): Boolean =
        targetRoles.all { roles.contains(it) }

    fun isAdmin(): Boolean = hasRole(UserRole.ADMIN)

    fun canAccessAdminPanel(): Boolean =
        hasAnyRole(UserRole.ADMIN, UserRole.BILLING_ADMIN, UserRole.SUPPORT, UserRole.MODERATOR)

    fun canManageUsers(): Boolean =
        hasAnyRole(UserRole.ADMIN, UserRole.SUPPORT)

    fun canManageBilling(): Boolean =
        hasAnyRole(UserRole.ADMIN, UserRole.BILLING_ADMIN)

    fun canModerateContent(): Boolean =
        hasAnyRole(UserRole.ADMIN, UserRole.MODERATOR)

    fun getHighestRole(): UserRole =
        roles.maxByOrNull { it.level } ?: UserRole.USER

    fun addRole(role: UserRole): User =
        copy(roles = roles + role)

    fun removeRole(role: UserRole): User =
        copy(roles = if (roles.size > 1) roles - role else setOf(UserRole.USER))

    fun replaceRoles(newRoles: Set<UserRole>): User =
        copy(roles = if (newRoles.isEmpty()) setOf(UserRole.USER) else newRoles)
}
