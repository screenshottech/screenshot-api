package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql.entities

import org.jetbrains.exposed.sql.Index

// Índices adicionales para mejorar performance
val userEmailIndex = Index(listOf(Users.email), unique = true)
val apiKeyHashIndex = Index(listOf(ApiKeys.keyHash), unique = true)
val screenshotUserIdIndex = Index(listOf(Screenshots.userId), unique = false)
val screenshotStatusIndex = Index(listOf(Screenshots.status), unique = false)
val screenshotCreatedAtIndex = Index(listOf(Screenshots.createdAt), unique = false)
val usageLogsUserIdIndex = Index(listOf(UsageLogs.userId), unique = false)
val usageLogsTimestampIndex = Index(listOf(UsageLogs.timestamp), unique = false)
val activitiesUserIdIndex = Index(listOf(Activities.userId), unique = false)
val activitiesTimestampIndex = Index(listOf(Activities.timestamp), unique = false)
