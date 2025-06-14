package dev.screenshotapi.infrastructure.auth.providers

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.screenshotapi.core.domain.entities.AuthResult
import dev.screenshotapi.core.domain.entities.User
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.services.AuthProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.util.*
import org.slf4j.LoggerFactory

@Serializable
private data class ClerkJWKSResponse(
    val keys: List<ClerkJWK>
)

@Serializable
private data class ClerkJWK(
    val kty: String,
    val use: String,
    val kid: String,
    val n: String,
    val e: String
)

class ClerkAuthProvider(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val httpClient: HttpClient,
    private val clerkDomain: String? = null
) : AuthProvider {

    private val logger = LoggerFactory.getLogger(ClerkAuthProvider::class.java)
    override val providerName: String = "clerk"

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedKeys: Map<String, RSAPublicKey> = emptyMap()
    private var keysLastFetched: Long = 0
    private val keysCacheDuration = 60 * 60 * 1000L // 1 hour

    init {
        logger.info("ClerkAuthProvider initialized with domain: ${clerkDomain ?: "default"}")
    }

    override suspend fun validateToken(token: String): AuthResult? {
        return try {
            logger.debug("Validating Clerk token...")
            val jwt = JWT.decode(token)
            logger.debug("JWT decoded - Subject: ${jwt.subject}, KeyId: ${jwt.keyId}")
            
            val kid = jwt.keyId ?: run {
                logger.error("No key ID found in JWT")
                return null
            }

            val publicKey = getPublicKey(kid) ?: run {
                logger.error("Failed to get public key for kid: $kid")
                return null
            }
            
            val algorithm = Algorithm.RSA256(publicKey, null)
            val verifier = JWT.require(algorithm).build()
            val verifiedJwt = verifier.verify(token)
            logger.info("JWT verified successfully")
            
            // Log all claims for debugging
            logger.debug("JWT Claims: ${verifiedJwt.claims.keys}")
            verifiedJwt.claims.forEach { (key, claim) ->
                logger.debug("Claim $key: ${claim.asString() ?: claim.toString()}")
            }

            val userId = verifiedJwt.subject ?: run {
                logger.error("No subject in verified JWT")
                return null
            }
            val email = verifiedJwt.getClaim("email")?.asString() ?: run {
                logger.error("No email claim in verified JWT - available claims: ${verifiedJwt.claims.keys}")
                return null
            }
            val name = verifiedJwt.getClaim("name")?.asString()

            val result = AuthResult(
                userId = userId,
                email = email,
                name = name,
                providerId = userId,
                providerName = providerName
            )
            logger.info("Auth result created for user: $email")
            result
        } catch (e: Exception) {
            logger.error("Error validating Clerk token", e)
            null
        }
    }

    override suspend fun createUserFromToken(token: String): AuthResult? {
        logger.info("Creating user from Clerk token...")
        val authResult = validateToken(token) ?: run {
            logger.error("Failed to validate token in createUserFromToken")
            return null
        }
        logger.info("Token validated, checking for existing user: ${authResult.email}")

        // Check if user already exists
        val existingUser = userRepository.findByEmail(authResult.email)
        if (existingUser != null) {
            logger.info("User already exists with ID: ${existingUser.id}")
            // Return AuthResult with internal user ID for existing users
            return authResult.copy(userId = existingUser.id)
        }

        logger.info("User not found, creating new user...")
        // Create new user with free plan
        val freePlan = planRepository.findById("plan_free") ?: run {
            logger.error("Free plan not found in database!")
            return null
        }
        logger.info("Found free plan: ${freePlan.name}")

        val newUser = User(
            id = generateUserId(),
            email = authResult.email,
            name = authResult.name,
            passwordHash = null, // External auth, no password
            planId = freePlan.id,
            planName = freePlan.name,
            creditsRemaining = freePlan.creditsPerMonth,
            authProvider = providerName,
            externalId = authResult.providerId,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        logger.info("Saving new user: ${newUser.email}")
        val savedUser = userRepository.save(newUser)
        logger.info("User created successfully with ID: ${savedUser.id}")
        
        // Return AuthResult with internal user ID for new users
        return authResult.copy(userId = savedUser.id)
    }

    private suspend fun getPublicKey(kid: String): RSAPublicKey? {
        val now = System.currentTimeMillis()

        // Refresh keys if cache is expired or key not found
        if (now - keysLastFetched > keysCacheDuration || !cachedKeys.containsKey(kid)) {
            refreshKeys()
        }

        return cachedKeys[kid]
    }

    private suspend fun refreshKeys() {
        try {
            val jwksUrl = if (clerkDomain != null) {
                "https://$clerkDomain/.well-known/jwks.json"
            } else {
                "https://clerk.dev/.well-known/jwks.json"
            }
            
            logger.info("Fetching JWKS from: $jwksUrl")
            val response: ClerkJWKSResponse = httpClient.get(jwksUrl).body()
            logger.info("JWKS response received with ${response.keys.size} keys")

            val keys = mutableMapOf<String, RSAPublicKey>()
            for (jwk in response.keys) {
                if (jwk.kty == "RSA" && jwk.use == "sig") {
                    val publicKey = buildRSAPublicKey(jwk.n, jwk.e)
                    if (publicKey != null) {
                        keys[jwk.kid] = publicKey
                        logger.debug("Added public key with kid: ${jwk.kid}")
                    }
                }
            }

            cachedKeys = keys
            keysLastFetched = System.currentTimeMillis()
            logger.info("JWKS cache refreshed with ${keys.size} keys")
        } catch (e: Exception) {
            logger.error("Failed to refresh Clerk JWKS", e)
            throw e // Re-throw to propagate the error
        }
    }

    private fun buildRSAPublicKey(nStr: String, eStr: String): RSAPublicKey? {
        return try {
            val n = Base64.getUrlDecoder().decode(nStr)
            val e = Base64.getUrlDecoder().decode(eStr)

            val nBigInt = java.math.BigInteger(1, n)
            val eBigInt = java.math.BigInteger(1, e)

            val spec = java.security.spec.RSAPublicKeySpec(nBigInt, eBigInt)
            val factory = KeyFactory.getInstance("RSA")
            factory.generatePublic(spec) as RSAPublicKey
        } catch (e: Exception) {
            null
        }
    }

    private fun generateUserId(): String {
        return "usr_${UUID.randomUUID().toString().replace("-", "")}"
    }
}
