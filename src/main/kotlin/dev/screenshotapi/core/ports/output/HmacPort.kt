package dev.screenshotapi.core.ports.output

interface HmacPort {
    fun generateToken(input: String): String
    
    fun validateToken(token: String, expectedInput: String): Boolean
    
    fun generateScreenshotToken(
        jobId: String,
        userId: String,
        createdAtEpochSeconds: Long,
        jobType: String
    ): String
    
    fun validateScreenshotToken(
        token: String,
        jobId: String,
        userId: String,
        createdAtEpochSeconds: Long,
        jobType: String
    ): Boolean
}