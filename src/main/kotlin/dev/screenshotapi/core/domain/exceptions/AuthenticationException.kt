package dev.screenshotapi.core.domain.exceptions

import java.time.Instant

sealed class AuthenticationException(message: String, cause: Throwable? = null) : BusinessException(message, cause) {
    class InvalidCredentials : AuthenticationException("Invalid email or password")
    class AccountLocked(val unlockTime: Instant) : AuthenticationException("Account is locked until $unlockTime")
    class AccountDisabled : AuthenticationException("Account is disabled")
    class EmailNotVerified : AuthenticationException("Email address not verified")
}
