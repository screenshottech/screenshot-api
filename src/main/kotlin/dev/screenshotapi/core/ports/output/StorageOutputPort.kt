package dev.screenshotapi.core.ports.output

import kotlinx.datetime.Instant


interface StorageOutputPort {
    suspend fun upload(data: ByteArray, filename: String): String
    suspend fun upload(data: ByteArray, filename: String, contentType: String): String
    suspend fun uploadFromFile(filePath: String, filename: String): String
    suspend fun delete(filename: String): Boolean
    suspend fun exists(filename: String): Boolean
    suspend fun getUrl(filename: String): String
    suspend fun getPresignedUrl(filename: String, expirationMinutes: Int = 60): String
    suspend fun copy(sourceFilename: String, targetFilename: String): Boolean
    suspend fun getMetadata(filename: String): FileMetadata?
}

data class FileMetadata(
    val filename: String,
    val size: Long,
    val contentType: String,
    val lastModified: Instant,
    val etag: String? = null
)


open class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
class FileNotFoundException(filename: String) : StorageException("File not found: $filename")
class UploadFailedException(filename: String, cause: Throwable? = null) :
    StorageException("Upload failed for: $filename", cause)
