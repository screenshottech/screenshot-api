package dev.screenshotapi.core.usecases.screenshot

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.repositories.ScreenshotRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.ports.output.TokenGenerationPort
import org.slf4j.LoggerFactory

/**
 * Use case for validating HMAC-based screenshot tokens
 * Provides secure access validation without exposing internal job details
 */
class ValidateScreenshotTokenUseCase(
    private val screenshotRepository: ScreenshotRepository,
    private val tokenGenerationPort: TokenGenerationPort
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    suspend operator fun invoke(request: Request): Response {
        logger.debug(
            "Validating screenshot token: token={}, extractedFromFilename={}", 
            request.token.take(8) + "...", 
            request.extractedFromFilename
        )
        
        try {
            // Step 1: Find job by token (we'll need to implement this search method)
            val job = findJobByToken(request.token)
                ?: return Response(
                    isValid = false,
                    errorMessage = "Invalid or expired token",
                    job = null
                )
            
            // Step 2: Validate token against job parameters
            val validationResult = tokenGenerationPort.validateTokenDetailed(
                request.token, 
                job, 
                requireStrictValidation = request.requireUserMatch
            )
            
            if (!validationResult.isValid) {
                logger.warn(
                    "HMAC token validation failed for job {}: token={}, error={}", 
                    job.id, 
                    request.token.take(8) + "...",
                    validationResult.errorMessage
                )
                return Response(
                    isValid = false,
                    errorMessage = validationResult.errorMessage ?: "Token validation failed",
                    job = null
                )
            }
            
            // Step 3: Optional user access validation
            if (request.requireUserMatch && request.userId != null) {
                if (job.userId != request.userId) {
                    logger.warn(
                        "User mismatch for token validation: jobUser={}, requestUser={}, token={}", 
                        job.userId, 
                        request.userId, 
                        request.token.take(8) + "..."
                    )
                    return Response(
                        isValid = false,
                        errorMessage = "Access denied: user mismatch",
                        job = null
                    )
                }
            }
            
            logger.debug(
                "Screenshot token validation successful: jobId={}, userId={}, token={}", 
                job.id, 
                job.userId, 
                request.token.take(8) + "..."
            )
            
            return Response(
                isValid = true,
                errorMessage = null,
                job = job
            )
            
        } catch (e: Exception) {
            logger.error("Error during token validation: token=${request.token.take(8)}...", e)
            return Response(
                isValid = false,
                errorMessage = "Validation error",
                job = null
            )
        }
    }
    
    /**
     * Find a screenshot job by validating the token against all possible jobs
     * This is a fallback approach until we can implement token-based lookup
     * 
     * TODO: For better performance, consider adding a token index to the database
     * or implementing a token-to-jobId mapping service
     */
    private suspend fun findJobByToken(token: String): ScreenshotJob? {
        // For now, we'll extract potential job info from the token
        // In a more advanced implementation, we could maintain a token index
        
        // This is a simplified approach - in production you might want to:
        // 1. Add a token field to the database with an index
        // 2. Implement a Redis-based token-to-jobId mapping
        // 3. Use the token to derive the job ID more efficiently
        
        // For this implementation, we'll need to search by pattern or 
        // implement a more efficient lookup mechanism
        
        return null // This will be implemented when we have repository support
    }
    
    data class Request(
        val token: String,
        val userId: String? = null,
        val requireUserMatch: Boolean = false,
        val extractedFromFilename: Boolean = false
    )
    
    data class Response(
        val isValid: Boolean,
        val errorMessage: String?,
        val job: ScreenshotJob?
    )
}