package dev.screenshotapi.infrastructure.plugins

import dev.screenshotapi.core.domain.exceptions.*
import dev.screenshotapi.infrastructure.adapters.input.rest.ErrorResponseDto
import dev.screenshotapi.infrastructure.exceptions.DatabaseException
import dev.screenshotapi.infrastructure.exceptions.QueueException
import dev.screenshotapi.infrastructure.exceptions.StorageException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureExceptionHandling() {
    install(StatusPages) {
        // Business Exceptions
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    code = "VALIDATION_ERROR",
                    message = cause.message ?: "Validation failed",
                    field = cause.field
                )
            )
        }

        exception<ResourceNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto(
                    code = "RESOURCE_NOT_FOUND",
                    message = cause.message ?: "Resource not found",
                    resourceType = cause.resourceType,
                    resourceId = cause.resourceId
                )
            )
        }

        exception<UserAlreadyExistsException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponseDto(
                    code = "USER_EXISTS",
                    message = cause.message ?: "User with this email already exists",
                    details = mapOf("email" to cause.email)
                )
            )
        }

        exception<InsufficientCreditsException> { call, cause ->
            call.respond(
                HttpStatusCode.PaymentRequired,
                ErrorResponseDto(
                    code = "INSUFFICIENT_CREDITS",
                    message = cause.message ?: "Insufficient credits",
                    details = mapOf(
                        "userId" to cause.userId,
                        "requiredCredits" to cause.requiredCredits.toString(),
                        "availableCredits" to cause.availableCredits.toString()
                    )
                )
            )
        }

        // Authentication Exceptions
        exception<AuthenticationException.InvalidCredentials> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponseDto(
                    code = "INVALID_CREDENTIALS",
                    message = "Invalid email or password"
                )
            )
        }

        exception<AuthenticationException.AccountLocked> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponseDto(
                    code = "ACCOUNT_LOCKED",
                    message = cause.message ?: "Account is locked",
                    details = mapOf("unlockTime" to cause.unlockTime.toString())
                )
            )
        }

        // Authorization Exceptions
        exception<AuthorizationException.InsufficientPermissions> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponseDto(
                    code = "INSUFFICIENT_PERMISSIONS",
                    message = "Access denied",
                    details = mapOf("requiredPermission" to cause.requiredPermission)
                )
            )
        }

        exception<AuthorizationException.ApiKeyRequired> { call, cause ->
            call.respond(
                HttpStatusCode.PaymentRequired,
                ErrorResponseDto(
                    code = "API_KEY_REQUIRED",
                    message = cause.message ?: "Valid API key required for screenshot processing",
                    details = mapOf(
                        "action" to "Create an API key in your dashboard",
                        "endpoint" to "/api/v1/user/api-keys"
                    )
                )
            )
        }

        exception<AuthorizationException.ApiKeyNotOwned> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponseDto(
                    code = "API_KEY_NOT_OWNED",
                    message = cause.message ?: "API key does not belong to authenticated user"
                )
            )
        }

        exception<AuthorizationException.ApiKeyInactive> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponseDto(
                    code = "API_KEY_INACTIVE",
                    message = cause.message ?: "API key is inactive"
                )
            )
        }

        exception<AuthorizationException.RateLimitExceeded> { call, cause ->
            call.respond(
                HttpStatusCode.TooManyRequests,
                ErrorResponseDto(
                    code = "RATE_LIMIT_EXCEEDED",
                    message = "Rate limit exceeded",
                    details = mapOf("resetTime" to cause.resetTime.toString())
                )
            )
        }

        // Screenshot Exceptions
        exception<ScreenshotException.InvalidUrl> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    code = "INVALID_URL",
                    message = cause.message ?: "Invalid URL",
                    details = mapOf("url" to cause.url)
                )
            )
        }

        exception<ScreenshotException.TimeoutException> { call, cause ->
            call.respond(
                HttpStatusCode.RequestTimeout,
                ErrorResponseDto(
                    code = "SCREENSHOT_TIMEOUT",
                    message = cause.message ?: "Screenshot timeout",
                    details = mapOf(
                        "url" to cause.url,
                        "timeoutMs" to cause.timeoutMs.toString()
                    )
                )
            )
        }

        exception<ScreenshotException.FileTooLarge> { call, cause ->
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponseDto(
                    code = "FILE_TOO_LARGE",
                    message = cause.message ?: "File too large",
                    details = mapOf(
                        "sizeBytes" to cause.sizeBytes.toString(),
                        "maxSizeBytes" to cause.maxSizeBytes.toString()
                    )
                )
            )
        }

        // Infrastructure Exceptions
        exception<StorageException> { call, cause ->
            call.application.log.error("Storage error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    code = "STORAGE_ERROR",
                    message = "Storage operation failed"
                )
            )
        }

        exception<DatabaseException> { call, cause ->
            call.application.log.error("Database error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    code = "DATABASE_ERROR",
                    message = "Database operation failed"
                )
            )
        }

        exception<QueueException> { call, cause ->
            call.application.log.error("Queue error", cause)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponseDto(
                    code = "QUEUE_ERROR",
                    message = "Queue service unavailable"
                )
            )
        }

        // Payment Exceptions
        exception<PaymentException.PaymentFailed> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    code = "PAYMENT_FAILED",
                    message = cause.message ?: "Payment failed",
                    details = mapOf("reason" to cause.reason)
                )
            )
        }

        exception<PaymentException.SubscriptionNotFound> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto(
                    code = "SUBSCRIPTION_NOT_FOUND",
                    message = cause.message ?: "Subscription not found",
                    details = mapOf("subscriptionId" to cause.subscriptionId)
                )
            )
        }

        exception<PaymentException.WebhookVerificationFailed> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponseDto(
                    code = "WEBHOOK_VERIFICATION_FAILED",
                    message = "Webhook signature verification failed"
                )
            )
        }

        // Generic Exception Handler
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    code = "INTERNAL_SERVER_ERROR",
                    message = "An unexpected error occurred"
                )
            )
        }
    }
}
