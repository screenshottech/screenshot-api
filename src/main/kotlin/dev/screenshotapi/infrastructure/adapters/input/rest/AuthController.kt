package dev.screenshotapi.infrastructure.adapters.input.rest


import dev.screenshotapi.core.domain.exceptions.AuthenticationException
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.UserAlreadyExistsException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.usecases.auth.*
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.CreateApiKeyRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.LoginRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.RegisterRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.UpdateApiKeyRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.UpdateProfileRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.toDto
import dev.screenshotapi.infrastructure.auth.UserPrincipal
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
import dev.screenshotapi.infrastructure.auth.providers.LocalAuthProvider
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
    private val updateApiKeyUseCase: UpdateApiKeyUseCase by inject()
    private val getUserProfileUseCase: GetUserProfileUseCase by inject()
    private val updateUserProfileUseCase: UpdateUserProfileUseCase by inject()
    private val getUserUsageUseCase: GetUserUsageUseCase by inject()
    private val getUserUsageTimelineUseCase: GetUserUsageTimelineUseCase by inject()
    private val authProviderFactory: AuthProviderFactory by inject()

    suspend fun login(call: ApplicationCall) {
        try {
            val dto = call.receive<LoginRequestDto>()
            val request = AuthenticateUserRequest(dto.email, dto.password)
            val response = authenticateUserUseCase(request)

            // Generate JWT token for the authenticated user
            val localAuthProvider = authProviderFactory.getProvider("local") as? LocalAuthProvider
            val jwt = localAuthProvider?.createToken(response.userId ?: "unknown") ?: "jwt_generation_failed"
            
            call.respond(
                HttpStatusCode.OK, mapOf(
                    "token" to jwt,
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
                    "status" to response.status.name.lowercase(),
                    "roles" to response.roles.map { it.name.lowercase() },
                    "creditsRemaining" to response.creditsRemaining
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

            val apiKeysDto = response.apiKeys.map { apiKey ->
                dev.screenshotapi.infrastructure.adapters.input.rest.dto.ApiKeySummaryResponseDto(
                    id = apiKey.id,
                    name = apiKey.name,
                    isActive = apiKey.isActive,
                    maskedKey = "sk_****${apiKey.id.takeLast(4)}",
                    usageCount = 0, // TODO: implement usage tracking
                    createdAt = apiKey.createdAt.toString(),
                    lastUsedAt = null // TODO: implement last used tracking
                )
            }
            
            val responseDto = dev.screenshotapi.infrastructure.adapters.input.rest.dto.ApiKeysListResponseDto(
                apiKeys = apiKeysDto
            )

            call.respond(HttpStatusCode.OK, responseDto)

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

            call.respond(HttpStatusCode.Created, response.toDto())

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

    suspend fun updateApiKey(call: ApplicationCall) {
        try {
            val principal = call.principal<UserPrincipal>()!!
            val keyId = call.parameters["keyId"]!!
            val dto = call.receive<UpdateApiKeyRequestDto>()
            val request = UpdateApiKeyRequest(
                userId = principal.userId,
                apiKeyId = keyId,
                isActive = dto.isActive,
                name = dto.name
            )
            val response = updateApiKeyUseCase(request)

            call.respond(HttpStatusCode.OK, response.toDto())

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("API Key", call.parameters["keyId"] ?: "unknown")
            )
        } catch (e: ValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation(e.message ?: "Validation failed", e.field)
            )
        } catch (e: Exception) {
            call.application.log.error("Update API key error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to update API key")
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
            println("Getting usage - checking principal...")
            val principal = call.principal<UserPrincipal>()
            println("Principal: $principal")
            
            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No valid principal found"))
                return
            }
            
            println("User ID from principal: ${principal.userId}")
            val response = getUserUsageUseCase(GetUserUsageRequest(principal.userId))

            call.respond(HttpStatusCode.OK, response.toDto())

        } catch (e: Exception) {
            call.application.log.error("Get usage error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to get usage statistics")
            )
        }
    }

    suspend fun getUsageTimeline(call: ApplicationCall) {
        try {
            // Check authentication first
            val principal = call.principal<UserPrincipal>()
            if (principal == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponseDto.unauthorized("Authentication required")
                )
                return
            }
            
            // Validate period parameter with proper error handling
            val periodParam = call.parameters["period"]
            val period = try {
                dev.screenshotapi.core.domain.entities.TimePeriod.fromString(periodParam)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDto.validation(
                        "Invalid period parameter. Valid values: 7d, 30d, 90d, 1y",
                        "period"
                    )
                )
                return
            }
            
            val granularity = call.parameters["granularity"]?.let { 
                try { 
                    dev.screenshotapi.core.domain.entities.TimeGranularity.valueOf(it.uppercase()) 
                } catch (e: IllegalArgumentException) { 
                    dev.screenshotapi.core.domain.entities.TimeGranularity.DAILY 
                }
            } ?: dev.screenshotapi.core.domain.entities.TimeGranularity.DAILY

            val request = GetUserUsageTimelineRequest(
                userId = principal.userId,
                period = period,
                granularity = granularity
            )
            
            val response = getUserUsageTimelineUseCase(request)

            call.respond(HttpStatusCode.OK, response.toDto())

        } catch (e: ResourceNotFoundException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponseDto.notFound("User", "timeline")
            )
        } catch (e: ValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto.validation(e.message ?: "Validation failed", e.field)
            )
        } catch (e: Exception) {
            call.application.log.error("Get usage timeline error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto.internal("Failed to get usage timeline")
            )
        }
    }
}
