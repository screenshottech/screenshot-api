package dev.screenshotapi.infrastructure.auth

import dev.screenshotapi.core.domain.services.AuthProvider
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.infrastructure.auth.providers.ClerkAuthProvider
import dev.screenshotapi.infrastructure.auth.providers.LocalAuthProvider
import dev.screenshotapi.infrastructure.config.AuthConfig
import io.ktor.client.*
import org.slf4j.LoggerFactory

class AuthProviderFactory(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val httpClient: HttpClient,
    private val authConfig: AuthConfig
) {
    
    private val logger = LoggerFactory.getLogger(AuthProviderFactory::class.java)
    private val providers = mutableMapOf<String, AuthProvider>()
    
    init {
        logger.info("Starting AuthProviderFactory initialization...")
        logger.debug("JWT Secret length: ${authConfig.jwtSecret.length}")
        logger.debug("JWT Issuer: ${authConfig.jwtIssuer}")
        logger.debug("JWT Audience: ${authConfig.jwtAudience}")
        logger.info("Default Auth Provider: ${authConfig.defaultAuthProvider}")
        logger.info("Enabled Auth Providers: ${authConfig.enabledAuthProviders}")
        logger.info("Clerk Domain: ${authConfig.clerkDomain ?: "Not configured"}")
        
        try {
            initializeProviders()
            logger.info("Successfully initialized providers: ${providers.keys}")
        } catch (e: Exception) {
            logger.error("ERROR during AuthProviderFactory initialization", e)
            throw e
        }
    }
    
    fun getProvider(providerName: String): AuthProvider? {
        return providers[providerName]
    }
    
    fun getEnabledProviders(): List<AuthProvider> {
        val enabledProviderNames = authConfig.enabledAuthProviders
        return enabledProviderNames.mapNotNull { providers[it] }
    }
    
    fun getDefaultProvider(): AuthProvider? {
        return providers[authConfig.defaultAuthProvider]
    }
    
    private fun initializeProviders() {
        providers["local"] = LocalAuthProvider(
            userRepository = userRepository,
            jwtSecret = authConfig.jwtSecret,
            jwtIssuer = authConfig.jwtIssuer,
            jwtAudience = authConfig.jwtAudience,
            jwtExpirationHours = authConfig.jwtExpirationHours
        )
        
        if (authConfig.enabledAuthProviders.contains("clerk")) {
            providers["clerk"] = ClerkAuthProvider(
                userRepository = userRepository,
                planRepository = planRepository,
                httpClient = httpClient,
                clerkDomain = authConfig.clerkDomain
            )
        }
    }
}