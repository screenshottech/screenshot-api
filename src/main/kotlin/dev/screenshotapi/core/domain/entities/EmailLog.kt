package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Domain entity representing an email log entry for tracking email delivery and engagement.
 * This entity follows the established domain entity patterns and supports email analytics.
 */
data class EmailLog(
    val id: String,
    val userId: String,
    val emailType: EmailType,
    val subject: String,
    val recipientEmail: String,
    val sentAt: Instant,
    val opened: Boolean = false,
    val clicked: Boolean = false,
    val openedAt: Instant? = null,
    val clickedAt: Instant? = null,
    val bounced: Boolean = false,
    val bouncedAt: Instant? = null,
    val unsubscribed: Boolean = false,
    val unsubscribedAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /**
     * Mark email as opened
     */
    fun markAsOpened(openedAt: Instant = Clock.System.now()): EmailLog {
        return if (!opened) {
            copy(opened = true, openedAt = openedAt, updatedAt = Clock.System.now())
        } else {
            this
        }
    }

    /**
     * Mark email as clicked
     */
    fun markAsClicked(clickedAt: Instant = Clock.System.now()): EmailLog {
        return copy(
            clicked = true,
            clickedAt = clickedAt,
            opened = true, // If clicked, it was also opened
            openedAt = openedAt ?: clickedAt,
            updatedAt = Clock.System.now()
        )
    }

    /**
     * Mark email as bounced
     */
    fun markAsBounced(bouncedAt: Instant = Clock.System.now()): EmailLog {
        return copy(
            bounced = true,
            bouncedAt = bouncedAt,
            updatedAt = Clock.System.now()
        )
    }

    /**
     * Mark email as unsubscribed
     */
    fun markAsUnsubscribed(unsubscribedAt: Instant = Clock.System.now()): EmailLog {
        return copy(
            unsubscribed = true,
            unsubscribedAt = unsubscribedAt,
            updatedAt = Clock.System.now()
        )
    }

    /**
     * Get engagement score (0-100)
     */
    fun getEngagementScore(): Int {
        return when {
            clicked -> 100
            opened -> 50
            bounced -> 0
            else -> 25 // Delivered but not opened
        }
    }

    /**
     * Check if email was successfully delivered
     */
    fun isDelivered(): Boolean = !bounced

    /**
     * Check if email was engaged with (opened or clicked)
     */
    fun isEngaged(): Boolean = opened || clicked

    /**
     * Add metadata to the email log
     */
    fun addMetadata(key: String, value: String): EmailLog {
        return copy(
            metadata = metadata + (key to value),
            updatedAt = Clock.System.now()
        )
    }

    /**
     * Get metadata value by key
     */
    fun getMetadata(key: String): String? = metadata[key]

    /**
     * Get all metadata keys
     */
    fun getMetadataKeys(): Set<String> = metadata.keys
}