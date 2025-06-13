package dev.screenshotapi.core.domain.entities

import kotlinx.datetime.Instant

data class Plan(
    val id: String,
    val name: String,
    val description: String? = null,
    val creditsPerMonth: Int,
    val priceCentsMonthly: Int,
    val priceCentsAnnual: Int? = null,
    val billingCycle: String = "monthly", // 'monthly' or 'annual'
    val currency: String = "USD",
    val features: List<String> = emptyList(),
    val isActive: Boolean = true,
    val stripeProductId: String? = null,
    val stripePriceIdMonthly: String? = null,
    val stripePriceIdAnnual: String? = null,
    val stripeMetadata: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)
