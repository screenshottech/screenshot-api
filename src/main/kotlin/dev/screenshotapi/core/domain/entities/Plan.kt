package dev.screenshotapi.core.domain.entities

data class Plan(
    val id: String,
    val name: String,
    val creditsPerMonth: Int,
    val priceInCents: Int,
    val features: List<String> = emptyList(),
    val isActive: Boolean = true
)
