package dev.screenshotapi.infrastructure.exceptions

sealed class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FileNotFoundException(val filename: String) : StorageException("File not found: $filename")
    class UploadFailedException(val filename: String, cause: Throwable? = null) :
        StorageException("Upload failed for: $filename", cause)

    class DownloadFailedException(val filename: String, cause: Throwable? = null) :
        StorageException("Download failed for: $filename", cause)

    class DeleteFailedException(val filename: String, cause: Throwable? = null) :
        StorageException("Delete failed for: $filename", cause)

    class StorageQuotaExceeded : StorageException("Storage quota exceeded")
    class InvalidStorageConfiguration(val reason: String) : StorageException("Invalid storage configuration: $reason")
}
