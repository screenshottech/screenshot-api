package dev.screenshotapi.infrastructure.auth

data class MultiProviderPrincipal(
    val userId: String,
    val email: String,
    val name: String?,
    val provider: String
)