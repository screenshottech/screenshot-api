package dev.screenshotapi.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import dev.screenshotapi.core.domain.entities.Permission
import dev.screenshotapi.core.domain.repositories.UserRepository
import io.ktor.server.auth.jwt.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class JwtAuthProvider(
    private val userRepository: UserRepository,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
    private val jwtExpirationHours: Int
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .build()

    fun validateJwt(credential: JWTCredential): UserPrincipal? = runBlocking {
        return@runBlocking try {
            // Try standard JWT subject claim first, then fallback to custom userId claim
            val userId = credential.payload.subject ?: credential.payload.getClaim("userId")?.asString()
            
            if (userId == null) {
                logger.warn("JWT validation failed: No userId found in token")
                return@runBlocking null
            }

            val user = userRepository.findById(userId)
            if (user == null) {
                logger.warn("JWT validation failed: User not found for userId: $userId")
                return@runBlocking null
            }

            logger.debug("JWT validation successful for user: $userId")

            // Get roles from JWT first, fallback to database
            val jwtRoles = credential.payload.getClaim("roles")?.asList(String::class.java)
            
            val userRoles = if (!jwtRoles.isNullOrEmpty()) {
                // Convert JWT roles to UserRole enum
                val convertedRoles = jwtRoles.mapNotNull { roleName ->
                    try {
                        dev.screenshotapi.core.domain.entities.UserRole.valueOf(roleName)
                    } catch (e: Exception) {
                        logger.warn("Failed to convert JWT role: $roleName")
                        null
                    }
                }.toSet()
                convertedRoles
            } else {
                // Fallback to database roles
                user.roles
            }

            UserPrincipal(
                userId = user.id,
                email = user.email,
                name = user.name,
                status = user.status,
                permissions = getDefaultPermissions(),
                roles = userRoles,
                planId = user.planId,
                creditsRemaining = user.creditsRemaining
            )
        } catch (e: Exception) {
            logger.error("JWT validation failed with exception: ${e.message}", e)
            null
        }
    }

    suspend fun createToken(userId: String): String {
        val user = userRepository.findById(userId)
        val userRoles = user?.roles?.map { it.name } ?: listOf("USER")
        
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withSubject(userId)
            .withClaim("userId", userId)
            .withClaim("roles", userRoles)
            .withClaim("email", user?.email)
            .withIssuedAt(java.util.Date())
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + jwtExpirationHours * 60 * 60 * 1000))
            .sign(algorithm)
    }


    private fun getDefaultPermissions(): Set<Permission> {
        return setOf(
            Permission.SCREENSHOT_CREATE,
            Permission.SCREENSHOT_READ,
            Permission.SCREENSHOT_LIST
        )
    }
}
