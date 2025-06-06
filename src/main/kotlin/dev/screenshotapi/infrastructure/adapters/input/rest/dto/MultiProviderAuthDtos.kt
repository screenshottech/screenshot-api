package dev.screenshotapi.infrastructure.adapters.input.rest.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthProviderLoginRequestDto(
    val token: String
)

@Serializable
data class AuthProviderLoginResponseDto(
    val success: Boolean,
    val message: String,
    val userId: String? = null,
    val email: String? = null,
    val name: String? = null,
    val jwt: String? = null
)

@Serializable
data class AuthProvidersListResponseDto(
    val enabledProviders: List<String>,
    val defaultProvider: String
)