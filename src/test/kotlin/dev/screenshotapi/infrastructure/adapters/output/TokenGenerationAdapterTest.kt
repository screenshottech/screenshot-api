package dev.screenshotapi.infrastructure.adapters.output

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.ports.output.TokenGenerationPort
import dev.screenshotapi.infrastructure.services.ScreenshotTokenService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class TokenGenerationAdapterTest {
    
    private lateinit var tokenGenerationAdapter: TokenGenerationAdapter
    private lateinit var screenshotTokenService: ScreenshotTokenService
    
    private val testJob = ScreenshotJob(
        id = "job_1672531200000_abcd1234",
        userId = "user_123",
        apiKeyId = "key_456",
        request = ScreenshotRequest(
            url = "https://example.com",
            width = 1920,
            height = 1080,
            format = ScreenshotFormat.PNG,
            quality = 80
        ),
        status = ScreenshotStatus.QUEUED,
        jobType = JobType.SCREENSHOT,
        createdAt = Instant.fromEpochSeconds(1672531200)
    )
    
    @BeforeEach
    fun setup() {
        screenshotTokenService = mockk()
        tokenGenerationAdapter = TokenGenerationAdapter(screenshotTokenService)
    }
    
    @Test
    fun `generateToken should delegate to ScreenshotTokenService`() {
        // Arrange
        val expectedToken = "test_token_32_chars"
        every { screenshotTokenService.generateToken(testJob) } returns expectedToken
        
        // Act
        val result = tokenGenerationAdapter.generateToken(testJob)
        
        // Assert
        assertEquals(expectedToken, result, "Should return token from service")
        verify { screenshotTokenService.generateToken(testJob) }
    }
    
    @Test
    fun `validateToken should delegate to ScreenshotTokenService`() {
        // Arrange
        val token = "test_token"
        every { screenshotTokenService.validateToken(token, testJob) } returns true
        
        // Act
        val result = tokenGenerationAdapter.validateToken(token, testJob)
        
        // Assert
        assertTrue(result, "Should return validation result from service")
        verify { screenshotTokenService.validateToken(token, testJob) }
    }
    
    @Test
    fun `generateSecureFilename should delegate with correct parameters`() {
        val expectedFilename = "screenshots/2023/01/secure_token_abc123.png"
        every { 
            screenshotTokenService.generateSecureFilename(testJob, testJob.request, null) 
        } returns expectedFilename
        
        val result = tokenGenerationAdapter.generateSecureFilename(testJob, testJob.request)
        
        assertEquals(expectedFilename, result)
        verify { screenshotTokenService.generateSecureFilename(testJob, testJob.request, null) }
    }
    
    @Test
    fun `generateSecureFilename should handle extension override`() {
        val expectedFilename = "screenshots/2023/01/secure_token_abc123.pdf"
        every { 
            screenshotTokenService.generateSecureFilename(testJob, testJob.request, "pdf") 
        } returns expectedFilename
        
        val result = tokenGenerationAdapter.generateSecureFilename(testJob, testJob.request, "pdf")
        
        assertEquals(expectedFilename, result)
        verify { screenshotTokenService.generateSecureFilename(testJob, testJob.request, "pdf") }
    }
    
    @Test
    fun `extractTokenFromFilename should delegate to service`() {
        val filename = "screenshots/2023/01/token123.png"
        every { screenshotTokenService.extractTokenFromFilename(filename) } returns "token123"
        
        val result = tokenGenerationAdapter.extractTokenFromFilename(filename)
        
        assertEquals("token123", result)
        verify { screenshotTokenService.extractTokenFromFilename(filename) }
    }
    
    @Test
    fun `isSecureFilename should delegate to service`() {
        val filename = "screenshots/2023/01/secure_token.png"
        every { screenshotTokenService.isSecureFilename(filename) } returns true
        
        val result = tokenGenerationAdapter.isSecureFilename(filename)
        
        assertTrue(result)
        verify { screenshotTokenService.isSecureFilename(filename) }
    }
    
    @Test
    fun `validateTokenDetailed should return success for valid token (no expiration check)`() {
        val token = "valid_token"
        every { screenshotTokenService.validateToken(token, testJob) } returns true
        
        val result = tokenGenerationAdapter.validateTokenDetailed(token, testJob)
        
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
        verify { screenshotTokenService.validateToken(token, testJob) }
        // Note: Tokens don't expire as they are part of permanent filenames
    }
    
    @Test
    fun `validateTokenDetailed should return failure for invalid token`() {
        val token = "invalid_token"
        every { screenshotTokenService.validateToken(token, testJob) } returns false
        
        val result = tokenGenerationAdapter.validateTokenDetailed(token, testJob)
        
        assertFalse(result.isValid)
        assertEquals("Invalid token", result.errorMessage)
        verify { screenshotTokenService.validateToken(token, testJob) }
    }
    
    @Test
    fun `validateTokenDetailed should handle exceptions gracefully`() {
        val token = "error_token"
        every { screenshotTokenService.validateToken(token, testJob) } throws RuntimeException("Test error")
        
        val result = tokenGenerationAdapter.validateTokenDetailed(token, testJob)
        
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("Validation error") == true)
        verify { screenshotTokenService.validateToken(token, testJob) }
    }
    
    @Test
    fun `validateTokenDetailed should work with strict validation`() {
        val token = "valid_token"
        every { screenshotTokenService.validateToken(token, testJob) } returns true
        
        val result = tokenGenerationAdapter.validateTokenDetailed(token, testJob, requireStrictValidation = true)
        
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
        verify { screenshotTokenService.validateToken(token, testJob) }
    }
}