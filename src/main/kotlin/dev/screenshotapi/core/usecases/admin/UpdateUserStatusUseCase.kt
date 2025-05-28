package dev.screenshotapi.core.usecases.admin

class UpdateUserStatusUseCase {
    suspend operator fun invoke(request: UpdateUserStatusRequest): UpdateUserStatusResponse {
        return UpdateUserStatusResponse(
            userId = request.userId,
            status = request.status,
            updatedAt = kotlinx.datetime.Clock.System.now(),
            updatedBy = request.adminUserId
        )
    }
}
