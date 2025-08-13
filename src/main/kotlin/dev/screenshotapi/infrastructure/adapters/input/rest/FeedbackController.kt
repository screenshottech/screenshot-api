package dev.screenshotapi.infrastructure.adapters.input.rest

import dev.screenshotapi.core.domain.entities.FeedbackStatus
import dev.screenshotapi.core.domain.entities.FeedbackType
import dev.screenshotapi.core.usecases.feedback.*
import dev.screenshotapi.infrastructure.auth.requireUserPrincipal
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FeedbackController : KoinComponent {
    private val createFeedbackUseCase: CreateFeedbackUseCase by inject()
    private val getUserFeedbackUseCase: GetUserFeedbackUseCase by inject()
    private val getFeedbackAnalyticsUseCase: GetFeedbackAnalyticsUseCase by inject()

    fun Route.feedbackRoutes() {
        route("/user") {
            post("/feedback") {
                val principal = call.requireUserPrincipal()
                val dto = call.receive<CreateFeedbackRequestDto>()

                val feedbackType = FeedbackType.fromString(dto.feedbackType)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid feedback type: ${dto.feedbackType}")
                    )

                val request = CreateFeedbackRequest(
                    userId = principal.userId,
                    feedbackType = feedbackType,
                    message = dto.message,
                    rating = dto.rating,
                    subject = dto.subject,
                    metadata = dto.metadata ?: emptyMap(),
                    userAgent = call.request.headers["User-Agent"],
                    ipAddress = null
                )

                when (val response = createFeedbackUseCase.invoke(request)) {
                    is CreateFeedbackResponse.Success -> {
                        call.respond(HttpStatusCode.Created, response.feedback.toDto())
                    }
                    is CreateFeedbackResponse.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to response.message))
                    }
                }
            }

            get("/feedback") {
                val principal = call.requireUserPrincipal()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val status = call.request.queryParameters["status"]?.let { FeedbackStatus.fromString(it) }

                val request = GetUserFeedbackRequest(principal.userId, status, page, size)

                when (val response = getUserFeedbackUseCase.invoke(request)) {
                    is GetUserFeedbackResponse.Success -> {
                        call.respond(HttpStatusCode.OK, GetUserFeedbackResponseDto(
                            feedback = response.feedback.map { it.toDto() },
                            totalCount = response.totalCount,
                            page = response.page,
                            size = response.size,
                            hasMore = response.hasMore
                        ))
                    }
                    is GetUserFeedbackResponse.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to response.message))
                    }
                }
            }

            get("/feedback/types") {
                val types = FeedbackType.values().map {
                    FeedbackTypeDto(it.name, it.displayName, it.description)
                }
                call.respond(HttpStatusCode.OK, mapOf("types" to types))
            }
        }

        route("/admin") {
            get("/feedback/analytics") {
                val principal = call.requireUserPrincipal()

                if (!principal.canManageUsers()) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Admin access required")
                    )
                }

                val feedbackType = call.request.queryParameters["feedbackType"]?.let {
                    FeedbackType.fromString(it)
                }
                val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30

                val request = GetFeedbackAnalyticsRequest(feedbackType, days)

                when (val response = getFeedbackAnalyticsUseCase.invoke(request)) {
                    is GetFeedbackAnalyticsResponse.Success -> {
                        call.respond(HttpStatusCode.OK, FeedbackAnalyticsDto(
                            stats = response.stats.toDto(),
                            satisfaction = response.satisfaction.toDto()
                        ))
                    }
                    is GetFeedbackAnalyticsResponse.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to response.message))
                    }
                }
            }
        }
    }
}

// DTOs for request/response serialization

@Serializable
data class CreateFeedbackRequestDto(
    val feedbackType: String,
    val message: String,
    val rating: Int? = null,
    val subject: String? = null,
    val metadata: Map<String, String>? = null
)

@Serializable
data class UserFeedbackDto(
    val id: String,
    val feedbackType: String,
    val rating: Int?,
    val subject: String?,
    val message: String,
    val metadata: Map<String, String>,
    val status: String,
    val adminNotes: String?,
    val resolvedBy: String?,
    val resolvedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val isCritical: Boolean,
    val priority: String
)

@Serializable
data class GetUserFeedbackResponseDto(
    val feedback: List<UserFeedbackDto>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
    val hasMore: Boolean
)

@Serializable
data class FeedbackTypeDto(
    val name: String,
    val displayName: String,
    val description: String
)

@Serializable
data class FeedbackAnalyticsDto(
    val stats: FeedbackStatsDto,
    val satisfaction: SatisfactionMetricsDto
)

@Serializable
data class FeedbackStatsDto(
    val totalFeedback: Long,
    val pendingFeedback: Long,
    val resolvedFeedback: Long,
    val criticalFeedback: Long,
    val averageRating: Double?,
    val feedbackByType: Map<String, Long>,
    val feedbackByStatus: Map<String, Long>,
    val recentTrends: Map<String, Long>
)

@Serializable
data class SatisfactionMetricsDto(
    val averageRating: Double?,
    val totalRatings: Long,
    val ratingDistribution: Map<String, Long>, // Int keys serialized as strings
    val satisfactionTrend: List<DailySatisfactionDto>,
    val npsScore: Double?
)

@Serializable
data class DailySatisfactionDto(
    val date: String,
    val averageRating: Double?,
    val totalRatings: Long
)

// Extension functions for mapping domain entities to DTOs

private fun dev.screenshotapi.core.domain.entities.UserFeedback.toDto(): UserFeedbackDto = UserFeedbackDto(
    id = this.id,
    feedbackType = this.feedbackType.name,
    rating = this.rating,
    subject = this.subject,
    message = this.message,
    metadata = this.metadata,
    status = this.status.name,
    adminNotes = this.adminNotes,
    resolvedBy = this.resolvedBy,
    resolvedAt = this.resolvedAt?.toString(),
    createdAt = this.createdAt.toString(),
    updatedAt = this.updatedAt.toString(),
    isCritical = this.isCritical(),
    priority = this.getPriority().name
)

private fun dev.screenshotapi.core.ports.output.FeedbackStats.toDto(): FeedbackStatsDto = FeedbackStatsDto(
    totalFeedback = this.totalFeedback,
    pendingFeedback = this.pendingFeedback,
    resolvedFeedback = this.resolvedFeedback,
    criticalFeedback = this.criticalFeedback,
    averageRating = this.averageRating,
    feedbackByType = this.feedbackByType.mapKeys { it.key.name },
    feedbackByStatus = this.feedbackByStatus.mapKeys { it.key.name },
    recentTrends = this.recentTrends
)

private fun dev.screenshotapi.core.ports.output.SatisfactionMetrics.toDto(): SatisfactionMetricsDto = SatisfactionMetricsDto(
    averageRating = this.averageRating,
    totalRatings = this.totalRatings,
    ratingDistribution = this.ratingDistribution.mapKeys { it.key.toString() },
    satisfactionTrend = this.satisfactionTrend.map { it.toDto() },
    npsScore = this.npsScore
)

private fun dev.screenshotapi.core.ports.output.DailySatisfaction.toDto(): DailySatisfactionDto = DailySatisfactionDto(
    date = this.date,
    averageRating = this.averageRating,
    totalRatings = this.totalRatings
)
