package dev.screenshotapi.core.domain.entities

data class AuthResult(
    val userId: String,
    val email: String,
    val name: String?,
    val providerId: String,
    val providerName: String,
    val isValid: Boolean = true
)