package dev.screenshotapi.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import dev.screenshotapi.core.domain.entities.Permission
import dev.screenshotapi.core.domain.repositories.UserRepository
import io.ktor.server.auth.jwt.*
import kotlinx.coroutines.runBlocking

class JwtAuthProvider(
    private val userRepository: UserRepository,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String
) {

    private val algorithm = Algorithm.HMAC256(jwtSecret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .build()

    fun validateJwt(credential: JWTCredential): UserPrincipal? = runBlocking {
        return@runBlocking try {
            val userId = credential.payload.getClaim("userId")?.asString()
                ?: return@runBlocking null

            val user = userRepository.findById(userId)
                ?: return@runBlocking null

            // For now, determine admin status from email domain or specific user IDs
            // In production, this should come from a roles/permissions table
            val isAdmin = isAdminUser(user.email, user.id)

            UserPrincipal(
                userId = user.id,
                email = user.email,
                name = user.name,
                status = user.status,
                permissions = getDefaultPermissions(),
                isAdmin = isAdmin,
                planId = user.planId,
                creditsRemaining = user.creditsRemaining
            )
        } catch (e: Exception) {
            null
        }
    }

    fun createToken(userId: String): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("userId", userId)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)) // 24 hours
            .sign(algorithm)
    }

    private fun isAdminUser(email: String, userId: String): Boolean {
        // Simple admin detection - in production, use proper role system
        return email.endsWith("@admin.screenshotapi.com") ||
                email == "admin@example.com" ||
                userId.startsWith("admin_")
    }

    private fun getDefaultPermissions(): Set<Permission> {
        // Default permissions for authenticated users
        return setOf(
            Permission.SCREENSHOT_CREATE,
            Permission.SCREENSHOT_READ,
            Permission.SCREENSHOT_LIST
        )
    }
}
