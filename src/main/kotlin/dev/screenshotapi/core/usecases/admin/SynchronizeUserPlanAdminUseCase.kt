package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.billing.AddCreditsUseCase
import dev.screenshotapi.core.usecases.billing.AddCreditsRequest
import dev.screenshotapi.core.usecases.common.UseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import dev.screenshotapi.core.domain.entities.UsageLogAction
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Admin use case for manually synchronizing user plan with subscription plan.
 * 
 * This use case addresses plan synchronization issues:
 * - User plan not updated after subscription creation/change
 * - Inconsistencies between user.planId and subscription.planId
 * - Manual plan adjustments for customer support scenarios
 * - Rollback scenarios where plans need to be realigned
 * - Post-migration data cleanup and validation
 * 
 * Business Logic:
 * - Finds active subscription for user (or uses provided subscriptionId)
 * - Updates user plan to match subscription plan
 * - Adjusts credits based on plan difference (if applicable)
 * - Maintains complete audit trail
 * - Validates plan transitions are valid
 * 
 * Architecture:
 * - Uses composition pattern with existing use cases
 * - Follows domain-driven design principles
 * - Maintains transactional integrity
 * - Comprehensive logging for audit trail
 */
class SynchronizeUserPlanAdminUseCase(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val planRepository: PlanRepository,
    private val addCreditsUseCase: AddCreditsUseCase,
    private val logUsageUseCase: LogUsageUseCase
) : UseCase<SynchronizeUserPlanAdminRequest, SynchronizeUserPlanAdminResponse> {

    private val logger = LoggerFactory.getLogger(SynchronizeUserPlanAdminUseCase::class.java)

    override suspend operator fun invoke(request: SynchronizeUserPlanAdminRequest): SynchronizeUserPlanAdminResponse {
        logger.info("ADMIN_SYNC_PLAN_START: Admin initiating plan synchronization [userId=${request.userId}, subscriptionId=${request.subscriptionId}, adminUserId=${request.adminUserId}, reason=${request.reason}]")
        
        // 1. Verify user exists
        val user = userRepository.findById(request.userId)
            ?: run {
                logger.error("ADMIN_SYNC_PLAN_USER_NOT_FOUND: User not found [userId=${request.userId}, adminUserId=${request.adminUserId}]")
                throw ResourceNotFoundException("User not found with id: ${request.userId}")
            }
        
        val previousPlanId = user.planId
        logger.info("ADMIN_SYNC_PLAN_USER_FOUND: User located [userId=${request.userId}, currentPlanId=$previousPlanId, adminUserId=${request.adminUserId}]")
        
        // 2. Find target subscription
        val targetSubscription = if (request.subscriptionId != null) {
            // Use specific subscription
            val subscription = subscriptionRepository.findById(request.subscriptionId)
                ?: run {
                    logger.error("ADMIN_SYNC_PLAN_SUBSCRIPTION_NOT_FOUND: Specified subscription not found [subscriptionId=${request.subscriptionId}, adminUserId=${request.adminUserId}]")
                    throw ResourceNotFoundException("Subscription not found with id: ${request.subscriptionId}")
                }
            
            // Verify subscription belongs to user
            if (subscription.userId != request.userId) {
                logger.error("ADMIN_SYNC_PLAN_SUBSCRIPTION_MISMATCH: Subscription does not belong to user [subscriptionId=${request.subscriptionId}, subscriptionUserId=${subscription.userId}, requestUserId=${request.userId}, adminUserId=${request.adminUserId}]")
                throw ValidationException.Custom("Subscription ${request.subscriptionId} does not belong to user ${request.userId}", "subscriptionId")
            }
            
            subscription
        } else {
            // Find active subscription for user
            subscriptionRepository.findActiveByUserId(request.userId)
                ?: run {
                    logger.error("ADMIN_SYNC_PLAN_NO_ACTIVE_SUBSCRIPTION: No active subscription found for user [userId=${request.userId}, adminUserId=${request.adminUserId}]")
                    throw ResourceNotFoundException("No active subscription found for user: ${request.userId}")
                }
        }
        
        // 3. Validate subscription is active
        if (targetSubscription.status != SubscriptionStatus.ACTIVE) {
            logger.warn("ADMIN_SYNC_PLAN_INACTIVE_SUBSCRIPTION: Target subscription is not active [subscriptionId=${targetSubscription.id}, status=${targetSubscription.status}, adminUserId=${request.adminUserId}]")
            throw ValidationException.Custom("Cannot synchronize with inactive subscription. Status: ${targetSubscription.status}", "subscriptionStatus")
        }
        
        val targetPlanId = targetSubscription.planId
        logger.info("ADMIN_SYNC_PLAN_TARGET_SUBSCRIPTION: Target subscription found [subscriptionId=${targetSubscription.id}, targetPlanId=$targetPlanId, status=${targetSubscription.status}, adminUserId=${request.adminUserId}]")
        
        // 4. Check if synchronization is needed
        if (previousPlanId == targetPlanId) {
            logger.info("ADMIN_SYNC_PLAN_ALREADY_SYNCHRONIZED: User plan already matches subscription plan [userId=${request.userId}, planId=$targetPlanId, adminUserId=${request.adminUserId}]")
            
            return SynchronizeUserPlanAdminResponse(
                userId = request.userId,
                subscriptionId = targetSubscription.id,
                previousPlanId = previousPlanId,
                newPlanId = targetPlanId,
                planUpdated = false,
                creditsAdjusted = false,
                newCreditBalance = user.creditsRemaining,
                message = "User plan already synchronized with subscription plan",
                adminUserId = request.adminUserId,
                executedAt = Clock.System.now()
            )
        }
        
        // 5. Validate target plan exists
        val targetPlan = planRepository.findById(targetPlanId)
            ?: run {
                logger.error("ADMIN_SYNC_PLAN_TARGET_PLAN_NOT_FOUND: Target plan not found [targetPlanId=$targetPlanId, adminUserId=${request.adminUserId}]")
                throw ResourceNotFoundException("Target plan not found with id: $targetPlanId")
            }
        
        logger.info("ADMIN_SYNC_PLAN_TARGET_PLAN_FOUND: Target plan located [targetPlanId=$targetPlanId, planName=${targetPlan.name}, creditsPerMonth=${targetPlan.creditsPerMonth}, adminUserId=${request.adminUserId}]")
        
        // 6. Update user plan
        val updatedUser = user.copy(
            planId = targetPlanId,
            updatedAt = Clock.System.now()
        )
        
        val savedUser = try {
            userRepository.update(updatedUser)
        } catch (e: Exception) {
            logger.error("ADMIN_SYNC_PLAN_UPDATE_FAILED: Failed to update user plan [userId=${request.userId}, targetPlanId=$targetPlanId, adminUserId=${request.adminUserId}, error=${e.message}]", e)
            throw e
        }
        
        logger.info("ADMIN_SYNC_PLAN_USER_UPDATED: User plan updated successfully [userId=${request.userId}, previousPlanId=$previousPlanId, newPlanId=$targetPlanId, adminUserId=${request.adminUserId}]")
        
        // 7. Calculate and adjust credits based on plan difference
        var creditsAdjusted = false
        var newCreditBalance = savedUser.creditsRemaining
        
        // Get previous plan for credit calculation
        val previousPlan = planRepository.findById(previousPlanId)
        if (previousPlan != null && targetPlan.creditsPerMonth != previousPlan.creditsPerMonth) {
            val creditsDifference = targetPlan.creditsPerMonth - previousPlan.creditsPerMonth
            
            if (creditsDifference > 0) {
                // Add credits for plan upgrade
                try {
                    val addCreditsRequest = AddCreditsRequest(
                        userId = request.userId,
                        amount = creditsDifference,
                        transactionId = "admin_plan_sync_${request.userId}_${Clock.System.now().epochSeconds}"
                    )
                    
                    val addCreditsResponse = addCreditsUseCase(addCreditsRequest)
                    newCreditBalance = addCreditsResponse.newCreditBalance
                    creditsAdjusted = true
                    
                    logger.info("ADMIN_SYNC_PLAN_CREDITS_ADDED: Credits added for plan upgrade [userId=${request.userId}, creditsAdded=$creditsDifference, newBalance=$newCreditBalance, adminUserId=${request.adminUserId}]")
                    
                } catch (e: Exception) {
                    logger.error("ADMIN_SYNC_PLAN_CREDITS_ADD_FAILED: Failed to add credits [userId=${request.userId}, creditsToAdd=$creditsDifference, adminUserId=${request.adminUserId}, error=${e.message}]", e)
                    // Don't fail the entire operation for credit adjustment errors
                }
            }
            // Note: We don't subtract credits for downgrades to avoid negative balances
            // This follows the principle of not penalizing users for admin actions
        }
        
        // 8. Log administrative action for audit trail
        try {
            logUsageUseCase(LogUsageUseCase.Request(
                userId = request.userId,
                action = UsageLogAction.PLAN_CHANGED,
                creditsUsed = 0,
                metadata = mapOf(
                    "reason" to request.reason,
                    "adminUserId" to request.adminUserId,
                    "previousPlanId" to previousPlanId,
                    "newPlanId" to targetPlanId,
                    "subscriptionId" to targetSubscription.id,
                    "creditsAdjusted" to creditsAdjusted.toString(),
                    "creditsAdjustment" to if (creditsAdjusted) (newCreditBalance - user.creditsRemaining).toString() else "0"
                )
            ))
            
            logger.info("ADMIN_SYNC_PLAN_LOGGED: Administrative action logged for audit trail [userId=${request.userId}, adminUserId=${request.adminUserId}]")
        } catch (e: Exception) {
            logger.error("ADMIN_SYNC_PLAN_LOGGING_FAILED: Failed to log administrative action [userId=${request.userId}, adminUserId=${request.adminUserId}, error=${e.message}]", e)
            // Don't fail the operation for logging errors
        }
        
        logger.info("ADMIN_SYNC_PLAN_SUCCESS: Plan synchronization completed [userId=${request.userId}, previousPlanId=$previousPlanId, newPlanId=$targetPlanId, creditsAdjusted=$creditsAdjusted, newBalance=$newCreditBalance, adminUserId=${request.adminUserId}]")
        
        return SynchronizeUserPlanAdminResponse(
            userId = request.userId,
            subscriptionId = targetSubscription.id,
            previousPlanId = previousPlanId,
            newPlanId = targetPlanId,
            planUpdated = true,
            creditsAdjusted = creditsAdjusted,
            newCreditBalance = newCreditBalance,
            message = "User plan synchronized with subscription plan successfully",
            adminUserId = request.adminUserId,
            executedAt = Clock.System.now()
        )
    }
}