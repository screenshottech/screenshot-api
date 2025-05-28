package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.usecases.screenshot.*
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.ScreenshotRequestDto
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.ScreenshotStatus
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.toDto
import dev.screenshotapi.infrastructure.auth.ApiKeyPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScreenshotController : KoinComponent {
    private val takeScreenshotUseCase: TakeScreenshotUseCase by inject()
    private val getScreenshotStatusUseCase: GetScreenshotStatusUseCase by inject()
    private val listScreenshotsUseCase: ListScreenshotsUseCase by inject()

    suspend fun takeScreenshot(call: ApplicationCall) {
        val dto = call.receive<ScreenshotRequestDto>()
        val principal = call.principal<ApiKeyPrincipal>()!!

        val request = TakeScreenshotRequest(
            userId = principal.userId,
            apiKeyId = principal.keyId,
            screenshotRequest = dto.toDomain(),
            webhookUrl = call.request.headers["X-Webhook-URL"]
        )

        val response = takeScreenshotUseCase(request)
        call.respond(HttpStatusCode.Accepted, response.toDto())
    }

    suspend fun getScreenshotStatus(call: ApplicationCall) {
        val jobId = call.parameters["jobId"]!!
        val principal = call.principal<ApiKeyPrincipal>()!!

        val request = GetScreenshotStatusRequest(
            jobId = jobId,
            userId = principal.userId
        )

        val response = getScreenshotStatusUseCase(request)
        call.respond(HttpStatusCode.OK, response.toDto())
    }

    suspend fun listScreenshots(call: ApplicationCall) {
        val principal = call.principal<ApiKeyPrincipal>()!!
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val statusParam = call.request.queryParameters["status"]
        val status = statusParam?.let { ScreenshotStatus.valueOf(it.uppercase()) }

        val request = ListScreenshotsRequest(
            userId = principal.userId,
            page = page,
            limit = limit,
            status = status
        )

        val response = listScreenshotsUseCase(request)
        call.respond(HttpStatusCode.OK, response.toDto())
    }
}
