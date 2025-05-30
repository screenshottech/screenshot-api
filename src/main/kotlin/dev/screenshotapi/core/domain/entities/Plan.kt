package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

data class Plan(
    val id: String,
    val name: String,
    val description: String? = null,
    val creditsPerMonth: Int,
    val priceCents: Int,
    val currency: String = "USD",
    val features: List<String> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)
