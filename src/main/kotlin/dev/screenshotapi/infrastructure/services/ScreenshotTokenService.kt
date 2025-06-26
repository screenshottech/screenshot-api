package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.ScreenshotJob
import dev.screenshotapi.core.domain.entities.ScreenshotRequest
import dev.screenshotapi.core.ports.output.HmacPort
import dev.screenshotapi.infrastructure.config.AuthConfig
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

class ScreenshotTokenService(
    private val hmacPort: HmacPort,
    private val authConfig: AuthConfig
) {
    
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    fun generateSecureFilename(
        job: ScreenshotJob, 
        request: ScreenshotRequest,
        extension: String? = null
    ): String {
        return generateSecureFilename(
            userId = job.userId,
            jobId = job.id,
            createdAtEpochSeconds = job.createdAt.epochSeconds,
            request = request,
            extension = extension
        )
    }
    
    fun generateSecureFilename(
        userId: String,
        jobId: String,
        createdAtEpochSeconds: Long,
        request: ScreenshotRequest,
        extension: String? = null
    ): String {
        val instant = Instant.fromEpochSeconds(createdAtEpochSeconds).toLocalDateTime(TimeZone.UTC)
        val year = instant.year
        val month = instant.monthNumber.toString().padStart(2, '0')
        
        val tokenInput = "$jobId|$userId|$createdAtEpochSeconds|${request.url}|${request.format.name}"
        val hmacToken = hmacPort.generateToken(tokenInput)
        
        val ext = extension ?: request.format.name.lowercase()
        
        val filename = "screenshots/$year/$month/$hmacToken.$ext"
        
        
        return filename
    }
    
    fun generateToken(job: ScreenshotJob): String {
        return hmacPort.generateScreenshotToken(
            jobId = job.id,
            userId = job.userId,
            createdAtEpochSeconds = job.createdAt.epochSeconds,
            jobType = job.jobType.name
        )
    }
    
    fun validateToken(token: String, job: ScreenshotJob): Boolean {
        return try {
            val isValid = hmacPort.validateScreenshotToken(
                token = token,
                jobId = job.id,
                userId = job.userId,
                createdAtEpochSeconds = job.createdAt.epochSeconds,
                jobType = job.jobType.name
            )
            
            
            isValid
        } catch (e: Exception) {
            logger.error("Error validating HMAC token for job ${job.id}", e)
            false
        }
    }
    
    fun extractTokenFromFilename(filename: String): String? {
        return try {
            val pathParts = filename.split("/")
            if (pathParts.size != 4 || pathParts[0] != "screenshots") {
                return null
            }
            
            val filenamePart = pathParts[3]
            val tokenPart = filenamePart.substringBeforeLast(".")
            
            if (tokenPart.length == authConfig.hmacTokenLength && 
                tokenPart.matches(Regex("^[A-Za-z0-9_-]+$"))) {
                tokenPart
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract token from filename: $filename", e)
            null
        }
    }
    
    fun isSecureFilename(filename: String): Boolean {
        return extractTokenFromFilename(filename) != null
    }
    
    fun generateLegacyFilename(request: ScreenshotRequest, extension: String? = null): String {
        val now = Clock.System.now()
        val instant = now.toLocalDateTime(TimeZone.UTC)
        val year = instant.year
        val month = instant.monthNumber.toString().padStart(2, '0')
        
        val timestamp = now.toEpochMilliseconds()
        val urlHash = request.url.hashCode().toString().takeLast(8)
        val ext = extension ?: request.format.name.lowercase()
        val dimensions = "${request.width}x${request.height}"
        
        return "screenshots/$year/$month/${timestamp}_${urlHash}_${dimensions}.$ext"
    }
}