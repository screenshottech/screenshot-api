package dev.screenshotapi.core.domain.exceptions

class UserAlreadyExistsException(
    val email: String,
    message: String = "User with email $email already exists"
) : BusinessException(message)
