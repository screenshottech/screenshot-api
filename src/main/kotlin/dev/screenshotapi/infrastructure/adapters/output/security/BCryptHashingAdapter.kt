package dev.screenshotapi.infrastructure.adapters.output.security

import dev.screenshotapi.core.ports.output.HashingPort
import org.mindrot.jbcrypt.BCrypt

/**
 * BCrypt implementation of HashingPort
 * Provides secure hashing and verification using BCrypt algorithm
 */
class BCryptHashingAdapter : HashingPort {
    
    override fun hashSecure(plaintext: String): String {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt())
    }
    
    override fun verifyHash(plaintext: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(plaintext, hash)
        } catch (e: Exception) {
            false
        }
    }
}