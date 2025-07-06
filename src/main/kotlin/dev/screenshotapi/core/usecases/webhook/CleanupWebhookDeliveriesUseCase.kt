package dev.screenshotapi.core.usecases.webhook

import dev.screenshotapi.core.domain.entities.WebhookDeliveryStatus
import dev.screenshotapi.core.domain.repositories.WebhookDeliveryRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * Use case for cleaning up old webhook deliveries to manage database storage
 */
class CleanupWebhookDeliveriesUseCase(
    private val webhookDeliveryRepository: WebhookDeliveryRepository
) {
    
    /**
     * Clean up old webhook deliveries based on retention period
     * 
     * @param retentionDays Number of days to retain deliveries
     * @param batchSize Number of records to delete in each batch
     * @return Total number of deliveries deleted
     */
    suspend fun cleanupOldDeliveries(
        retentionDays: Int = 30,
        batchSize: Int = 1000
    ): Int {
        return try {
            val cutoffDate = Clock.System.now().minus(retentionDays.days)
            var totalDeleted = 0
            
            while (true) {
                val deleted = webhookDeliveryRepository.deleteOldDeliveries(
                    before = cutoffDate,
                    status = null,
                    limit = batchSize
                )
                
                totalDeleted += deleted
                
                // Stop if we deleted 0 records or fewer than the batch size (indicating we're done)
                if (deleted == 0 || deleted < batchSize) {
                    break
                }
            }
            
            totalDeleted
        } catch (e: Exception) {
            // Log error and return 0 to indicate no deletions
            0
        }
    }
    
    /**
     * Clean up only failed webhook deliveries
     * 
     * @param retentionDays Number of days to retain failed deliveries
     * @param batchSize Number of records to delete in each batch
     * @return Total number of failed deliveries deleted
     */
    suspend fun cleanupFailedDeliveries(
        retentionDays: Int = 7,
        batchSize: Int = 1000
    ): Int {
        return try {
            val cutoffDate = Clock.System.now().minus(retentionDays.days)
            
            webhookDeliveryRepository.deleteOldDeliveries(
                before = cutoffDate,
                status = WebhookDeliveryStatus.FAILED,
                limit = batchSize
            )
        } catch (e: Exception) {
            // Log error and return 0 to indicate no deletions
            0
        }
    }
}