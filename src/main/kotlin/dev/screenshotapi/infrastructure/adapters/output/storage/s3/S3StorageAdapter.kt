package dev.screenshotapi.infrastructure.adapters.output.storage.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CopyObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import dev.screenshotapi.core.ports.output.FileMetadata
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.core.ports.output.UploadFailedException
import kotlinx.datetime.Instant
import java.io.File

class S3StorageAdapter(
    private val s3Client: S3Client,
    private val bucketName: String,
    private val region: String,
    private val endpointUrl: String? = null,
    private val publicEndpointUrl: String? = null,
    private val includeBucketInUrl: Boolean = true // false para R2, true para S3
) : StorageOutputPort {

    override suspend fun upload(data: ByteArray, filename: String): String {
        return upload(data, filename, "application/octet-stream")
    }

    override suspend fun upload(data: ByteArray, filename: String, contentType: String): String {
        return try {
            val key = generateKey(filename)
            val request = PutObjectRequest {
                bucket = bucketName
                this.key = key
                body = ByteStream.fromBytes(data)
                this.contentType = contentType
            }

            s3Client.putObject(request)
            getUrl(key)
        } catch (e: Exception) {
            throw UploadFailedException(filename, e)
        }
    }

    override suspend fun uploadFromFile(filePath: String, filename: String): String {
        return try {
            val file = File(filePath)
            val data = file.readBytes()
            val contentType = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            upload(data, filename, contentType)
        } catch (e: Exception) {
            throw UploadFailedException(filename, e)
        }
    }

    override suspend fun delete(filename: String): Boolean {
        return try {
            val key = extractKeyFromFilename(filename)
            val request = DeleteObjectRequest {
                bucket = bucketName
                this.key = key
            }

            s3Client.deleteObject(request)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun exists(filename: String): Boolean {
        return try {
            val key = extractKeyFromFilename(filename)
            val request = HeadObjectRequest {
                bucket = bucketName
                this.key = key
            }
            s3Client.headObject(request)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getUrl(filename: String): String {
        val key = extractKeyFromFilename(filename)
        return if (endpointUrl != null) {
            // Use public endpoint if available, otherwise fall back to internal endpoint
            val baseUrl = publicEndpointUrl ?: endpointUrl
            if (includeBucketInUrl) {
                "$baseUrl/$bucketName/$key"
            } else {
                // For R2, bucket name should not be included in the public URL
                "$baseUrl/$key"
            }
        } else {
            // AWS S3
            "https://$bucketName.s3.$region.amazonaws.com/$key"
        }
    }

    override suspend fun getPresignedUrl(filename: String, expirationMinutes: Int): String {
        return getUrl(filename)
    }

    override suspend fun copy(sourceFilename: String, targetFilename: String): Boolean {
        return try {
            val sourceKey = extractKeyFromFilename(sourceFilename)
            val targetKey = generateKey(targetFilename)

            val request = CopyObjectRequest {
                bucket = bucketName
                key = targetKey
                copySource = "$bucketName/$sourceKey"
            }

            s3Client.copyObject(request)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getMetadata(filename: String): FileMetadata? {
        return try {
            val key = extractKeyFromFilename(filename)
            val request = HeadObjectRequest {
                bucket = bucketName
                this.key = key
            }

            val response = s3Client.headObject(request)
            FileMetadata(
                filename = filename,
                size = response.contentLength ?: 0L,
                contentType = response.contentType ?: "application/octet-stream",
                lastModified = response.lastModified?.let { Instant.fromEpochSeconds(it.epochSeconds) }
                    ?: Instant.fromEpochMilliseconds(0),
                etag = response.eTag
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun generateKey(filename: String): String {
        // Simply use the filename as provided by the caller
        // The caller is responsible for the full path structure
        return filename
    }

    private fun extractKeyFromFilename(filename: String): String {
        return when {
            filename.startsWith("https://") || filename.startsWith("http://") -> {
                // For URLs, extract the full key path after the domain/bucket
                val urlWithoutProtocol = filename.substringAfter("://")
                val pathStart = urlWithoutProtocol.indexOf("/")
                if (pathStart != -1) {
                    val fullPath = urlWithoutProtocol.substring(pathStart + 1)
                    // If URL includes bucket name in path (S3 style), skip it
                    if (includeBucketInUrl && fullPath.startsWith("$bucketName/")) {
                        fullPath.substringAfter("$bucketName/")
                    } else {
                        fullPath
                    }
                } else {
                    filename
                }
            }
            // For non-URLs, return as-is
            else -> filename
        }
    }
}
