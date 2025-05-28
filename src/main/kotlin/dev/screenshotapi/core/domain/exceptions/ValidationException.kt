package dev.screenshotapi.core.domain.exceptions

class ValidationException(
    message: String,
    val field: String? = null,
    cause: Throwable? = null
) : BusinessException(message, cause)
