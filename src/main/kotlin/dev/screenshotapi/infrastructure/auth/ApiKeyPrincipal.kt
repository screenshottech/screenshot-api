package dev.screenshotapi.infrastructure.auth

data class ApiKeyPrincipal(
    val keyId: String,
    val userId: String,
    val name: String
)
