package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.*
import dev.screenshotapi.core.ports.output.HmacPort
import dev.screenshotapi.infrastructure.config.AuthConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class ScreenshotTokenServiceTest {
    
    private lateinit var screenshotTokenService: ScreenshotTokenService
    private lateinit var hmacPort: HmacPort
    private lateinit var authConfig: AuthConfig
    
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
        createdAt = Instant.fromEpochSeconds(1672531200) // 2023-01-01 00:00:00 UTC
    )
    
    @BeforeEach
    fun setup() {
        hmacPort = mockk()
        authConfig = mockk()
        
        every { authConfig.hmacTokenLength } returns 32
        every { hmacPort.generateToken(any()) } returns "generated_token_32_chars_base64url"
        every { hmacPort.generateScreenshotToken(any(), any(), any(), any()) } returns "generated_token_32_chars_base64url"
        every { hmacPort.validateToken(any(), any()) } returns true
        every { hmacPort.validateScreenshotToken(any(), any(), any(), any(), any()) } returns true
        
        screenshotTokenService = ScreenshotTokenService(hmacPort, authConfig)
    }
    
    @Test
    fun `generateSecureFilename should create correct path structure`() {
        val expectedTokenInFilename = "generated_token_32_chars_base64url"
        
        val filename = screenshotTokenService.generateSecureFilename(testJob, testJob.request)
        
        assertTrue(filename.startsWith("screenshots/2023/01/"), "Should use correct year/month path")
        assertTrue(filename.endsWith(".png"), "Should use correct file extension")
        assertTrue(filename.contains(expectedTokenInFilename), "Should contain generated token")
        
        verify {
            hmacPort.generateToken(
                "job_1672531200000_abcd1234|user_123|1672531200|https://example.com|PNG"
            )
        }
    }
    
    @Test
    fun `generateSecureFilename should handle extension override`() {
        val customExtension = "pdf"
        
        val filename = screenshotTokenService.generateSecureFilename(testJob, testJob.request, customExtension)
        
        assertTrue(filename.endsWith(".pdf"), "Should use custom extension")
        assertFalse(filename.endsWith(".png"), "Should not use default extension")
    }
    
    @Test
    fun `generateToken should delegate to hmacPort`() {
        val expectedToken = "generated_token_32_chars_base64url"
        
        val token = screenshotTokenService.generateToken(testJob)
        
        assertEquals(expectedToken, token, "Should return token from HMAC port")
        verify {
            hmacPort.generateScreenshotToken(
                testJob.id,
                testJob.userId,
                testJob.createdAt.epochSeconds,
                testJob.jobType.name
            )
        }
    }
    
    @Test
    fun `validateToken should delegate to hmacPort`() {
        val token = "test_token"
        val result = screenshotTokenService.validateToken(token, testJob)
        
        assertTrue(result)
        verify {
            hmacPort.validateScreenshotToken(
                token,
                testJob.id,
                testJob.userId,
                testJob.createdAt.epochSeconds,
                testJob.jobType.name
            )
        }
    }
    
    @Test
    fun `validateToken should return false on exception`() {
        every { hmacPort.validateScreenshotToken(any(), any(), any(), any(), any()) } throws RuntimeException("Test error")
        
        val result = screenshotTokenService.validateToken("test_token", testJob)
        
        assertFalse(result)
    }
    
    @Test
    fun `extractTokenFromFilename should extract token from valid filename`() {
        val filename = "screenshots/2023/01/abc123def456ghi789jkl012mno345pq.png"
        val expectedToken = "abc123def456ghi789jkl012mno345pq"
        
        val token = screenshotTokenService.extractTokenFromFilename(filename)
        
        assertEquals(expectedToken, token, "Should extract token from valid filename")
    }
    
    @Test
    fun `extractTokenFromFilename should return null for invalid format`() {
        val invalidFilenames = listOf(
            "invalid/path/structure.png",           // Wrong path structure
            "screenshots/2023/token.png",          // Missing month
            "screenshots/2023/01/",                // No filename
            "not-screenshots/2023/01/token.png",   // Wrong base path
            "screenshots/2023/01/short.png"       // Token too short
        )
        
        invalidFilenames.forEach { filename ->
                val token = screenshotTokenService.extractTokenFromFilename(filename)
            
                assertNull(token, "Should return null for invalid filename: $filename")
        }
    }
    
    @Test
    fun `isSecureFilename should identify secure filenames correctly`() {
        val secureFilename = "screenshots/2023/01/abc123def456ghi789jkl012mno345pq.png"
        val legacyFilename = "screenshots/2023/01/timestamp_hash_dimensions.png"
        
        // Act & Assert
        assertTrue(
            screenshotTokenService.isSecureFilename(secureFilename),
            "Should identify secure filename with token"
        )
        assertFalse(
            screenshotTokenService.isSecureFilename(legacyFilename),
            "Should identify legacy filename with timestamp pattern"
        )
    }
    
    @Test
    fun `generateLegacyFilename should create legacy format`() {
        val filename = screenshotTokenService.generateLegacyFilename(testJob.request)
        
        assertTrue(filename.matches(Regex("screenshots/\\d{4}/\\d{2}/\\d+_\\w+_1920x1080\\.png")))
    }
    
    @Test
    fun `generateLegacyFilename should handle extension override`() {
        val filename = screenshotTokenService.generateLegacyFilename(testJob.request, "pdf")
        
        assertTrue(filename.endsWith(".pdf"))
        assertTrue(filename.contains("1920x1080"))
    }
    
    @Test
    fun `generateSecureFilename should pad month correctly`() {
        val februaryJob = testJob.copy(
            createdAt = Instant.fromEpochSeconds(1675209600) // 2023-02-01
        )
        
        val filename = screenshotTokenService.generateSecureFilename(februaryJob, februaryJob.request)
        
        assertTrue(filename.contains("screenshots/2023/02/"))
    }
}