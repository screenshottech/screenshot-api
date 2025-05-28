package dev.screenshotapi.core.domain.exceptions

abstract class BusinessException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
