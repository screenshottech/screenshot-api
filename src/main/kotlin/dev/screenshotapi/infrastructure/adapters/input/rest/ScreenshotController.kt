package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.usecases.screenshot.*
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.*
import dev.screenshotapi.infrastructure.auth.ApiKeyPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * REST adapter for screenshot operations.
 * Handles HTTP requests, converts DTOs to domain objects, and domain objects back to DTOs.
 */
class ScreenshotController : KoinComponent {
    private val takeScreenshotUseCase: TakeScreenshotUseCase by inject()
    private val getScreenshotStatusUseCase: GetScreenshotStatusUseCase by inject()
    private val listScreenshotsUseCase: ListScreenshotsUseCase by inject()

    suspend fun takeScreenshot(call: ApplicationCall) {
        val dto = call.receive<TakeScreenshotRequestDto>()
        val principal = call.principal<ApiKeyPrincipal>()!!

        // Convert DTO to domain request
        val useCaseRequest = dto.toDomainRequest(
            userId = principal.userId,
            apiKeyId = principal.keyId
        )

        // Execute use case and convert response back to DTO
        val result = takeScreenshotUseCase(useCaseRequest)
        call.respond(HttpStatusCode.Accepted, result.toDto())
    }

    suspend fun getScreenshotStatus(call: ApplicationCall) {
        val jobId = call.parameters["jobId"]!!
        val principal = call.principal<ApiKeyPrincipal>()!!

        // Convert to domain request
        val useCaseRequest = GetScreenshotStatusUseCase.Request(
            jobId = jobId,
            userId = principal.userId
        )

        // Execute use case and convert response back to DTO
        val result = getScreenshotStatusUseCase(useCaseRequest)
        call.respond(HttpStatusCode.OK, result.toDto())
    }

    suspend fun listScreenshots(call: ApplicationCall) {
        val principal = call.principal<ApiKeyPrincipal>()!!
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val statusParam = call.request.queryParameters["status"]

        // Convert to domain request
        val useCaseRequest = ListScreenshotsUseCase.Request(
            userId = principal.userId,
            page = page,
            limit = limit,
            status = statusParam?.let { 
                dev.screenshotapi.core.domain.entities.ScreenshotStatus.valueOf(it.uppercase()) 
            }
        )

        // Execute use case and convert response back to DTO
        val result = listScreenshotsUseCase(useCaseRequest)
        call.respond(HttpStatusCode.OK, result.toDto())
    }
}
