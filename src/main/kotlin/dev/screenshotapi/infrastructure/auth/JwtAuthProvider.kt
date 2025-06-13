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
            println("[JwtAuthProvider] ===== JWT VALIDATION START =====")
            println("[JwtAuthProvider] Raw JWT token: ${credential.payload}")
            println("[JwtAuthProvider] JWT subject: ${credential.payload.subject}")
            println("[JwtAuthProvider] JWT claims: ${credential.payload.claims}")
            
            // Try standard JWT subject claim first, then fallback to custom userId claim
            val userId = credential.payload.subject ?: credential.payload.getClaim("userId")?.asString()
            println("[JwtAuthProvider] Extracted userId: $userId")
            
            if (userId == null) {
                println("[JwtAuthProvider] No userId found in JWT - returning null")
                return@runBlocking null
            }

            val user = userRepository.findById(userId)
            if (user == null) {
                println("[JwtAuthProvider] User not found for userId: $userId")
                return@runBlocking null
            }
            println("[JwtAuthProvider] Found user: ${user.email}")

            // Get roles from JWT first, fallback to database
            val jwtRoles = credential.payload.getClaim("roles")?.asList(String::class.java)
            
            println("[JwtAuthProvider] JWT roles claim: $jwtRoles")
            println("[JwtAuthProvider] Database roles: ${user.roles.map { it.name }}")
            
            val userRoles = if (!jwtRoles.isNullOrEmpty()) {
                println("[JwtAuthProvider] Converting JWT roles to enum...")
                // Convert JWT roles to UserRole enum
                val convertedRoles = jwtRoles.mapNotNull { roleName ->
                    try {
                        println("[JwtAuthProvider] Converting role: $roleName")
                        val role = dev.screenshotapi.core.domain.entities.UserRole.valueOf(roleName)
                        println("[JwtAuthProvider] Successfully converted: $roleName -> $role")
                        role
                    } catch (e: Exception) {
                        println("[JwtAuthProvider] Failed to convert role: $roleName - ${e.message}")
                        null
                    }
                }.toSet()
                println("[JwtAuthProvider] Final converted roles: ${convertedRoles.map { it.name }}")
                convertedRoles
            } else {
                println("[JwtAuthProvider] No JWT roles found, using database roles")
                // Fallback to database roles
                user.roles
            }

            println("[JwtAuthProvider] User ${user.email} validated with final roles: ${userRoles.map { it.name }}")

            val principal = UserPrincipal(
                userId = user.id,
                email = user.email,
                name = user.name,
                status = user.status,
                permissions = getDefaultPermissions(),
                roles = userRoles, // Use JWT roles or database fallback
                planId = user.planId,
                creditsRemaining = user.creditsRemaining
            )
            
            println("[JwtAuthProvider] ===== JWT VALIDATION END =====")
            println("[JwtAuthProvider] Returning principal with roles: ${principal.roles.map { it.name }}")
            
            principal
        } catch (e: Exception) {
            println("[JwtAuthProvider] JWT validation failed: ${e.message}")
            println("[JwtAuthProvider] Exception details: ${e.javaClass.simpleName}")
            e.printStackTrace()
            null
        }
    }

    suspend fun createToken(userId: String): String {
        // Get user data to include roles in JWT
        val user = userRepository.findById(userId)
        val userRoles = user?.roles?.map { it.name } ?: listOf("USER")
        
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withSubject(userId) // Use standard JWT subject claim
            .withClaim("userId", userId) // Keep for backward compatibility
            .withClaim("roles", userRoles) // Add roles to JWT
            .withClaim("email", user?.email) // Add email for convenience
            .withIssuedAt(java.util.Date()) // Add issued at time
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)) // 24 hours
            .sign(algorithm)
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
