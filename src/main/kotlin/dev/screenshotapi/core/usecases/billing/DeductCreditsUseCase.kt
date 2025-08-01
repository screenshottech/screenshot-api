package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.domain.entities.UsageLogAction
import dev.screenshotapi.core.domain.entities.CreditDeductionReason
import dev.screenshotapi.core.usecases.email.SendCreditAlertUseCase
import dev.screenshotapi.core.usecases.email.SendCreditAlertRequest
import kotlinx.datetime.*
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days

class DeductCreditsUseCase(
    private val userRepository: UserRepository,
    private val logUsageUseCase: LogUsageUseCase,
    private val sendCreditAlertUseCase: SendCreditAlertUseCase? = null
) {

    private val logger = LoggerFactory.getLogger(DeductCreditsUseCase::class.java)
    suspend operator fun invoke(request: DeductCreditsRequest): DeductCreditsResponse {
        // Get user to check current credits
        val user = userRepository.findById(request.userId)
            ?: throw IllegalArgumentException("User not found: ${request.userId}")

        // Check if user has sufficient credits
        if (user.creditsRemaining < request.amount) {
            throw IllegalStateException("Insufficient credits. Required: ${request.amount}, Available: ${user.creditsRemaining}")
        }

        // Deduct credits from user
        val updatedUser = user.copy(
            creditsRemaining = user.creditsRemaining - request.amount,
            updatedAt = Clock.System.now()
        )
        userRepository.update(updatedUser)

        val deductedAt = Clock.System.now()

        // Determine the appropriate log action based on deduction reason
        val logAction = when (request.reason) {
            CreditDeductionReason.AI_ANALYSIS -> UsageLogAction.AI_ANALYSIS_CREDITS_DEDUCTED
            else -> UsageLogAction.CREDITS_DEDUCTED
        }
        
        // Log the credit deduction in usage logs
        logUsageUseCase.invoke(LogUsageUseCase.Request(
            userId = request.userId,
            action = logAction,
            creditsUsed = request.amount,
            screenshotId = null, // Only for Screenshots table IDs, not AnalysisJobs or other job types
            metadata = mapOf(
                "previousCredits" to user.creditsRemaining.toString(),
                "newCredits" to updatedUser.creditsRemaining.toString(),
                "deductedAmount" to request.amount.toString()
            ).plus(
                // Add job context in metadata instead of screenshotId field
                request.jobId?.let { mapOf("jobId" to it) } ?: emptyMap()
            ).plus(
                // Add optional business context
                request.reason?.let {
                    mapOf(
                        "reason" to it.name,
                        "reasonDisplay" to it.displayName,
                        "reasonDescription" to it.description
                    )
                } ?: emptyMap()
            ),
            timestamp = deductedAt
        ))

        // Check for credit alert triggers if email service is available
        if (sendCreditAlertUseCase != null) {
            try {
                // Calculate usage percentage for the user's plan
                val planCredits = user.creditsRemaining + request.amount // Original credits
                val usagePercent = if (planCredits > 0) {
                    ((planCredits - updatedUser.creditsRemaining).toDouble() / planCredits * 100).toInt()
                } else {
                    100
                }

                logger.debug("CREDIT_ALERT_CHECK: Usage percentage check [userId=${request.userId}, usagePercent=$usagePercent, creditsRemaining=${updatedUser.creditsRemaining}]")

                // Determine if we should send alert based on percentage thresholds
                val shouldSendAlert = when {
                    usagePercent >= 90 -> true
                    usagePercent >= 80 -> true
                    usagePercent >= 50 -> true
                    else -> false
                }

                if (shouldSendAlert) {
                    logger.info("CREDIT_ALERT_TRIGGER: Triggering credit alert [userId=${request.userId}, usagePercent=$usagePercent]")

                    // Calculate reset date (placeholder - in production this would be based on billing cycle)
                    val futureDate = Clock.System.now().plus(30.days)
                    val resetDate = try {
                        @OptIn(FormatStringsInDatetimeFormats::class)
                        val formatter = LocalDateTime.Format { byUnicodePattern("MMM dd, yyyy") }
                        futureDate.toLocalDateTime(TimeZone.UTC).format(formatter)
                    } catch (e: Exception) {
                        // Fallback to simple date format
                        val localDate = futureDate.toLocalDateTime(TimeZone.UTC).date
                        "${localDate.month.name.take(3)} ${localDate.dayOfMonth}, ${localDate.year}"
                    }

                    sendCreditAlertUseCase.invoke(SendCreditAlertRequest(
                        userId = request.userId,
                        usagePercent = usagePercent,
                        creditsUsed = planCredits - updatedUser.creditsRemaining,
                        creditsTotal = planCredits,
                        resetDate = resetDate
                    ))

                    logger.info("CREDIT_ALERT_SUCCESS: Credit alert triggered successfully [userId=${request.userId}, usagePercent=$usagePercent]")
                }

            } catch (e: Exception) {
                logger.error("CREDIT_ALERT_FAILED: Failed to send credit alert [userId=${request.userId}]", e)
                // Don't fail credit deduction if email fails
            }
        } else {
            logger.debug("CREDIT_ALERT_DISABLED: Credit alert service not available [userId=${request.userId}]")
        }

        return DeductCreditsResponse(
            userId = request.userId,
            creditsDeducted = request.amount,
            creditsRemaining = updatedUser.creditsRemaining,
            deductedAt = deductedAt
        )
    }
}
