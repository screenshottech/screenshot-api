package dev.screenshotapi.infrastructure.adapters.input.rest


import dev.screenshotapi.core.domain.exceptions.AuthenticationException
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.UserAlreadyExistsException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.usecases.auth.*
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.CreateApiKeyRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.LoginRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.RegisterRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.UpdateProfileRequestDto
import dev.screenshotapi.infrastructure.auth.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AuthController : KoinComponent {
    private val authenticateUserUseCase: AuthenticateUserUseCase by inject()
    private val registerUserUseCase: RegisterUserUseCase by inject()
    private val createApiKeyUseCase: CreateApiKeyUseCase by inject()
    private val listApiKeysUseCase: ListApiKeysUseCase by inject()
    private val deleteApiKeyUseCase: DeleteApiKeyUseCase by inject()
    private val getUserProfileUseCase: GetUserProfileUseCase by inject()
    private val updateUserProfileUseCase: UpdateUserProfileUseCase by inject()
    private val getUserUsageUseCase: GetUserUsageUseCase by inject()

    suspend fun login(call: ApplicationCall) {
        try {
            val dto = call.receive<LoginRequestDto>()
            val request = AuthenticateUserRequest(dto.email, dto.password)
            val response = authenticateUserUseCase(request)

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "token" to "jwt_placeholder",
                    "userId" to response.userId,
                    "email" to response.email
                )
            )

        } catch (e: AuthenticationException.InvalidCredentials) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponseDto.unauthorized("Invalid email or password")
            )
        } catch (e: AuthenticationException.AccountLocked) {
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponseDto(
                    code = "ACCOUNT_LOCKED",
                    message = "Account is temporarily locked",
                    details = mapOf("unlockTime" to e.unlockTime.toString())
                )
            )
        } catch (e: Exception) {
            call.application.log.error("Login error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Login failed")
            )
        }
    }

    suspend fun register(call: ApplicationCall) {
        try {
            val dto = call.receive<RegisterRequestDto>()
            val request = RegisterUserRequest(dto.email, dto.password, dto.name ?: "User")
            val response = registerUserUseCase(request)

            call.respond(
                HttpStatusCode.Created, mapOf(
                    "userId" to response.userId,
                    "email" to response.email,
                    "status" to response.status?.name?.lowercase()
                )
            )

        } catch (e: ValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation(e.message ?: "Validation failed", e.field)
            )
        } catch (e: UserAlreadyExistsException) {
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponseDto(
                    code = "USER_EXISTS",
                    message = "User with this email already exists"
                )
            )
        } catch (e: Exception) {
            call.application.log.error("Registration error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Registration failed")
            )
        }
    }

    suspend fun getProfile(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            val response = getUserProfileUseCase(GetUserProfileRequest(principal.userId))

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "userId" to response.userId,
                    "email" to response.email,
                    "name" to response.name,
                    "status" to response.status.name.lowercase()
                )
            )

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("User", "profile")
            )
        } catch (e: Exception) {
            call.application.log.error("Get profile error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to get profile")
            )
        }
    }

    suspend fun updateProfile(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            val dto = call.receive<UpdateProfileRequestDto>()
            val request = UpdateUserProfileRequest(principal.userId, dto.name, dto.email)
            val response = updateUserProfileUseCase(request)

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "userId" to response.userId,
                    "email" to response.email,
                    "name" to response.name,
                    "status" to response.status.name.lowercase()
                )
            )

        } catch (e: ValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation(e.message ?: "Validation failed", e.field)
            )
        } catch (e: Exception) {
            call.application.log.error("Update profile error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to update profile")
            )
        }
    }

    suspend fun listApiKeys(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            val response = listApiKeysUseCase(ListApiKeysRequest(principal.userId))

            call.respond(
                HttpStatusCode.OK, mapOf(
                "apiKeys" to response.apiKeys.map {
                    mapOf(
                        "id" to it.id,
                        "name" to it.name,
                        "isActive" to it.isActive
                    )
                }
            ))

        } catch (e: Exception) {
            call.application.log.error("List API keys error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to list API keys")
            )
        }
    }

    suspend fun createApiKey(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            val dto = call.receive<CreateApiKeyRequestDto>()
            val request = CreateApiKeyRequest(
                userId = principal.userId,
                name = dto.name
            )
            val response = createApiKeyUseCase(request)

            call.respond(
                HttpStatusCode.Created, mapOf(
                    "id" to response.id,
                    "name" to response.name,
                    "keyValue" to response.keyValue,
                    "isActive" to response.isActive
                )
            )

        } catch (e: ValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation(e.message ?: "Validation failed", e.field)
            )
        } catch (e: Exception) {
            call.application.log.error("Create API key error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to create API key")
            )
        }
    }

    suspend fun deleteApiKey(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            val keyId = call.parameters["keyId"]!!
            val request = DeleteApiKeyRequest(principal.userId, keyId)
            deleteApiKeyUseCase(request)

            call.respond(HttpStatusCode.NoContent)

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("API Key", call.parameters["keyId"] ?: "unknown")
            )
        } catch (e: Exception) {
            call.application.log.error("Delete API key error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to delete API key")
            )
        }
    }

    suspend fun getUsage(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            val response = getUserUsageUseCase(GetUserUsageRequest(principal.userId))

            call.respond(
                HttpStatusCode.OK, mapOf(
                    "userId" to response.userId,
                    "creditsRemaining" to response.creditsRemaining,
                    "totalScreenshots" to response.totalScreenshots,
                    "screenshotsLast30Days" to response.screenshotsLast30Days
                )
            )

        } catch (e: Exception) {
            call.application.log.error("Get usage error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to get usage statistics")
            )
        }
    }
}
