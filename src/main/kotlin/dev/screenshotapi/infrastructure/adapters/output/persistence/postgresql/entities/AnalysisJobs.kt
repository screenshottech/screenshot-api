package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Analysis Jobs Database Table
 * 
 * Handles AI analysis jobs separately from screenshots for better scalability
 * and cost control. Each analysis job references a completed screenshot.
 */
object AnalysisJobs : IdTable<String>("analysis_jobs") {
    override val id: Column<EntityID<String>> = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)
    
    // Core job info
    val userId = varchar("user_id", 255)
    val screenshotJobId = varchar("screenshot_job_id", 255)
    val screenshotUrl = text("screenshot_url")
    val analysisType = varchar("analysis_type", 50) // AnalysisType enum name
    val status = varchar("status", 50) // AnalysisStatus enum name
    val language = varchar("language", 10).default("en")
    val webhookUrl = text("webhook_url").nullable()
    
    // Results
    val resultData = text("result_data").nullable() // JSON serialized analysis result
    val confidence = double("confidence").nullable()
    val metadata = text("metadata").nullable() // JSON serialized Map<String, String>
    
    // Processing info
    val processingTimeMs = long("processing_time_ms").nullable()
    val tokensUsed = integer("tokens_used").nullable()
    val costUsd = double("cost_usd").nullable()
    val errorMessage = text("error_message").nullable()
    
    // Timestamps
    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()
    val updatedAt = timestamp("updated_at")

    init {
        // Performance indexes for common queries
        index(false, userId)
        index(false, screenshotJobId) 
        index(false, status)
        index(false, analysisType)
        index(false, createdAt)
        
        // Composite indexes for common filtering patterns
        index(false, userId, status)
        index(false, userId, analysisType)
        index(false, status, createdAt) // For queue processing
    }
}