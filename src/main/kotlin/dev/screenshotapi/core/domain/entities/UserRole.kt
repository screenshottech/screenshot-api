package dev.screenshotapi.core.domain.entities

/**
 * User roles enum defining the hierarchical permission system.
 * 
 * Role Hierarchy (higher roles inherit lower role permissions):
 * ADMIN > BILLING_ADMIN > SUPPORT > MODERATOR > USER
 * 
 * Role Definitions:
 * - USER: Regular users - can create screenshots, manage own API keys
 * - MODERATOR: Content moderation - can review flagged content, suspend users
 * - SUPPORT: Customer support - can view user details, help with credits/issues
 * - BILLING_ADMIN: Billing management - can manage plans, resolve payment issues
 * - ADMIN: Full system access - can do everything, system configuration
 */
enum class UserRole(
    val displayName: String,
    val description: String,
    val level: Int // For hierarchy checking
) {
    USER(
        displayName = "User",
        description = "Regular user with basic permissions",
        level = 0
    ),
    
    MODERATOR(
        displayName = "Moderator", 
        description = "Content moderation and user management",
        level = 1
    ),
    
    SUPPORT(
        displayName = "Support",
        description = "Customer support and user assistance", 
        level = 2
    ),
    
    BILLING_ADMIN(
        displayName = "Billing Admin",
        description = "Billing, payments, and credit management",
        level = 3
    ),
    
    ADMIN(
        displayName = "Administrator",
        description = "Full system access and configuration",
        level = 4
    );

    /**
     * Check if this role has equal or higher privilege than the target role
     */
    fun hasPrivilegeLevel(targetRole: UserRole): Boolean {
        return this.level >= targetRole.level
    }

    /**
     * Get all roles this role can manage (same or lower level)
     */
    fun getManageableRoles(): Set<UserRole> {
        return values().filter { it.level <= this.level }.toSet()
    }

    companion object {
        /**
         * Parse role from string (case-insensitive)
         */
        fun fromString(roleString: String?): UserRole {
            if (roleString.isNullOrBlank()) return USER
            return try {
                valueOf(roleString.uppercase())
            } catch (e: IllegalArgumentException) {
                USER
            }
        }

        /**
         * Get all administrative roles (non-USER roles)
         */
        fun getAdminRoles(): Set<UserRole> {
            return values().filter { it != USER }.toSet()
        }

        /**
         * Get roles that can access admin panel
         */
        fun getAdminPanelRoles(): Set<UserRole> {
            return setOf(SUPPORT, MODERATOR, BILLING_ADMIN, ADMIN)
        }
    }
}