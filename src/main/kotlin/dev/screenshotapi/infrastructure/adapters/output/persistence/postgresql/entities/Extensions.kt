package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import dev.screenshotapi.core.domain.entities.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow

// User conversions
fun ResultRow.toUser(): User {
    return User(
        id = this[Users.id].toString(),
        email = this[Users.email],
        name = this[Users.name],
        passwordHash = this[Users.passwordHash],
        planId = this[Users.planId].toString(),
        creditsRemaining = this[Users.creditsRemaining],
        status = UserStatus.valueOf(this[Users.status]),
        stripeCustomerId = this[Users.stripeCustomerId],
        lastActivity = this[Users.lastActivity],
        createdAt = this[Users.createdAt],
        updatedAt = this[Users.updatedAt]
    )
}

fun ResultRow.toApiKey(): ApiKey {
    val permissionsJson = this[ApiKeys.permissions]
    val permissions = try {
        Json.decodeFromString<List<String>>(permissionsJson)
            .map { Permission.valueOf(it) }
            .toSet()
    } catch (e: Exception) {
        // Fallback si el JSON no es válido
        setOf(Permission.SCREENSHOT_CREATE, Permission.SCREENSHOT_READ)
    }

    return ApiKey(
        id = this[ApiKeys.id].toString(),
        userId = this[ApiKeys.userId].toString(),
        name = this[ApiKeys.name],
        keyHash = this[ApiKeys.keyHash],
        keyPrefix = this[ApiKeys.keyPrefix],
        permissions = permissions,
        rateLimit = this[ApiKeys.rateLimit],
        usageCount = this[ApiKeys.usageCount],
        isActive = this[ApiKeys.isActive],
        lastUsed = this[ApiKeys.lastUsed],
        expiresAt = this[ApiKeys.expiresAt],
        createdAt = this[ApiKeys.createdAt]
    )
}

// ScreenshotJob conversions
fun ResultRow.toScreenshotJob(): ScreenshotJob {
    val optionsJson = this[Screenshots.options]
    val request = try {
        Json.decodeFromString<ScreenshotRequest>(optionsJson)
    } catch (e: Exception) {
        // Fallback request si el JSON no es válido
        ScreenshotRequest(
            url = this[Screenshots.url],
            width = 1920,
            height = 1080,
            fullPage = false,
            format = ScreenshotFormat.PNG
        )
    }

    return ScreenshotJob(
        id = this[Screenshots.id].toString(),
        userId = this[Screenshots.userId].toString(),
        apiKeyId = this[Screenshots.apiKeyId].toString(),
        request = request,
        status = ScreenshotStatus.valueOf(this[Screenshots.status]),
        resultUrl = this[Screenshots.resultUrl],
        errorMessage = this[Screenshots.errorMessage],
        processingTimeMs = this[Screenshots.processingTimeMs],
        webhookUrl = this[Screenshots.webhookUrl],
        webhookSent = this[Screenshots.webhookSent],
        createdAt = this[Screenshots.createdAt],        // Ya es kotlinx.datetime.Instant
        updatedAt = this[Screenshots.updatedAt],        // Ya es kotlinx.datetime.Instant
        completedAt = this[Screenshots.completedAt]     // Ya es kotlinx.datetime.Instant?
    )
}

// Plan conversions
fun ResultRow.toPlan(): Plan {
    val featuresJson = this[Plans.features]
    val features = try {
        if (featuresJson != null) {
            Json.decodeFromString<List<String>>(featuresJson)
        } else {
            emptyList<String>()
        }
    } catch (e: Exception) {
        emptyList<String>()
    }

    return Plan(
        id = this[Plans.id].toString(),
        name = this[Plans.name],
        description = this[Plans.description],
        creditsPerMonth = this[Plans.creditsPerMonth],
        priceCents = this[Plans.priceCents],
        currency = this[Plans.currency],
        features = features,
        isActive = this[Plans.isActive],
        createdAt = this[Plans.createdAt],
        updatedAt = this[Plans.updatedAt]
    )
}

// Helper functions for JSON serialization
fun Set<Permission>.toJson(): String = Json.encodeToString(this.map { it.name })
fun ScreenshotRequest.toJson(): String = Json.encodeToString(this)
fun List<String>.toJson(): String = Json.encodeToString(this)
