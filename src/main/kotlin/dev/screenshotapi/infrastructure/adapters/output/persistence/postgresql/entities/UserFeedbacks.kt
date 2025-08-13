package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.FeedbackType
import dev.screenshotapi.core.domain.entities.UserFeedback
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 * User Feedback table definition following established patterns
 */
object UserFeedbacks : Table("user_feedback") {
    val id = varchar("id", 255)
    val userId = varchar("user_id", 255)
    val feedbackType = varchar("feedback_type", 50)
    val rating = integer("rating").nullable()
    val subject = varchar("subject", 255).nullable()
    val message = text("message")
    val metadata = text("metadata").default("{}")
    val status = varchar("status", 50).default("PENDING")
    val userAgent = text("user_agent").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val adminNotes = text("admin_notes").nullable()
    val resolvedBy = varchar("resolved_by", 255).nullable()
    val resolvedAt = timestamp("resolved_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Extension function to convert ResultRow to UserFeedback entity
 */
fun ResultRow.toUserFeedback(): UserFeedback? {
    return try {
        val metadataJson = this[UserFeedbacks.metadata]
        val metadata = if (metadataJson.isNotBlank()) {
            Json.decodeFromString<Map<String, String>>(metadataJson)
        } else {
            emptyMap()
        }

        UserFeedback(
            id = this[UserFeedbacks.id],
            userId = this[UserFeedbacks.userId],
            feedbackType = FeedbackType.fromString(this[UserFeedbacks.feedbackType])
                ?: FeedbackType.GENERAL,
            rating = this[UserFeedbacks.rating],
            subject = this[UserFeedbacks.subject],
            message = this[UserFeedbacks.message],
            metadata = metadata,
            status = FeedbackStatus.fromString(this[UserFeedbacks.status])
                ?: FeedbackStatus.PENDING,
            userAgent = this[UserFeedbacks.userAgent],
            ipAddress = this[UserFeedbacks.ipAddress],
            adminNotes = this[UserFeedbacks.adminNotes],
            resolvedBy = this[UserFeedbacks.resolvedBy],
            resolvedAt = this[UserFeedbacks.resolvedAt],
            createdAt = this[UserFeedbacks.createdAt],
            updatedAt = this[UserFeedbacks.updatedAt]
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * Extension function to convert UserFeedback to insert/update statement
 */
fun UserFeedback.toInsertStatement(): UserFeedbacks.(UpdateBuilder<*>) -> Unit = {
    it[UserFeedbacks.id] = this@toInsertStatement.id
    it[userId] = this@toInsertStatement.userId
    it[feedbackType] = this@toInsertStatement.feedbackType.name
    it[rating] = this@toInsertStatement.rating
    it[subject] = this@toInsertStatement.subject
    it[message] = this@toInsertStatement.message
    it[metadata] = Json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        kotlinx.serialization.json.JsonObject(this@toInsertStatement.metadata.mapValues { entry ->
            kotlinx.serialization.json.JsonPrimitive(entry.value)
        })
    )
    it[status] = this@toInsertStatement.status.name
    it[userAgent] = this@toInsertStatement.userAgent
    it[ipAddress] = this@toInsertStatement.ipAddress
    it[adminNotes] = this@toInsertStatement.adminNotes
    it[resolvedBy] = this@toInsertStatement.resolvedBy
    it[resolvedAt] = this@toInsertStatement.resolvedAt
    it[createdAt] = this@toInsertStatement.createdAt
    it[updatedAt] = this@toInsertStatement.updatedAt
}
