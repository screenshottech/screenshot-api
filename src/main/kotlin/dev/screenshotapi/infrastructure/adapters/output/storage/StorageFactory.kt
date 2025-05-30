package dev.screenshotapi.infrastructure.adapters.output.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.net.url.Url
import dev.screenshotapi.core.ports.output.StorageOutputPort
import dev.screenshotapi.infrastructure.adapters.output.storage.local.LocalStorageAdapter
import dev.screenshotapi.infrastructure.adapters.output.storage.s3.S3StorageAdapter
import dev.screenshotapi.infrastructure.config.StorageConfig

object StorageFactory {
    fun create(config: StorageConfig): StorageOutputPort {
        return if (config.useLocal) {
            LocalStorageAdapter(config.localPath)
        } else {
            createS3StorageAdapter(config)
        }
    }
    
    private fun createS3StorageAdapter(config: StorageConfig): StorageOutputPort {
        require(config.s3Bucket != null) { "S3 bucket name is required for S3 storage" }
        require(config.s3Region != null) { "S3 region is required for S3 storage" }
        
        val s3Client = S3Client {
            region = config.s3Region
            config.awsEndpointUrl?.let { 
                endpointUrl = Url.parse(it)
                forcePathStyle = true // Required for LocalStack
            }
        }
        
        return S3StorageAdapter(s3Client, config.s3Bucket, config.s3Region, config.awsEndpointUrl, config.awsPublicEndpointUrl)
    }
}
