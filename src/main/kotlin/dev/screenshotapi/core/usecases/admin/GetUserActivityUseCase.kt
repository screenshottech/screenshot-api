package dev.screenshotapi.core.usecases.admin

class GetUserActivityUseCase {
    suspend operator fun invoke(request: GetUserActivityRequest): GetUserActivityResponse {
        return GetUserActivityResponse(
            userId = request.userId,
            days = request.days,
            activities = emptyList()
        )
    }
}
