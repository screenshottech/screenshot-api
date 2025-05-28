package dev.screenshotapi.core.usecases.admin

class ListUsersUseCase {
    suspend operator fun invoke(request: ListUsersRequest): ListUsersResponse {
        return ListUsersResponse(
            users = emptyList(),
            page = request.page,
            limit = request.limit,
            total = 0
        )
    }
}
