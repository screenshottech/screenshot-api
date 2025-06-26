package dev.screenshotapi.infrastructure.adapters.output.security

import dev.screenshotapi.core.ports.output.HmacPort
import dev.screenshotapi.infrastructure.config.AuthConfig
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HmacAdapter(private val authConfig: AuthConfig) : HmacPort {
    
    private val algorithm = "HmacSHA256"
    private val hmacSecret: String by lazy { 
        loadHmacSecret() 
    }
    
    override fun generateToken(input: String): String {
        return try {
            val secretKeySpec = SecretKeySpec(hmacSecret.toByteArray(), algorithm)
            val mac = Mac.getInstance(algorithm)
            mac.init(secretKeySpec)
            
            val hmacBytes = mac.doFinal(input.toByteArray())
            
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmacBytes)
                .take(32)
                
        } catch (e: Exception) {
            throw IllegalStateException("Failed to generate HMAC token", e)
        }
    }
    
    override fun validateToken(token: String, expectedInput: String): Boolean {
        return try {
            val expectedToken = generateToken(expectedInput)
            constantTimeEquals(token, expectedToken)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun generateScreenshotToken(
        jobId: String,
        userId: String, 
        createdAtEpochSeconds: Long,
        jobType: String
    ): String {
        val input = "$jobId|$userId|$createdAtEpochSeconds|$jobType"
        return generateToken(input)
    }
    
    override fun validateScreenshotToken(
        token: String,
        jobId: String,
        userId: String,
        createdAtEpochSeconds: Long,
        jobType: String
    ): Boolean {
        val input = "$jobId|$userId|$createdAtEpochSeconds|$jobType"
        return validateToken(token, input)
    }
    
    private fun loadHmacSecret(): String {
        val hmacSecret = System.getenv("HMAC_SECRET")
        
        return when {
            !hmacSecret.isNullOrBlank() -> hmacSecret
            else -> authConfig.jwtSecret
        }
    }
    
    // Constant-time comparison to prevent timing attacks
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}