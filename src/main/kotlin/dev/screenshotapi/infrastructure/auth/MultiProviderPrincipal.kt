package dev.screenshotapi.infrastructure.auth

import io.ktor.server.auth.*

data class MultiProviderPrincipal(
    val userId: String,
    val email: String,
    val name: String?,
    val provider: String
) : Principal