package dev.screenshotapi.core.domain.exceptions

class InsufficientCreditsException(
    val userId: String,
    val requiredCredits: Int,
    val availableCredits: Int,
    message: String = "Insufficient credits. Required: $requiredCredits, Available: $availableCredits"
) : BusinessException(message)

