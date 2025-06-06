package dev.screenshotapi.infrastructure.adapters.input.rest

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

@Serializable
data class ErrorResponseDto(
    val code: String,
    val message: String,
    val timestamp: String = Instant.now().toString(),
    val field: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val details: Map<String, String>? = null,
    val traceId: String = UUID.randomUUID().toString()
) {
    companion object {
        fun validation(message: String, field: String? = null) = ErrorResponseDto(
            code = "VALIDATION_ERROR",
            message = message,
            field = field
        )

        fun notFound(resourceType: String, resourceId: String) = ErrorResponseDto(
            code = "RESOURCE_NOT_FOUND",
            message = "$resourceType not found",
            resourceType = resourceType,
            resourceId = resourceId
        )

        fun unauthorized(message: String = "Unauthorized") = ErrorResponseDto(
            code = "UNAUTHORIZED",
            message = message
        )

        fun forbidden(message: String = "Access denied") = ErrorResponseDto(
            code = "FORBIDDEN",
            message = message
        )

        fun internal(message: String = "Internal server error") = ErrorResponseDto(
            code = "INTERNAL_SERVER_ERROR",
            message = message
        )
    }
}
