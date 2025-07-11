package dev.screenshotapi.core.usecases.email

import dev.screenshotapi.core.domain.entities.EmailLog
import dev.screenshotapi.core.domain.entities.EmailType
import dev.screenshotapi.core.domain.entities.UsageLogAction
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

class SendCreditAlertUseCase(
    private val userRepository: UserRepository,
    private val emailLogRepository: EmailLogRepository,
    private val emailService: EmailService,
    private val logUsageUseCase: LogUsageUseCase
) : UseCase<SendCreditAlertRequest, SendCreditAlertResponse> {

    private val logger = LoggerFactory.getLogger(SendCreditAlertUseCase::class.java)

    override suspend fun invoke(request: SendCreditAlertRequest): SendCreditAlertResponse {
        logger.info("SEND_CREDIT_ALERT_START: Starting credit alert email [userId=${request.userId}, percent=${request.usagePercent}]")

        // 1. Validate input
        if (request.userId.isBlank()) {
            throw ValidationException("User ID is required", "userId")
        }
        if (request.usagePercent !in 1..100) {
            throw ValidationException("Usage percent must be between 1 and 100", "usagePercent")
        }
        if (request.creditsUsed < 0) {
            throw ValidationException("Credits used cannot be negative", "creditsUsed")
        }
        if (request.creditsTotal <= 0) {
            throw ValidationException("Credits total must be positive", "creditsTotal")
        }

        // 2. Load user (validation)
        val user = userRepository.findById(request.userId)
            ?: throw ResourceNotFoundException("User not found: ${request.userId}")

        // 3. Determine email type based on usage percentage
        val emailType = when (request.usagePercent) {
            in 50..59 -> EmailType.CREDIT_ALERT_50
            in 80..89 -> EmailType.CREDIT_ALERT_80
            in 90..100 -> EmailType.CREDIT_ALERT_90
            else -> {
                logger.warn("SEND_CREDIT_ALERT_INVALID_PERCENT: Invalid usage percent [userId=${request.userId}, percent=${request.usagePercent}]")
                throw ValidationException("Invalid usage percent for alert: ${request.usagePercent}", "usagePercent")
            }
        }

        // 4. Check if this type of alert already sent (idempotency)
        val existingEmail = emailLogRepository.findByUserIdAndType(request.userId, emailType)
        if (existingEmail != null) {
            logger.info("SEND_CREDIT_ALERT_ALREADY_SENT: Credit alert already sent [userId=${request.userId}, type=${emailType.name}, emailId=${existingEmail.id}]")
            return SendCreditAlertResponse(
                success = true,
                message = "Credit alert already sent for this threshold",
                emailId = existingEmail.id,
                emailType = emailType,
                alreadySent = true
            )
        }

        // 5. Generate email ID
        val emailId = "email_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"

        try {
            // 6. Send email via email service
            val deliveryResult = emailService.sendCreditAlertEmail(
                user = user,
                usagePercent = request.usagePercent,
                creditsUsed = request.creditsUsed,
                creditsTotal = request.creditsTotal,
                resetDate = request.resetDate
            )

            if (!deliveryResult.success) {
                logger.error("SEND_CREDIT_ALERT_DELIVERY_FAILED: Email delivery failed [userId=${request.userId}, type=${emailType.name}, error=${deliveryResult.error}]")
                return SendCreditAlertResponse(
                    success = false,
                    message = "Email delivery failed: ${deliveryResult.error}",
                    emailId = emailId,
                    emailType = emailType,
                    alreadySent = false
                )
            }

            // 7. Create email log
            val subject = when (emailType) {
                EmailType.CREDIT_ALERT_50 -> "You're halfway through your monthly credits - great progress!"
                EmailType.CREDIT_ALERT_80 -> "⚠️ 80% of credits used - consider upgrading to avoid interruption"
                EmailType.CREDIT_ALERT_90 -> "🚨 URGENT: Only ${request.creditsTotal - request.creditsUsed} credits remaining"
                else -> "Credit usage alert - ${request.usagePercent}% used"
            }

            val emailLog = EmailLog(
                id = emailId,
                userId = request.userId,
                emailType = emailType,
                subject = subject,
                recipientEmail = user.email,
                sentAt = Clock.System.now(),
                metadata = mapOf(
                    "usagePercent" to request.usagePercent.toString(),
                    "creditsUsed" to request.creditsUsed.toString(),
                    "creditsTotal" to request.creditsTotal.toString(),
                    "creditsRemaining" to (request.creditsTotal - request.creditsUsed).toString(),
                    "resetDate" to request.resetDate,
                    "planName" to user.planName,
                    "providerMessageId" to (deliveryResult.providerMessageId ?: ""),
                    "messageId" to (deliveryResult.messageId ?: "")
                ),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )

            // 8. Save email log
            val savedEmailLog = emailLogRepository.save(emailLog)

            // 9. Log usage for audit trail
            logUsageUseCase(LogUsageUseCase.Request(
                userId = request.userId,
                action = UsageLogAction.EMAIL_SENT,
                metadata = mapOf(
                    "emailType" to emailType.name,
                    "emailId" to emailId,
                    "recipientEmail" to user.email,
                    "subject" to subject,
                    "usagePercent" to request.usagePercent.toString(),
                    "alertThreshold" to when (emailType) {
                        EmailType.CREDIT_ALERT_50 -> "50"
                        EmailType.CREDIT_ALERT_80 -> "80"
                        EmailType.CREDIT_ALERT_90 -> "90"
                        else -> "unknown"
                    }
                )
            ))

            logger.info("SEND_CREDIT_ALERT_SUCCESS: Credit alert email sent successfully [userId=${request.userId}, type=${emailType.name}, emailId=$emailId, messageId=${deliveryResult.messageId}]")

            return SendCreditAlertResponse(
                success = true,
                message = "Credit alert email sent successfully",
                emailId = emailId,
                emailType = emailType,
                alreadySent = false
            )

        } catch (e: Exception) {
            logger.error("SEND_CREDIT_ALERT_ERROR: Exception sending credit alert email [userId=${request.userId}, type=${emailType.name}, emailId=$emailId]", e)

            // Try to save error log
            try {
                val errorEmailLog = EmailLog(
                    id = emailId,
                    userId = request.userId,
                    emailType = emailType,
                    subject = "Credit Alert - ${request.usagePercent}% used",
                    recipientEmail = user.email,
                    sentAt = Clock.System.now(),
                    metadata = mapOf(
                        "usagePercent" to request.usagePercent.toString(),
                        "creditsUsed" to request.creditsUsed.toString(),
                        "creditsTotal" to request.creditsTotal.toString(),
                        "error" to e.message.orEmpty(),
                        "errorType" to e.javaClass.simpleName
                    ),
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
                emailLogRepository.save(errorEmailLog)
            } catch (logError: Exception) {
                logger.error("SEND_CREDIT_ALERT_LOG_ERROR: Failed to save error log [userId=${request.userId}]", logError)
            }

            return SendCreditAlertResponse(
                success = false,
                message = "Failed to send credit alert email: ${e.message}",
                emailId = emailId,
                emailType = emailType,
                alreadySent = false
            )
        }
    }
}

data class SendCreditAlertRequest(
    val userId: String,
    val usagePercent: Int,
    val creditsUsed: Int,
    val creditsTotal: Int,
    val resetDate: String
)

data class SendCreditAlertResponse(
    val success: Boolean,
    val message: String,
    val emailId: String,
    val emailType: EmailType,
    val alreadySent: Boolean
)
