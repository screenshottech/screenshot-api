package dev.screenshotapi.core.ports.output

/**
 * Port for secure hashing operations
 * This allows the domain to hash and verify sensitive data without coupling to specific implementations
 */
interface HashingPort {
    /**
     * Hash a plaintext value securely for storage (uses BCrypt - non-deterministic)
     * @param plaintext The value to hash
     * @return The secure hash
     */
    fun hashSecure(plaintext: String): String
    
    /**
     * Create a deterministic hash for lookups (uses SHA-256 - deterministic)
     * @param plaintext The value to hash
     * @return The deterministic hash for database lookups
     */
    fun hashForLookup(plaintext: String): String
    
    /**
     * Verify if a plaintext value matches a hash
     * @param plaintext The plaintext to verify
     * @param hash The hash to verify against
     * @return true if the plaintext matches the hash
     */
    fun verifyHash(plaintext: String, hash: String): Boolean
}