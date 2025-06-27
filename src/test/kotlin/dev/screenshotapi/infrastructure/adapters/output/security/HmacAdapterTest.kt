package dev.screenshotapi.infrastructure.adapters.output.security

import dev.screenshotapi.infrastructure.config.AuthConfig
import dev.screenshotapi.infrastructure.config.Environment
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class HmacAdapterTest {
    
    private lateinit var hmacAdapter: HmacAdapter
    private lateinit var authConfig: AuthConfig
    
    @BeforeEach
    fun setup() {
        authConfig = mockk<AuthConfig>()
        every { authConfig.jwtSecret } returns "test-secret-key-for-hmac-testing-32-chars-long"
        every { authConfig.hmacTokenLength } returns 32
        
        hmacAdapter = HmacAdapter(authConfig)
    }
    
    @Test
    fun `generateToken should create deterministic tokens`() {
        val input = "test-input-data"
        
        val token1 = hmacAdapter.generateToken(input)
        val token2 = hmacAdapter.generateToken(input)
        
        assertEquals(token1, token2, "Same input should produce same token (deterministic)")
        assertEquals(32, token1.length, "Token should be 32 characters")
        assertTrue(token1.matches(Regex("^[A-Za-z0-9_-]+$")), "Token should be Base64URL safe")
    }
    
    @Test
    fun `validateToken should correctly validate tokens`() {
        val input = "test-input-data"
        val validToken = hmacAdapter.generateToken(input)
        val invalidToken = "invalid-token"
        val differentInput = "different-input"
        
        assertTrue(hmacAdapter.validateToken(validToken, input), "Valid token should pass validation")
        assertFalse(hmacAdapter.validateToken(invalidToken, input), "Invalid token should fail validation")
        assertFalse(hmacAdapter.validateToken(validToken, differentInput), "Valid token with wrong input should fail")
    }
    
    @Test
    fun `generateScreenshotToken should create consistent tokens`() {
        val jobId = "job_123456789_abcd1234"
        val userId = "user_123"
        val createdAt = 1672531200L // January 1, 2023 00:00:00 UTC
        val jobType = "SCREENSHOT"
        
        val token1 = hmacAdapter.generateScreenshotToken(jobId, userId, createdAt, jobType)
        val token2 = hmacAdapter.generateScreenshotToken(jobId, userId, createdAt, jobType)
        
        assertEquals(token1, token2, "Same parameters should produce same token")
        assertEquals(32, token1.length, "Token should be 32 characters")
    }
    
    @Test
    fun `validateScreenshotToken should validate screenshot-specific tokens`() {
        val jobId = "job_123456789_abcd1234"
        val userId = "user_123"
        val createdAt = 1672531200L
        val jobType = "SCREENSHOT"
        val token = hmacAdapter.generateScreenshotToken(jobId, userId, createdAt, jobType)
        
        assertTrue(
            hmacAdapter.validateScreenshotToken(token, jobId, userId, createdAt, jobType),
            "Valid parameters should pass validation"
        )
        
        assertFalse(
            hmacAdapter.validateScreenshotToken(token, "different_job", userId, createdAt, jobType),
            "Different job ID should fail validation"
        )
        assertFalse(
            hmacAdapter.validateScreenshotToken(token, jobId, "different_user", createdAt, jobType),
            "Different user ID should fail validation"
        )
        assertFalse(
            hmacAdapter.validateScreenshotToken(token, jobId, userId, 9999999999L, jobType),
            "Different creation time should fail validation"
        )
        assertFalse(
            hmacAdapter.validateScreenshotToken(token, jobId, userId, createdAt, "OCR"),
            "Different job type should fail validation"
        )
    }
    
    @Test
    fun `different inputs should produce different tokens`() {
        val input1 = "input1"
        val input2 = "input2"
        
        val token1 = hmacAdapter.generateToken(input1)
        val token2 = hmacAdapter.generateToken(input2)
        
        assertNotEquals(token1, token2, "Different inputs should produce different tokens")
    }
    
    @Test
    fun `constantTimeEquals should prevent timing attacks`() {
        val testInput = "test"
        val validToken = hmacAdapter.generateToken(testInput)
        val invalidToken = "a".repeat(32) // Same length, different content
        val shortToken = "short"
        
        assertFalse(
            hmacAdapter.validateToken(shortToken, testInput),
            "Different length should fail immediately"
        )
        assertFalse(
            hmacAdapter.validateToken(invalidToken, testInput),
            "Same length but different content should fail securely"
        )
        assertTrue(
            hmacAdapter.validateToken(validToken, testInput),
            "Valid token should pass validation"
        )
    }
    
    @Test
    fun `tokens should be URL-safe Base64`() {
        val input = "test-input-with-special-chars-!@#$%^&*()"
        
        val token = hmacAdapter.generateToken(input)
        
        assertTrue(
            token.matches(Regex("^[A-Za-z0-9_-]+$")),
            "Token should only contain URL-safe characters"
        )
        assertFalse(token.contains("+"), "Token should not contain '+' character")
        assertFalse(token.contains("/"), "Token should not contain '/' character")
        assertFalse(token.contains("="), "Token should not contain '=' padding")
    }
}