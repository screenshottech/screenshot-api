package dev.screenshotapi.infrastructure.auth

import io.ktor.server.auth.*

data class ApiKeyPrincipal(
    val keyId: String,
    val userId: String,
    val name: String
) : Principal
