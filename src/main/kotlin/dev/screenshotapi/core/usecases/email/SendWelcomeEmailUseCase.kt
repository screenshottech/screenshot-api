package dev.screenshotapi.core.usecases.email

import dev.screenshotapi.core.domain.entities.EmailLog
import dev.screenshotapi.core.domain.entities.EmailType
import dev.screenshotapi.core.domain.entities.User
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.EmailLogRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.infrastructure.services.EmailService
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.*

class SendWelcomeEmailUseCase(
    private val userRepository: UserRepository,
    private val emailLogRepository: EmailLogRepository,
    private val emailService: EmailService,
    private val logUsageUseCase: LogUsageUseCase
) : UseCase<SendWelcomeEmailRequest, SendWelcomeEmailResponse> {
    
    private val logger = LoggerFactory.getLogger(SendWelcomeEmailUseCase::class.java)
    
    override suspend fun invoke(request: SendWelcomeEmailRequest): SendWelcomeEmailResponse {
        logger.info("SEND_WELCOME_EMAIL_START: Starting welcome email [userId=${request.userId}]")
        
        // 1. Validate input
        if (request.userId.isBlank()) {
            throw ValidationException.Required("userId")
        }
        if (request.apiKey.isBlank()) {
            throw ValidationException.Required("apiKey")
        }
        
        // 2. Load user (validation)
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User not found: ${request.userId}")
        
        // 3. Check if welcome email already sent (idempotency)
        val existingEmail = emailLogRepository.findByUserIdAndType(request.userId, EmailType.WELCOME)
        if (existingEmail != null) {
            logger.info("SEND_WELCOME_EMAIL_ALREADY_SENT: Welcome email already sent [userId=${request.userId}, emailId=${existingEmail.id}]")
            return SendWelcomeEmailResponse(
                success = true,
                message = "Welcome email already sent",
                emailId = existingEmail.id,
                alreadySent = true
            )
        }
        
        // 4. Generate email ID
        val emailId = "email_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
        
        try {
            // 5. Send email via email service
            val deliveryResult = emailService.sendWelcomeEmail(user, request.apiKey)
            
            if (!deliveryResult.success) {
                logger.error("SEND_WELCOME_EMAIL_DELIVERY_FAILED: Email delivery failed [userId=${request.userId}, error=${deliveryResult.error}]")
                return SendWelcomeEmailResponse(
                    success = false,
                    message = "Email delivery failed: ${deliveryResult.error}",
                    emailId = emailId,
                    alreadySent = false
                )
            }
            
            // 6. Create email log
            val emailLog = EmailLog(
                id = emailId,
                userId = request.userId,
                emailType = EmailType.WELCOME,
                subject = "Welcome to Screenshot API! Your ${user.creditsRemaining} free credits are ready 🚀",
                recipientEmail = user.email,
                sentAt = Clock.System.now(),
                metadata = mapOf(
                    "apiKey" to request.apiKey,
                    "userEmail" to user.email,
                    "planName" to user.planName,
                    "creditsRemaining" to user.creditsRemaining.toString(),
                    "providerMessageId" to (deliveryResult.providerMessageId ?: ""),
                    "messageId" to (deliveryResult.messageId ?: "")
                ),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            // 7. Save email log
            val savedEmailLog = emailLogRepository.save(emailLog)
            
            // 8. Log usage for audit trail
            logUsageUseCase(LogUsageUseCase.Request(
                userId = request.userId,
                action = dev.screenshotapi.core.domain.entities.UsageLogAction.EMAIL_SENT,
                metadata = mapOf(
                    "emailType" to EmailType.WELCOME.name,
                    "emailId" to emailId,
                    "recipientEmail" to user.email,
                    "subject" to emailLog.subject
                )
            ))
            
            logger.info("SEND_WELCOME_EMAIL_SUCCESS: Welcome email sent successfully [userId=${request.userId}, emailId=$emailId, messageId=${deliveryResult.messageId}]")
            
            return SendWelcomeEmailResponse(
                success = true,
                message = "Welcome email sent successfully",
                emailId = emailId,
                alreadySent = false
            )
            
        } catch (e: Exception) {
            logger.error("SEND_WELCOME_EMAIL_ERROR: Exception sending welcome email [userId=${request.userId}, emailId=$emailId]", e)
            
            // Try to save error log
            try {
                val errorEmailLog = EmailLog(
                    id = emailId,
                    userId = request.userId,
                    emailType = EmailType.WELCOME,
                    subject = "Welcome to Screenshot API! Your ${user.creditsRemaining} free credits are ready 🚀",
                    recipientEmail = user.email,
                    sentAt = Clock.System.now(),
                    metadata = mapOf(
                        "apiKey" to request.apiKey,
                        "userEmail" to user.email,
                        "error" to e.message.orEmpty(),
                        "errorType" to e.javaClass.simpleName
                    ),
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
                emailLogRepository.save(errorEmailLog)
            } catch (logError: Exception) {
                logger.error("SEND_WELCOME_EMAIL_LOG_ERROR: Failed to save error log [userId=${request.userId}]", logError)
            }
            
            return SendWelcomeEmailResponse(
                success = false,
                message = "Failed to send welcome email: ${e.message}",
                emailId = emailId,
                alreadySent = false
            )
        }
    }
}

data class SendWelcomeEmailRequest(
    val userId: String,
    val apiKey: String
)

data class SendWelcomeEmailResponse(
    val success: Boolean,
    val message: String,
    val emailId: String,
    val alreadySent: Boolean
)