package dev.screenshotapi.infrastructure.auth.providers

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import dev.screenshotapi.core.domain.entities.AuthResult
import dev.screenshotapi.core.domain.services.AuthProvider
import dev.screenshotapi.core.domain.repositories.UserRepository
import org.slf4j.LoggerFactory

class LocalAuthProvider(
    private val userRepository: UserRepository,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String
) : AuthProvider {
    
    private val logger = LoggerFactory.getLogger(LocalAuthProvider::class.java)
    override val providerName: String = "local"
    
    init {
        logger.info("Initializing LocalAuthProvider")
        logger.debug("JWT Secret length: ${jwtSecret.length}")
        logger.debug("JWT Issuer: $jwtIssuer")
        logger.debug("JWT Audience: $jwtAudience")
    }
    
    private val algorithm = try {
        logger.debug("Creating HMAC256 algorithm...")
        Algorithm.HMAC256(jwtSecret).also {
            logger.info("HMAC256 algorithm created successfully")
        }
    } catch (e: Exception) {
        logger.error("Failed to create HMAC256 algorithm", e)
        throw e
    }
    
    private val verifier: JWTVerifier = try {
        JWT.require(algorithm)
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .build().also {
                logger.info("JWT verifier created successfully")
            }
    } catch (e: Exception) {
        logger.error("Failed to create JWT verifier", e)
        throw e
    }
    
    override suspend fun validateToken(token: String): AuthResult? {
        return try {
            val jwt = verifier.verify(token)
            // Try standard JWT subject claim first, then fallback to custom userId claim
            val userId = jwt.subject ?: jwt.getClaim("userId")?.asString() ?: return null
            
            val user = userRepository.findById(userId) ?: return null
            
            AuthResult(
                userId = user.id,
                email = user.email,
                name = user.name,
                providerId = user.id,
                providerName = providerName
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun createUserFromToken(token: String): AuthResult? {
        // For local provider, we don't create users from tokens
        // Users are created through registration flow
        return validateToken(token)
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
}