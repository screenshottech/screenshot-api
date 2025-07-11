package dev.screenshotapi.infrastructure.adapters.input.rest


import dev.screenshotapi.core.usecases.auth.*
import dev.screenshotapi.core.usecases.email.GetEmailLogsByUserRequest
import dev.screenshotapi.core.usecases.email.GetEmailLogsByUserUseCase
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import dev.screenshotapi.infrastructure.auth.AuthProviderFactory
import dev.screenshotapi.infrastructure.auth.providers.LocalAuthProvider
import dev.screenshotapi.infrastructure.auth.requireUserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
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
    private val getEmailLogsByUserUseCase: GetEmailLogsByUserUseCase by inject()
    private val authProviderFactory: AuthProviderFactory by inject()

    suspend fun login(call: ApplicationCall) {
        val dto = call.receive<LoginRequestDto>()
        val request = AuthenticateUserRequest(dto.email, dto.password)
        val response = authenticateUserUseCase(request)

        // Generate JWT token for the authenticated user
        val localAuthProvider = authProviderFactory.getProvider("local") as? LocalAuthProvider
        val jwt = localAuthProvider?.createToken(response.userId ?: "unknown") ?: "jwt_generation_failed"

        call.respond(
            HttpStatusCode.OK,
            LoginResponseDto(
                token = jwt,
                userId = response.userId ?: "unknown_user",
                email = response.email ?: "unknown@example.com",
                name = null,
                expiresAt = "placeholder_expiry"
            )
        )
    }

    suspend fun register(call: ApplicationCall) {
        val dto = call.receive<RegisterRequestDto>()
        val request = RegisterUserRequest(dto.email, dto.password, dto.name ?: "User")
        val response = registerUserUseCase(request)

        call.respond(HttpStatusCode.Created, response.toDto())
    }

    suspend fun getProfile(call: ApplicationCall) {
        val principal = call.requireUserPrincipal()
        val response = getUserProfileUseCase(GetUserProfileRequest(principal.userId))

        call.respond(HttpStatusCode.OK, response.toDto())
    }

    suspend fun updateProfile(call: ApplicationCall) {
        val principal = call.requireUserPrincipal()
        val dto = call.receive<UpdateProfileRequestDto>()
        val request = UpdateUserProfileRequest(principal.userId, dto.name, dto.email)
        val response = updateUserProfileUseCase(request)

        call.respond(HttpStatusCode.OK, response.toDto())
    }

    suspend fun listApiKeys(call: ApplicationCall) {
        val principal = call.requireUserPrincipal()
        val response = listApiKeysUseCase(ListApiKeysRequest(principal.userId))

        val apiKeysDto = response.apiKeys.map { apiKey ->
            ApiKeySummaryResponseDto(
                id = apiKey.id,
                name = apiKey.name,
                isActive = apiKey.isActive,
                isDefault = apiKey.isDefault,
                maskedKey = "sk_****${apiKey.id.takeLast(4)}",
                usageCount = 0, // TODO: implement usage tracking
                createdAt = apiKey.createdAt.toString(),
                lastUsedAt = null // TODO: implement last used tracking
            )
        }

        val responseDto = ApiKeysListResponseDto(
            apiKeys = apiKeysDto
        )

        call.respond(HttpStatusCode.OK, responseDto)
    }

    suspend fun createApiKey(call: ApplicationCall) {
        val principal = call.requireUserPrincipal()
        val dto = call.receive<CreateApiKeyRequestDto>()
        val request = CreateApiKeyRequest(
            userId = principal.userId,
            name = dto.name,
            setAsDefault = dto.setAsDefault
        )
        val response = createApiKeyUseCase(request)

        call.respond(HttpStatusCode.Created, response.toDto())
    }

    suspend fun updateApiKey(call: ApplicationCall) {
        val principal = call.requireUserPrincipal()
        val keyId = call.parameters["keyId"]!!
        val dto = call.receive<UpdateApiKeyRequestDto>()
        val request = UpdateApiKeyRequest(
            userId = principal.userId,
            apiKeyId = keyId,
            isActive = dto.isActive,
            name = dto.name,
            setAsDefault = dto.setAsDefault
        )
        val response = updateApiKeyUseCase(request)

        call.respond(HttpStatusCode.OK, response.toDto())
    }

    suspend fun deleteApiKey(call: ApplicationCall) {
        val principal = call.requireUserPrincipal()
        val keyId = call.parameters["keyId"]!!
        val request = DeleteApiKeyRequest(principal.userId, keyId)
        deleteApiKeyUseCase(request)

        call.respond(HttpStatusCode.NoContent)
    }

    suspend fun getUsage(call: ApplicationCall) {
        val principal = call.requireUserPrincipal()
        val response = getUserUsageUseCase(GetUserUsageRequest(principal.userId))

        call.respond(HttpStatusCode.OK, response.toDto())
    }

    suspend fun getUsageTimeline(call: ApplicationCall) {
        // Get authenticated user
        val principal = call.requireUserPrincipal()

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
    }

    suspend fun getEmailLogs(call: ApplicationCall) {
        val principal = call.requireUserPrincipal()
        val request = GetEmailLogsByUserRequest(principal.userId)
        val response = getEmailLogsByUserUseCase(request)

        call.respond(HttpStatusCode.OK, response.toDto())
    }
}
