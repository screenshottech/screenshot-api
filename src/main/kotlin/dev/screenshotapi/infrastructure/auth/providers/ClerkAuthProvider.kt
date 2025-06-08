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

    override val providerName: String = "clerk"

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedKeys: Map<String, RSAPublicKey> = emptyMap()
    private var keysLastFetched: Long = 0
    private val keysCacheDuration = 60 * 60 * 1000L // 1 hour

    override suspend fun validateToken(token: String): AuthResult? {
        return try {
            val jwt = JWT.decode(token)
            val kid = jwt.keyId ?: return null

            val publicKey = getPublicKey(kid) ?: return null
            val algorithm = Algorithm.RSA256(publicKey, null)

            val verifier = JWT.require(algorithm).build()
            val verifiedJwt = verifier.verify(token)

            val userId = verifiedJwt.subject ?: return null
            val email = verifiedJwt.getClaim("email")?.asString() ?: return null
            val name = verifiedJwt.getClaim("name")?.asString()

            AuthResult(
                userId = userId,
                email = email,
                name = name,
                providerId = userId,
                providerName = providerName
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun createUserFromToken(token: String): AuthResult? {
        val authResult = validateToken(token) ?: return null

        // Check if user already exists
        val existingUser = userRepository.findByEmail(authResult.email)
        if (existingUser != null) {
            // Return AuthResult with internal user ID for existing users
            return authResult.copy(userId = existingUser.id)
        }

        // Create new user with free plan
        val freePlan = planRepository.findById("plan_free") ?: return null

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

        val savedUser = userRepository.save(newUser)
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

            val response: ClerkJWKSResponse = httpClient.get(jwksUrl).body()

            val keys = mutableMapOf<String, RSAPublicKey>()
            for (jwk in response.keys) {
                if (jwk.kty == "RSA" && jwk.use == "sig") {
                    val publicKey = buildRSAPublicKey(jwk.n, jwk.e)
                    if (publicKey != null) {
                        keys[jwk.kid] = publicKey
                    }
                }
            }

            cachedKeys = keys
            keysLastFetched = System.currentTimeMillis()
        } catch (e: Exception) {
            // Log error in production
            println("Failed to refresh Clerk JWKS: ${e.message}")
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
