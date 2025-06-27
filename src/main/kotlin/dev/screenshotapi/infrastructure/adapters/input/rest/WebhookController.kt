package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.domain.entities.WebhookEvent
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.usecases.webhook.*
import dev.screenshotapi.infrastructure.adapters.input.rest.dto.webhook.*
import dev.screenshotapi.infrastructure.auth.requireUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days

class WebhookController : KoinComponent {
    private val logger = LoggerFactory.getLogger(WebhookController::class.java)
    private val createWebhookUseCase: CreateWebhookUseCase by inject()
    private val updateWebhookUseCase: UpdateWebhookUseCase by inject()
    private val deleteWebhookUseCase: DeleteWebhookUseCase by inject()
    private val listWebhooksUseCase: ListWebhooksUseCase by inject()
    private val getWebhookDeliveriesUseCase: GetWebhookDeliveriesUseCase by inject()
    private val regenerateWebhookSecretUseCase: RegenerateWebhookSecretUseCase by inject()
    private val sendWebhookUseCase: SendWebhookUseCase by inject()

    suspend fun createWebhook(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val request = call.receive<CreateWebhookRequestDto>()

            val webhook = createWebhookUseCase.invoke(
                userId = userId,
                url = request.url,
                events = request.events.map { WebhookEvent.valueOf(it) }.toSet(),
                description = request.description
            )

            call.respond(HttpStatusCode.Created, webhook.toDtoWithSecret())
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            logger.error("Failed to create webhook", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to create webhook"))
        }
    }

    suspend fun listWebhooks(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val webhooks = listWebhooksUseCase.invoke(userId)
            val response = WebhookListResponseDto(
                webhooks = webhooks.map { it.toDtoSafe() },
                total = webhooks.size
            )
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            logger.error("Failed to list webhooks", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to list webhooks"))
        }
    }

    suspend fun getWebhook(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val webhookId = call.parameters["webhookId"]
                ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation("webhookId is required"))

            val webhooks = listWebhooksUseCase.invoke(userId)
            val webhook = webhooks.find { it.id == webhookId }
                ?: return call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("Webhook", webhookId))

            call.respond(HttpStatusCode.OK, webhook.toDtoSafe())
        } catch (e: Exception) {
            logger.error("Failed to get webhook", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to get webhook"))
        }
    }

    suspend fun updateWebhook(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val webhookId = call.parameters["webhookId"]
                ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation("webhookId is required"))

            val request = call.receive<UpdateWebhookRequestDto>()

            val webhook = updateWebhookUseCase.invoke(
                webhookId = webhookId,
                userId = userId,
                url = request.url,
                events = request.events?.map { WebhookEvent.valueOf(it) }?.toSet(),
                description = request.description,
                isActive = request.isActive
            )

            call.respond(HttpStatusCode.OK, webhook.toDtoSafe())
        } catch (e: ResourceNotFoundException) {
            call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("Webhook", "unknown"))
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            logger.error("Failed to update webhook", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to update webhook"))
        }
    }

    suspend fun regenerateWebhookSecret(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val webhookId = call.parameters["webhookId"]
                ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation("webhookId is required"))

            val webhook = regenerateWebhookSecretUseCase.invoke(webhookId, userId)

            call.respond(HttpStatusCode.OK, webhook.toDtoWithSecret())
        } catch (e: ResourceNotFoundException) {
            call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("Webhook", "unknown"))
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponseDto.forbidden(e.message ?: "Access denied"))
        } catch (e: Exception) {
            logger.error("Failed to regenerate secret", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to regenerate secret"))
        }
    }

    suspend fun deleteWebhook(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val webhookId = call.parameters["webhookId"]
                ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation("webhookId is required"))

            val deleted = deleteWebhookUseCase.invoke(webhookId, userId)

            if (deleted) {
                call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("Webhook", webhookId))
            }
        } catch (e: ResourceNotFoundException) {
            call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("Webhook", "unknown"))
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponseDto.forbidden(e.message ?: "Access denied"))
        } catch (e: Exception) {
            logger.error("Failed to delete webhook", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to delete webhook"))
        }
    }

    suspend fun getWebhookDeliveries(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val webhookId = call.parameters["webhookId"]
                ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation("webhookId is required"))

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val deliveries = getWebhookDeliveriesUseCase.getDeliveries(webhookId, userId, limit)

            val response = WebhookDeliveryListResponseDto(
                deliveries = deliveries.map { it.toDto() },
                total = deliveries.size
            )
            call.respond(HttpStatusCode.OK, response)
        } catch (e: ResourceNotFoundException) {
            call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("Webhook", "unknown"))
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponseDto.forbidden(e.message ?: "Access denied"))
        } catch (e: Exception) {
            logger.error("Failed to get deliveries", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to get deliveries"))
        }
    }

    suspend fun getWebhookStats(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val webhookId = call.parameters["webhookId"]
                ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation("webhookId is required"))

            val daysParam = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
            val since = Clock.System.now().minus(daysParam.days)

            val stats = getWebhookDeliveriesUseCase.getDeliveryStats(webhookId, userId, since)

            call.respond(HttpStatusCode.OK, stats.toDto())
        } catch (e: ResourceNotFoundException) {
            call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("Webhook", "unknown"))
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponseDto.forbidden(e.message ?: "Access denied"))
        } catch (e: Exception) {
            logger.error("Failed to get stats", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to get stats"))
        }
    }

    suspend fun getAllWebhookDeliveries(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val deliveries = getWebhookDeliveriesUseCase.getUserDeliveries(userId, limit)

            val response = WebhookDeliveryListResponseDto(
                deliveries = deliveries.map { it.toDto() },
                total = deliveries.size
            )
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            logger.error("Failed to get deliveries", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to get deliveries"))
        }
    }

    suspend fun getWebhookDelivery(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val deliveryId = call.parameters["deliveryId"]
                ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponseDto.validation("deliveryId is required"))

            val delivery = getWebhookDeliveriesUseCase.getDelivery(deliveryId, userId)
            call.respond(HttpStatusCode.OK, delivery.toDto())
        } catch (e: ResourceNotFoundException) {
            call.respond(HttpStatusCode.NotFound, ErrorResponseDto.notFound("WebhookDelivery", "unknown"))
        } catch (e: ValidationException) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponseDto.forbidden(e.message ?: "Access denied"))
        } catch (e: Exception) {
            logger.error("Failed to get delivery", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to get delivery"))
        }
    }

    suspend fun getWebhookEvents(call: ApplicationCall) {
        try {
            val userId = call.requireUserId()
            val events = WebhookEvent.values().map { event ->
                mapOf(
                    "name" to event.name,
                    "description" to getEventDescription(event)
                )
            }
            call.respond(HttpStatusCode.OK, mapOf("events" to events))
        } catch (e: Exception) {
            logger.error("Failed to get events", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto.internal("Failed to get events"))
        }
    }

    suspend fun getDebugStats(call: ApplicationCall) {
        val userId = call.requireUserId()
        val webhooks = listWebhooksUseCase.invoke(userId)
        val totalDeliveries = getWebhookDeliveriesUseCase.getUserDeliveries(userId, 1000)

        val stats = WebhookDebugStatsDto(
            userId = userId,
            totalWebhooks = webhooks.size,
            activeWebhooks = webhooks.count { it.isActive },
            totalDeliveries = totalDeliveries.size,
            deliveryStats = WebhookDeliveryBreakdownDto(
                delivered = totalDeliveries.count { it.status.name == "DELIVERED" },
                failed = totalDeliveries.count { it.status.name == "FAILED" },
                pending = totalDeliveries.count { it.status.name == "PENDING" },
                retrying = totalDeliveries.count { it.status.name == "RETRYING" }
            ),
            availableEvents = WebhookEvent.values().map { it.name },
            webhookEndpoints = listOf(
                "POST /api/v1/webhooks - Create webhook",
                "GET /api/v1/webhooks - List webhooks",
                "PUT /api/v1/webhooks/{id} - Update webhook",
                "DELETE /api/v1/webhooks/{id} - Delete webhook",
                "POST /api/v1/webhooks/{id}/test - Test webhook",
                "POST /api/v1/webhooks/{id}/regenerate-secret - Regenerate secret"
            )
        )

        call.respond(HttpStatusCode.OK, stats)
    }

    suspend fun testWebhook(call: ApplicationCall) {
        val userId = call.requireUserId()
        val webhookId = call.parameters["webhookId"]
            ?: throw ValidationException("webhookId is required")

        val webhooks = listWebhooksUseCase.invoke(userId)
        val webhook = webhooks.find { it.id == webhookId }
            ?: throw ResourceNotFoundException("Webhook", webhookId)

        val testEventData = mapOf(
            "test" to "true",
            "timestamp" to Clock.System.now().toString(),
            "webhookId" to webhookId,
            "userId" to userId
        )

        val delivery = sendWebhookUseCase.sendWebhook(
            webhook = webhook,
            event = WebhookEvent.WEBHOOK_TEST,
            eventData = testEventData
        )

        call.respond(HttpStatusCode.OK, delivery.toDto())
    }

    private fun getEventDescription(event: WebhookEvent): String {
        return when (event) {
            WebhookEvent.SCREENSHOT_COMPLETED -> "Fired when a screenshot is successfully generated"
            WebhookEvent.SCREENSHOT_FAILED -> "Fired when a screenshot generation fails permanently"
            WebhookEvent.CREDITS_LOW -> "Fired when credits drop below 20%"
            WebhookEvent.CREDITS_EXHAUSTED -> "Fired when credits reach 0"
            WebhookEvent.SUBSCRIPTION_RENEWED -> "Fired when a subscription is renewed"
            WebhookEvent.SUBSCRIPTION_CANCELLED -> "Fired when a subscription is cancelled"
            WebhookEvent.PAYMENT_SUCCESSFUL -> "Fired when a payment is successful"
            WebhookEvent.PAYMENT_FAILED -> "Fired when a payment fails"
            WebhookEvent.PAYMENT_PROCESSED -> "Fired when a payment is successfully processed"
            WebhookEvent.USER_REGISTERED -> "Fired when a new user registers (admin webhooks only)"
            WebhookEvent.WEBHOOK_TEST -> "Fired when testing a webhook configuration"
        }
    }
}

fun Route.webhookRoutes() {
    val controller = WebhookController()

    route("/webhooks") {

        post { controller.createWebhook(call) }
        get { controller.listWebhooks(call) }
        get("/{webhookId}") { controller.getWebhook(call) }
        put("/{webhookId}") { controller.updateWebhook(call) }
        post("/{webhookId}/regenerate-secret") { controller.regenerateWebhookSecret(call) }
        delete("/{webhookId}") { controller.deleteWebhook(call) }
        get("/{webhookId}/deliveries") { controller.getWebhookDeliveries(call) }
        get("/{webhookId}/stats") { controller.getWebhookStats(call) }
        get("/deliveries") { controller.getAllWebhookDeliveries(call) }
        get("/deliveries/{deliveryId}") { controller.getWebhookDelivery(call) }
        get("/events") { controller.getWebhookEvents(call) }
        get("/debug/stats") { controller.getDebugStats(call) }
        post("/{webhookId}/test") { controller.testWebhook(call) }
    }
}
