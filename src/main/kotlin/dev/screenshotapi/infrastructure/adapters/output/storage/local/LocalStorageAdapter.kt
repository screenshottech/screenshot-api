package dev.screenshotapi.infrastructure.adapters.output.storage.local

import dev.screenshotapi.core.ports.output.FileMetadata
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.infrastructure.exceptions.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.io.File

// infrastructure/adapters/output/storage/local/LocalStorageAdapter.kt
class LocalStorageAdapter(
    private val basePath: String,
    private val baseUrl: String = "http://localhost:8080/files"
) : StorageOutputPort {

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        // Crear directorio base si no existe
        val baseDir = File(basePath)
        if (!baseDir.exists()) {
            baseDir.mkdirs()
            logger.info("Created storage directory: $basePath")
        }
    }

    override suspend fun upload(data: ByteArray, filename: String): String = withContext(Dispatchers.IO) {
        upload(data, filename, detectContentType(filename))
    }

    override suspend fun upload(data: ByteArray, filename: String, contentType: String): String =
        withContext(Dispatchers.IO) {
            try {
                val sanitizedFilename = sanitizeFilename(filename)
                val file = File(basePath, sanitizedFilename)

                // Crear directorios padre si no existen
                file.parentFile?.mkdirs()

                file.writeBytes(data)

                logger.info("Uploaded file: $sanitizedFilename (${data.size} bytes)")
                return@withContext getUrl(sanitizedFilename)

            } catch (e: Exception) {
                logger.error("Failed to upload file: $filename", e)
                throw StorageException.UploadFailedException(filename, e)
            }
        }

    override suspend fun uploadFromFile(filePath: String, filename: String): String = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                throw StorageException.FileNotFoundException(filePath)
            }

            val data = sourceFile.readBytes()
            upload(data, filename)

        } catch (e: Exception) {
            logger.error("Failed to upload from file: $filePath", e)
            throw StorageException.UploadFailedException(filename, e)
        }
    }

    override suspend fun delete(filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(basePath, sanitizeFilename(filename))
            val deleted = file.delete()

            if (deleted) {
                logger.info("Deleted file: $filename")
            } else {
                logger.warn("File not found for deletion: $filename")
            }

            deleted
        } catch (e: Exception) {
            logger.error("Failed to delete file: $filename", e)
            false
        }
    }

    override suspend fun exists(filename: String): Boolean = withContext(Dispatchers.IO) {
        File(basePath, sanitizeFilename(filename)).exists()
    }

    override suspend fun getUrl(filename: String): String {
        return "$baseUrl/${sanitizeFilename(filename)}"
    }

    override suspend fun getPresignedUrl(filename: String, expirationMinutes: Int): String {
        // Para local storage, simplemente retornamos la URL normal
        // En un escenario real podrías implementar tokens temporales
        return getUrl(filename)
    }

    override suspend fun copy(sourceFilename: String, targetFilename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(basePath, sanitizeFilename(sourceFilename))
            val targetFile = File(basePath, sanitizeFilename(targetFilename))

            if (!sourceFile.exists()) {
                throw StorageException.FileNotFoundException(sourceFilename)
            }

            targetFile.parentFile?.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = true)

            logger.info("Copied file: $sourceFilename -> $targetFilename")
            true
        } catch (e: Exception) {
            logger.error("Failed to copy file: $sourceFilename -> $targetFilename", e)
            false
        }
    }

    override suspend fun getMetadata(filename: String): FileMetadata? = withContext(Dispatchers.IO) {
        try {
            val file = File(basePath, sanitizeFilename(filename))
            if (!file.exists()) return@withContext null

            FileMetadata(
                filename = filename,
                size = file.length(),
                contentType = detectContentType(filename),
                lastModified = Instant.fromEpochMilliseconds(file.lastModified())
            )
        } catch (e: Exception) {
            logger.error("Failed to get metadata for: $filename", e)
            null
        }
    }

    private fun sanitizeFilename(filename: String): String {
        // Remover caracteres peligrosos y paths relativos
        return filename
            .replace("..", "")
            .replace("/", "_")
            .replace("\\", "_")
            .replace(" ", "_")
            .take(255) // Límite de longitud de archivos
    }

    private fun detectContentType(filename: String): String {
        return when (filename.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "pdf" -> "application/pdf"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
