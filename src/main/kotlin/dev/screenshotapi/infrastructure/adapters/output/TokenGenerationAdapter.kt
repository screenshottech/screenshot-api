package dev.screenshotapi.infrastructure.adapters.output

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.ports.output.TokenGenerationPort
import dev.screenshotapi.infrastructure.services.ScreenshotTokenService
import org.slf4j.LoggerFactory

class TokenGenerationAdapter(
    private val screenshotTokenService: ScreenshotTokenService
) : TokenGenerationPort {
    
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    override fun generateToken(job: ScreenshotJob): String {
        return screenshotTokenService.generateToken(job)
    }
    
    override fun validateToken(token: String, job: ScreenshotJob): Boolean {
        return screenshotTokenService.validateToken(token, job)
    }
    
    override fun generateSecureFilename(
        job: ScreenshotJob, 
        request: ScreenshotRequest,
        extension: String?
    ): String {
        return screenshotTokenService.generateSecureFilename(job, request, extension)
    }
    
    override fun extractTokenFromFilename(filename: String): String? {
        return screenshotTokenService.extractTokenFromFilename(filename)
    }
    
    override fun isSecureFilename(filename: String): Boolean {
        return screenshotTokenService.isSecureFilename(filename)
    }
    
    override fun validateTokenDetailed(
        token: String, 
        job: ScreenshotJob,
        requireStrictValidation: Boolean
    ): TokenGenerationPort.TokenValidationResult {
        return try {
            val isValid = screenshotTokenService.validateToken(token, job)
            
            if (!isValid) {
                return TokenGenerationPort.TokenValidationResult(
                    isValid = false,
                    errorMessage = "Invalid token"
                )
            }
            
            TokenGenerationPort.TokenValidationResult(
                isValid = true,
                errorMessage = null
            )
            
        } catch (e: Exception) {
            TokenGenerationPort.TokenValidationResult(
                isValid = false,
                errorMessage = "Validation error: ${e.message}"
            )
        }
    }
}