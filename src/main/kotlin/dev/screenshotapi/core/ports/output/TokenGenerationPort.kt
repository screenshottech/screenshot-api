package dev.screenshotapi.core.ports.output

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotRequest

interface TokenGenerationPort {
    
    fun generateToken(job: ScreenshotJob): String
    
    fun validateToken(token: String, job: ScreenshotJob): Boolean
    
    fun generateSecureFilename(
        job: ScreenshotJob, 
        request: ScreenshotRequest,
        extension: String? = null
    ): String
    
    fun extractTokenFromFilename(filename: String): String?
    
    fun isSecureFilename(filename: String): Boolean
    
    data class TokenValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
    
    fun validateTokenDetailed(
        token: String, 
        job: ScreenshotJob,
        requireStrictValidation: Boolean = false
    ): TokenValidationResult
}