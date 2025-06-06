package dev.screenshotapi.infrastructure.auth

import dev.screenshotapi.core.domain.services.AuthProvider
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.infrastructure.auth.providers.ClerkAuthProvider
import dev.screenshotapi.infrastructure.auth.providers.LocalAuthProvider
import dev.screenshotapi.infrastructure.config.AuthConfig
import io.ktor.client.*

class AuthProviderFactory(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val httpClient: HttpClient,
    private val authConfig: AuthConfig
) {
    
    private val providers = mutableMapOf<String, AuthProvider>()
    
    init {
        initializeProviders()
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
        // Always initialize local provider
        providers["local"] = LocalAuthProvider(
            userRepository = userRepository,
            jwtSecret = authConfig.jwtSecret,
            jwtIssuer = authConfig.jwtIssuer,
            jwtAudience = authConfig.jwtAudience
        )
        
        // Initialize Clerk provider if configured
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