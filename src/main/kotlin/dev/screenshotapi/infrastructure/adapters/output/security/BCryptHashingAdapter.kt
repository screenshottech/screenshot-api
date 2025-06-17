package dev.screenshotapi.infrastructure.adapters.output.security

import dev.screenshotapi.core.ports.output.HashingPort
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest

/**
 * BCrypt implementation of HashingPort
 * Provides secure hashing and verification using BCrypt algorithm
 * Also provides SHA-256 for deterministic lookups
 */
class BCryptHashingAdapter : HashingPort {
    
    override fun hashSecure(plaintext: String): String {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt())
    }
    
    override fun hashForLookup(plaintext: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(plaintext.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            throw IllegalStateException("SHA-256 algorithm not available", e)
        }
    }
    
    override fun verifyHash(plaintext: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(plaintext, hash)
        } catch (e: Exception) {
            false
        }
    }
}