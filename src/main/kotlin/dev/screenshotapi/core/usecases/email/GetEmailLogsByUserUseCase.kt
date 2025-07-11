package dev.screenshotapi.core.usecases.email

import dev.screenshotapi.core.domain.entities.EmailLog
import dev.screenshotapi.core.domain.repositories.EmailLogRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException

class GetEmailLogsByUserUseCase(
    private val emailLogRepository: EmailLogRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: GetEmailLogsByUserRequest): GetEmailLogsByUserResponse {
        userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User not found: ${request.userId}")
        
        val emailLogs = emailLogRepository.findByUserId(request.userId)
        
        return GetEmailLogsByUserResponse(
            userId = request.userId,
            emailLogs = emailLogs,
            totalEmails = emailLogs.size
        )
    }
}

data class GetEmailLogsByUserRequest(
    val userId: String
)

data class GetEmailLogsByUserResponse(
    val userId: String,
    val emailLogs: List<EmailLog>,
    val totalEmails: Int
)