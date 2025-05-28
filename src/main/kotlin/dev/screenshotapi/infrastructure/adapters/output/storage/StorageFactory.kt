package dev.screenshotapi.infrastructure.adapters.output.storage

import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.infrastructure.adapters.output.storage.local.LocalStorageAdapter
import dev.screenshotapi.infrastructure.config.StorageConfig

object StorageFactory {
    fun create(config: StorageConfig): StorageOutputPort {
        return if (config.useLocal) {
            LocalStorageAdapter(config.localPath)
        } else {
            // TODO: Implement S3StorageAdapter when needed
            LocalStorageAdapter(config.localPath)
        }
    }
}
