package dev.screenshotapi.infrastructure.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class AuthConfig(
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtExpirationHours: Int,
    val jwtRefreshExpirationDays: Int,
    val apiKeyLength: Int,
    val apiKeyPrefix: String,
    val passwordMinLength: Int,
    val passwordRequireUppercase: Boolean,
    val passwordRequireNumbers: Boolean,
    val passwordRequireSpecialChars: Boolean,
    val maxLoginAttempts: Int,
    val lockoutDurationMinutes: Int,
    val sessionTimeoutMinutes: Int,
    val defaultAuthProvider: String,
    val enabledAuthProviders: List<String>,
    val clerkDomain: String?,
    val hmacTokenLength: Int
) {
    companion object {
        fun load(): AuthConfig {
            val environment = Environment.current()
            
            return AuthConfig(
                jwtSecret = loadJwtSecret(environment),
            jwtIssuer = System.getenv("JWT_ISSUER")
                ?: "screenshotapi-api",
            jwtAudience = System.getenv("JWT_AUDIENCE")
                ?: "screenshotapi-api-users",
            jwtExpirationHours = System.getenv("JWT_EXPIRATION_HOURS")?.toInt()
                ?: 24,
            jwtRefreshExpirationDays = System.getenv("JWT_REFRESH_EXPIRATION_DAYS")?.toInt()
                ?: 30,
            apiKeyLength = System.getenv("API_KEY_LENGTH")?.toInt()
                ?: 32,
            apiKeyPrefix = System.getenv("API_KEY_PREFIX")
                ?: "sk_",
            passwordMinLength = System.getenv("PASSWORD_MIN_LENGTH")?.toInt()
                ?: 8,
            passwordRequireUppercase = System.getenv("PASSWORD_REQUIRE_UPPERCASE")?.toBoolean()
                ?: true,
            passwordRequireNumbers = System.getenv("PASSWORD_REQUIRE_NUMBERS")?.toBoolean()
                ?: true,
            passwordRequireSpecialChars = System.getenv("PASSWORD_REQUIRE_SPECIAL_CHARS")?.toBoolean()
                ?: true,
            maxLoginAttempts = System.getenv("MAX_LOGIN_ATTEMPTS")?.toInt()
                ?: 5,
            lockoutDurationMinutes = System.getenv("LOCKOUT_DURATION_MINUTES")?.toInt()
                ?: 15,
            sessionTimeoutMinutes = System.getenv("SESSION_TIMEOUT_MINUTES")?.toInt()
                ?: 60,
            defaultAuthProvider = System.getenv("DEFAULT_AUTH_PROVIDER")
                ?: "local",
            enabledAuthProviders = System.getenv("ENABLED_AUTH_PROVIDERS")?.split(",")?.map { it.trim() }
                ?: listOf("local"),
            clerkDomain = System.getenv("CLERK_DOMAIN"),
            hmacTokenLength = System.getenv("HMAC_TOKEN_LENGTH")?.toInt()
                ?: 32
            )
        }
        
        /**
         * Load JWT secret with environment-specific validation
         * Requires JWT_SECRET environment variable in production for security
         * 
         * For production deployment, generate a secure secret using:
         * openssl rand -base64 32
         * or
         * head -c 32 /dev/urandom | base64
         */
        private fun loadJwtSecret(environment: Environment): String {
            val jwtSecret = System.getenv("JWT_SECRET")
            
            return when {
                // Production: JWT_SECRET is mandatory with strict validation
                environment.isProduction -> {
                    if (jwtSecret.isNullOrBlank()) {
                        throw IllegalStateException(
                            "JWT_SECRET environment variable is required in production. " +
                            "Generate a strong secret with: openssl rand -base64 32"
                        )
                    }
                    
                    // Validate secret strength in production
                    if (jwtSecret.length < 32) {
                        throw IllegalStateException(
                            "JWT_SECRET must be at least 32 characters long in production. " +
                            "Current length: ${jwtSecret.length}. " +
                            "Generate a new one with: openssl rand -base64 32"
                        )
                    }
                    
                    // Additional entropy validation
                    if (jwtSecret.matches(Regex("^[a-zA-Z0-9]{32}$"))) {
                        throw IllegalStateException(
                            "JWT_SECRET appears to be low entropy. " +
                            "Use a cryptographically secure generator: openssl rand -base64 32"
                        )
                    }
                    
                    jwtSecret
                }
                
                // Development/Staging: Use provided secret or safe default
                else -> {
                    jwtSecret ?: "development-jwt-secret-key-for-local-testing-only-change-in-production-32chars"
                }
            }
        }
    }

    // Computed properties
    val jwtExpiration: Duration get() = jwtExpirationHours.hours
    val jwtRefreshExpiration: Duration get() = (jwtRefreshExpirationDays * 24).hours
    val lockoutDuration: Duration get() = lockoutDurationMinutes.minutes
    val sessionTimeout: Duration get() = sessionTimeoutMinutes.minutes

    // Validation methods
    fun isValidPassword(password: String): Boolean {
        if (password.length < passwordMinLength) return false
        if (passwordRequireUppercase && !password.any { it.isUpperCase() }) return false
        if (passwordRequireNumbers && !password.any { it.isDigit() }) return false
        if (passwordRequireSpecialChars && !password.any { !it.isLetterOrDigit() }) return false
        return true
    }

    fun generateApiKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val randomString = (1..apiKeyLength)
            .map { chars.random() }
            .joinToString("")
        return "$apiKeyPrefix$randomString"
    }

    fun isValidApiKeyFormat(key: String): Boolean {
        return key.startsWith(apiKeyPrefix) &&
                key.length == apiKeyPrefix.length + apiKeyLength &&
                key.substring(apiKeyPrefix.length).all { it.isLetterOrDigit() }
    }
}

