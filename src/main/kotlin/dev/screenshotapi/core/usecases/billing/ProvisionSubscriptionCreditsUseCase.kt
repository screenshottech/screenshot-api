package dev.screenshotapi.core.usecases.billing

import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.ValidationException
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.common.UseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Request model for provisioning subscription credits
 */
data class ProvisionSubscriptionCreditsRequest(
    val subscriptionId: String,
    val reason: String = "subscription_created"
)

/**
 * Response model for credit provisioning
 */
data class ProvisionSubscriptionCreditsResponse(
    val subscriptionId: String,
    val userId: String,
    val planId: String,
    val creditsProvisioned: Int,
    val newCreditBalance: Int,
    val userPlanUpdated: Boolean,
    val processed: Boolean,
    val message: String
)

/**
 * Use case for provisioning credits when subscription is created/updated.
 * 
 * Business Rules:
 * 1. Only active subscriptions get credits
 * 2. User plan is synchronized with subscription plan
 * 3. Credits are calculated based on billing cycle (monthly/annual)
 * 4. All operations are logged for audit trail
 * 5. Operations are idempotent (safe to retry)
 * 
 * Hexagonal Architecture:
 * - Uses domain repositories (output ports)
 * - Contains business logic in domain layer
 * - Orchestrates multiple operations atomically
 * - Logs events for observability
 */
class ProvisionSubscriptionCreditsUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val addCreditsUseCase: AddCreditsUseCase,
    private val logUsageUseCase: LogUsageUseCase
) : UseCase<ProvisionSubscriptionCreditsRequest, ProvisionSubscriptionCreditsResponse> {

    private val logger = LoggerFactory.getLogger(ProvisionSubscriptionCreditsUseCase::class.java)

    override suspend operator fun invoke(request: ProvisionSubscriptionCreditsRequest): ProvisionSubscriptionCreditsResponse {
        logger.info("PROVISION_CREDITS_START: Starting credit provisioning [subscriptionId=${request.subscriptionId}, reason=${request.reason}]")
        
        // 1. Verify subscription exists and is active
        val subscription = subscriptionRepository.findById(request.subscriptionId)
            ?: run {
                logger.error("PROVISION_CREDITS_SUBSCRIPTION_NOT_FOUND: Subscription not found [subscriptionId=${request.subscriptionId}]")
                throw ResourceNotFoundException("Subscription not found with id: ${request.subscriptionId}")
            }
        
        logger.info("PROVISION_CREDITS_SUBSCRIPTION_FOUND: Subscription found [subscriptionId=${request.subscriptionId}, userId=${subscription.userId}, planId=${subscription.planId}, status=${subscription.status}]")
        
        // Business rule: Only provision credits for active subscriptions
        if (subscription.status != SubscriptionStatus.ACTIVE) {
            logger.warn("PROVISION_CREDITS_INACTIVE_SUBSCRIPTION: Subscription is not active [subscriptionId=${request.subscriptionId}, status=${subscription.status}]")
            return ProvisionSubscriptionCreditsResponse(
                subscriptionId = request.subscriptionId,
                userId = subscription.userId,
                planId = subscription.planId,
                creditsProvisioned = 0,
                newCreditBalance = 0,
                userPlanUpdated = false,
                processed = false,
                message = "Subscription is not active: ${subscription.status}"
            )
        }
        
        // 2. Get user and plan details
        val user = userRepository.findById(subscription.userId)
            ?: run {
                logger.error("PROVISION_CREDITS_USER_NOT_FOUND: User not found [userId=${subscription.userId}]")
                throw ResourceNotFoundException("User not found with id: ${subscription.userId}")
            }
        
        val plan = planRepository.findById(subscription.planId)
            ?: run {
                logger.error("PROVISION_CREDITS_PLAN_NOT_FOUND: Plan not found [planId=${subscription.planId}]")
                throw ResourceNotFoundException("Plan not found with id: ${subscription.planId}")
            }
        
        logger.info("PROVISION_CREDITS_ENTITIES_LOADED: User and plan loaded [userId=${user.id}, userEmail=${user.email}, planName=${plan.name}, creditsPerMonth=${plan.creditsPerMonth}]")
        
        // 3. Calculate credits to provision based on billing cycle
        val creditsToProvision = subscription.getTotalCreditsFromPlan(plan)
        
        logger.info("PROVISION_CREDITS_CALCULATED: Credits calculated [billingCycle=${subscription.billingCycle}, creditsToProvision=$creditsToProvision]")
        
        // 4. Check if user already has these credits (idempotency)
        val expectedCreditBalance = creditsToProvision
        if (user.planId == subscription.planId && user.creditsRemaining >= expectedCreditBalance) {
            logger.info("PROVISION_CREDITS_ALREADY_PROVISIONED: Credits already provisioned [userId=${user.id}, currentCredits=${user.creditsRemaining}, expectedCredits=$expectedCreditBalance]")
            return ProvisionSubscriptionCreditsResponse(
                subscriptionId = request.subscriptionId,
                userId = subscription.userId,
                planId = subscription.planId,
                creditsProvisioned = 0,
                newCreditBalance = user.creditsRemaining,
                userPlanUpdated = false,
                processed = true,
                message = "Credits already provisioned"
            )
        }
        
        // 5. Update user plan if different (synchronize subscription plan with user plan)
        var userPlanUpdated = false
        var updatedUser = user
        
        if (user.planId != subscription.planId) {
            logger.info("PROVISION_CREDITS_UPDATING_USER_PLAN: Updating user plan [userId=${user.id}, currentPlan=${user.planId}, newPlan=${subscription.planId}]")
            
            updatedUser = user.copy(
                planId = subscription.planId,
                planName = plan.name,
                updatedAt = Clock.System.now()
            )
            
            try {
                updatedUser = userRepository.update(updatedUser)
                userPlanUpdated = true
                logger.info("PROVISION_CREDITS_USER_PLAN_UPDATED: User plan updated successfully [userId=${user.id}, newPlan=${subscription.planId}]")
            } catch (e: Exception) {
                logger.error("PROVISION_CREDITS_USER_PLAN_UPDATE_FAILED: Failed to update user plan [userId=${user.id}, error=${e.message}]", e)
                throw e
            }
        }
        
        // 6. Add credits using existing AddCreditsUseCase (composition over inheritance)
        val addCreditsRequest = AddCreditsRequest(
            userId = subscription.userId,
            amount = creditsToProvision,
            transactionId = "subscription_${request.subscriptionId}_${Clock.System.now().epochSeconds}"
        )
        
        logger.info("PROVISION_CREDITS_ADDING_CREDITS: Adding credits to user [userId=${subscription.userId}, amount=$creditsToProvision]")
        
        val addCreditsResponse = try {
            addCreditsUseCase(addCreditsRequest)
        } catch (e: Exception) {
            logger.error("PROVISION_CREDITS_ADD_CREDITS_FAILED: Failed to add credits [userId=${subscription.userId}, amount=$creditsToProvision, error=${e.message}]", e)
            throw e
        }
        
        logger.info("PROVISION_CREDITS_CREDITS_ADDED: Credits added successfully [userId=${subscription.userId}, creditsAdded=${addCreditsResponse.creditsAdded}, newBalance=${addCreditsResponse.newCreditBalance}]")
        
        // 7. Log the provisioning event for audit trail
        try {
            logUsageUseCase(LogUsageUseCase.Request(
                userId = subscription.userId,
                action = dev.screenshotapi.core.domain.entities.UsageLogAction.CREDITS_ADDED,
                creditsUsed = 0, // Credits were added, not used
                metadata = mapOf(
                    "reason" to request.reason,
                    "subscriptionId" to request.subscriptionId,
                    "planId" to subscription.planId,
                    "billingCycle" to subscription.billingCycle.name,
                    "creditsProvisioned" to creditsToProvision.toString(),
                    "transactionId" to addCreditsRequest.transactionId
                )
            ))
            
            logger.info("PROVISION_CREDITS_LOGGED: Credit provisioning logged for audit trail [userId=${subscription.userId}]")
        } catch (e: Exception) {
            // Log but don't fail the entire operation for logging errors
            logger.error("PROVISION_CREDITS_LOGGING_FAILED: Failed to log credit provisioning [userId=${subscription.userId}, error=${e.message}]", e)
        }
        
        logger.info("PROVISION_CREDITS_SUCCESS: Credit provisioning completed successfully [subscriptionId=${request.subscriptionId}, userId=${subscription.userId}, creditsProvisioned=$creditsToProvision, newBalance=${addCreditsResponse.newCreditBalance}]")
        
        return ProvisionSubscriptionCreditsResponse(
            subscriptionId = request.subscriptionId,
            userId = subscription.userId,
            planId = subscription.planId,
            creditsProvisioned = addCreditsResponse.creditsAdded,
            newCreditBalance = addCreditsResponse.newCreditBalance,
            userPlanUpdated = userPlanUpdated,
            processed = true,
            message = "Credits provisioned successfully"
        )
    }
}