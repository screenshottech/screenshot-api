package dev.screenshotapi.core.domain.entities

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
    val stripeCustomerId: String? = null,
    val lastActivity: Instant? = null,
    val authProvider: String = "local",
    val externalId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun hasCredits(amount: Int): Boolean = creditsRemaining >= amount

    fun deductCredits(amount: Int): User =
        copy(creditsRemaining = (creditsRemaining - amount).coerceAtLeast(0))

    fun addCredits(amount: Int): User =
        copy(creditsRemaining = creditsRemaining + amount)

    fun updateLastActivity(): User =
        copy(lastActivity = kotlinx.datetime.Clock.System.now())
}
