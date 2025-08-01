package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * OCR Results Database Table
 * Following codebase conventions: plural naming, IdTable pattern
 */
object OcrResults : IdTable<String>("ocr_results") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)
    
    val userId = reference("user_id", Users)
    val screenshotJobId = varchar("screenshot_job_id", 255).nullable()
    val success = bool("success")
    val extractedText = text("extracted_text")
    val confidence = double("confidence")
    val wordCount = integer("word_count")
    val lines = text("lines") // JSON serialized
    val processingTime = double("processing_time")
    val language = varchar("language", 10)
    val engine = varchar("engine", 50)
    val structuredData = text("structured_data").nullable() // JSON serialized
    val metadata = text("metadata").nullable() // JSON serialized
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    init {
        // Performance indexes following codebase patterns
        index(false, userId)
        index(false, screenshotJobId)
        index(false, createdAt)
    }
}