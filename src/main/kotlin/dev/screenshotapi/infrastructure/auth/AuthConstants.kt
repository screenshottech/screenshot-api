package dev.screenshotapi.infrastructure.auth

/**
 * Authentication provider names used throughout the application
 */
object AuthProviders {
    const val JWT_AUTH = "jwt-auth"
    const val API_KEY_AUTH = "api-key-auth"
    const val API_KEY_LEGACY = "api-key"
    const val X_API_KEY = "x-api-key-header"
    const val MULTI_PROVIDER = "multi-provider"
}

/**
 * Common authentication combinations for routes
 */
object AuthCombinations {
    // User management - JWT only
    val USER_MANAGEMENT = arrayOf(AuthProviders.JWT_AUTH)

    // Operations - JWT OR API Key OR X-API-Key
    val OPERATIONS = arrayOf(AuthProviders.JWT_AUTH, AuthProviders.API_KEY_LEGACY, AuthProviders.X_API_KEY)
}
