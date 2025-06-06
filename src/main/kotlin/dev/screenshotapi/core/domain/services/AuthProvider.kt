package dev.screenshotapi.core.domain.services

import dev.screenshotapi.core.domain.entities.AuthResult

interface AuthProvider {
    val providerName: String
    
    suspend fun validateToken(token: String): AuthResult?
    
    suspend fun createUserFromToken(token: String): AuthResult?
}