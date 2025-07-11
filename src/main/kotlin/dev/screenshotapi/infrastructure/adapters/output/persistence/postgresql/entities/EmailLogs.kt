package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import dev.screenshotapi.core.domain.entities.EmailLog
import dev.screenshotapi.core.domain.entities.EmailType
import dev.screenshotapi.core.domain.entities.EmailCategory
import dev.screenshotapi.core.domain.entities.EmailPriority
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Email logs table for tracking email delivery and engagement
 */
object EmailLogs : Table("email_logs") {
    val id = varchar("id", 36)
    override val primaryKey = PrimaryKey(id)
    val userId = varchar("user_id", 36)
    val emailType = varchar("email_type", 50)
    val subject = varchar("subject", 200)
    val recipientEmail = varchar("recipient_email", 255)
    val sentAt = timestamp("sent_at")
    val opened = bool("opened").default(false)
    val clicked = bool("clicked").default(false)
    val openedAt = timestamp("opened_at").nullable()
    val clickedAt = timestamp("clicked_at").nullable()
    val bounced = bool("bounced").default(false)
    val bouncedAt = timestamp("bounced_at").nullable()
    val unsubscribed = bool("unsubscribed").default(false)
    val unsubscribedAt = timestamp("unsubscribed_at").nullable()
    val metadata = text("metadata").default("{}")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    
    // Indexes for performance
    init {
        index("idx_email_logs_user_id", false, userId)
        index("idx_email_logs_email_type", false, emailType)
        index("idx_email_logs_sent_at", false, sentAt)
        index("idx_email_logs_user_type", false, userId, emailType)
        index("idx_email_logs_opened", false, opened)
        index("idx_email_logs_clicked", false, clicked)
        index("idx_email_logs_bounced", false, bounced)
        index("idx_email_logs_unsubscribed", false, unsubscribed)
    }
}

/**
 * Extension function to convert ResultRow to EmailLog domain entity
 */
fun ResultRow.toEmailLog(): EmailLog {
    return EmailLog(
        id = this[EmailLogs.id],
        userId = this[EmailLogs.userId],
        emailType = EmailType.valueOf(this[EmailLogs.emailType]),
        subject = this[EmailLogs.subject],
        recipientEmail = this[EmailLogs.recipientEmail],
        sentAt = this[EmailLogs.sentAt],
        opened = this[EmailLogs.opened],
        clicked = this[EmailLogs.clicked],
        openedAt = this[EmailLogs.openedAt],
        clickedAt = this[EmailLogs.clickedAt],
        bounced = this[EmailLogs.bounced],
        bouncedAt = this[EmailLogs.bouncedAt],
        unsubscribed = this[EmailLogs.unsubscribed],
        unsubscribedAt = this[EmailLogs.unsubscribedAt],
        metadata = try {
            Json.decodeFromString<Map<String, String>>(this[EmailLogs.metadata])
        } catch (e: Exception) {
            emptyMap()
        },
        createdAt = this[EmailLogs.createdAt],
        updatedAt = this[EmailLogs.updatedAt]
    )
}

/**
 * Extension function to convert EmailLog to database insert/update values
 */
fun EmailLog.toInsertStatement(): EmailLogs.(org.jetbrains.exposed.sql.statements.UpdateBuilder<*>) -> Unit = {
    it[EmailLogs.id] = this@toInsertStatement.id
    it[EmailLogs.userId] = this@toInsertStatement.userId
    it[EmailLogs.emailType] = this@toInsertStatement.emailType.name
    it[EmailLogs.subject] = this@toInsertStatement.subject
    it[EmailLogs.recipientEmail] = this@toInsertStatement.recipientEmail
    it[EmailLogs.sentAt] = this@toInsertStatement.sentAt
    it[EmailLogs.opened] = this@toInsertStatement.opened
    it[EmailLogs.clicked] = this@toInsertStatement.clicked
    it[EmailLogs.openedAt] = this@toInsertStatement.openedAt
    it[EmailLogs.clickedAt] = this@toInsertStatement.clickedAt
    it[EmailLogs.bounced] = this@toInsertStatement.bounced
    it[EmailLogs.bouncedAt] = this@toInsertStatement.bouncedAt
    it[EmailLogs.unsubscribed] = this@toInsertStatement.unsubscribed
    it[EmailLogs.unsubscribedAt] = this@toInsertStatement.unsubscribedAt
    it[EmailLogs.metadata] = Json.encodeToString(this@toInsertStatement.metadata)
    it[EmailLogs.createdAt] = this@toInsertStatement.createdAt
    it[EmailLogs.updatedAt] = this@toInsertStatement.updatedAt
}