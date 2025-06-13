package dev.screenshotapi.core.usecases.admin

import dev.screenshotapi.core.domain.entities.SubscriptionStatus
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.repositories.PlanRepository
import dev.screenshotapi.core.domain.repositories.SubscriptionRepository
import dev.screenshotapi.core.domain.repositories.UserRepository
import dev.screenshotapi.core.usecases.billing.AddCreditsUseCase
import dev.screenshotapi.core.usecases.billing.AddCreditsRequest
import dev.screenshotapi.core.usecases.common.UseCase
import dev.screenshotapi.core.usecases.logging.LogUsageUseCase
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Admin use case for manually provisioning credits for existing subscriptions.
 * 
 * This use case addresses scenarios where admin needs to manually add credits:
 * - Customer support requests
 * - Bonus credit allocations
 * - Manual corrections and adjustments
 * - Promotional credit additions
 * 
 * Key Differences from Automatic Provisioning:
 * - NO idempotency checks - always adds credits when requested by admin
 * - Uses AddCreditsUseCase directly to bypass automatic provisioning logic
 * - Always adds the full plan amount regardless of current balance
 * - Designed for intentional admin actions
 * 
 * Architecture:
 * - Follows hexagonal architecture principles
 * - Reuses AddCreditsUseCase (composition over duplication)
 * - Maintains audit trail with admin user tracking
 * - Uses domain repositories as output ports
 * - Simple validation: subscription must be active
 * 
 * Security:
 * - Restricted to admin users only (enforced at controller layer)
 * - Logs admin user ID for complete audit trail
 * - Full audit trail for compliance
 */
class ProvisionSubscriptionCreditsAdminUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val addCreditsUseCase: AddCreditsUseCase,
    private val logUsageUseCase: LogUsageUseCase
) : UseCase<ProvisionSubscriptionCreditsAdminRequest, ProvisionSubscriptionCreditsAdminResponse> {

    private val logger = LoggerFactory.getLogger(ProvisionSubscriptionCreditsAdminUseCase::class.java)

    override suspend operator fun invoke(request: ProvisionSubscriptionCreditsAdminRequest): ProvisionSubscriptionCreditsAdminResponse {
        logger.info("ADMIN_PROVISION_CREDITS_START: Admin manually provisioning credits [subscriptionId=${request.subscriptionId}, adminUserId=${request.adminUserId}, reason=${request.reason}]")
        
        // 1. Verify subscription exists and is active
        val subscription = subscriptionRepository.findById(request.subscriptionId)
            ?: run {
                logger.error("ADMIN_PROVISION_CREDITS_SUBSCRIPTION_NOT_FOUND: Subscription not found [subscriptionId=${request.subscriptionId}, adminUserId=${request.adminUserId}]")
                throw ResourceNotFoundException("Subscription not found with id: ${request.subscriptionId}")
            }
        
        logger.info("ADMIN_PROVISION_CREDITS_SUBSCRIPTION_FOUND: Subscription found [subscriptionId=${request.subscriptionId}, userId=${subscription.userId}, planId=${subscription.planId}, status=${subscription.status}]")
        
        // Business rule: Only provision credits for active subscriptions
        if (subscription.status != SubscriptionStatus.ACTIVE) {
            logger.warn("ADMIN_PROVISION_CREDITS_INACTIVE_SUBSCRIPTION: Subscription is not active [subscriptionId=${request.subscriptionId}, status=${subscription.status}, adminUserId=${request.adminUserId}]")
            return ProvisionSubscriptionCreditsAdminResponse(
                subscriptionId = request.subscriptionId,
                userId = subscription.userId,
                planId = subscription.planId,
                creditsProvisioned = 0,
                newCreditBalance = 0,
                userPlanUpdated = false,
                processed = false,
                message = "Cannot provision credits: subscription is not active (${subscription.status})",
                adminUserId = request.adminUserId,
                executedAt = Clock.System.now()
            )
        }
        
        // 2. Get user and plan details
        val user = userRepository.findById(subscription.userId)
            ?: run {
                logger.error("ADMIN_PROVISION_CREDITS_USER_NOT_FOUND: User not found [userId=${subscription.userId}, adminUserId=${request.adminUserId}]")
                throw ResourceNotFoundException("User not found with id: ${subscription.userId}")
            }
        
        val plan = planRepository.findById(subscription.planId)
            ?: run {
                logger.error("ADMIN_PROVISION_CREDITS_PLAN_NOT_FOUND: Plan not found [planId=${subscription.planId}, adminUserId=${request.adminUserId}]")
                throw ResourceNotFoundException("Plan not found with id: ${subscription.planId}")
            }
        
        logger.info("ADMIN_PROVISION_CREDITS_ENTITIES_LOADED: User and plan loaded [userId=${user.id}, userEmail=${user.email}, planName=${plan.name}, creditsPerMonth=${plan.creditsPerMonth}, currentCredits=${user.creditsRemaining}]")
        
        // 3. Calculate credits to provision (always the full plan amount for admin operations)
        val creditsToProvision = plan.creditsPerMonth
        
        logger.info("ADMIN_PROVISION_CREDITS_CALCULATED: Credits to provision [creditsToProvision=$creditsToProvision, currentUserCredits=${user.creditsRemaining}]")
        
        // 4. Add credits using AddCreditsUseCase directly (NO idempotency checks - admin action is intentional)
        val addCreditsRequest = AddCreditsRequest(
            userId = subscription.userId,
            amount = creditsToProvision,
            transactionId = "admin_provision_${request.subscriptionId}_${Clock.System.now().epochSeconds}_${request.adminUserId}"
        )
        
        logger.info("ADMIN_PROVISION_CREDITS_ADDING_CREDITS: Adding credits to user [userId=${subscription.userId}, amount=$creditsToProvision, currentBalance=${user.creditsRemaining}, adminUserId=${request.adminUserId}]")
        
        val addCreditsResponse = try {
            addCreditsUseCase(addCreditsRequest)
        } catch (e: Exception) {
            logger.error("ADMIN_PROVISION_CREDITS_ADD_CREDITS_FAILED: Failed to add credits [userId=${subscription.userId}, amount=$creditsToProvision, adminUserId=${request.adminUserId}, error=${e.message}]", e)
            throw e
        }
        
        logger.info("ADMIN_PROVISION_CREDITS_CREDITS_ADDED: Credits added successfully by admin [userId=${subscription.userId}, creditsAdded=${addCreditsResponse.creditsAdded}, newBalance=${addCreditsResponse.newCreditBalance}, adminUserId=${request.adminUserId}]")
        
        // 5. Log the admin provisioning event for audit trail
        try {
            logUsageUseCase(LogUsageUseCase.Request(
                userId = subscription.userId,
                action = dev.screenshotapi.core.domain.entities.UsageLogAction.CREDITS_ADDED,
                creditsUsed = 0, // Credits were added, not used
                metadata = mapOf(
                    "reason" to request.reason,
                    "subscriptionId" to request.subscriptionId,
                    "adminUserId" to request.adminUserId,
                    "creditsProvisioned" to creditsToProvision.toString(),
                    "provisionType" to "admin_manual",
                    "planId" to subscription.planId,
                    "executedAt" to Clock.System.now().toString()
                )
            ))
        } catch (e: Exception) {
            logger.warn("ADMIN_PROVISION_CREDITS_LOGGING_FAILED: Failed to log usage event [userId=${subscription.userId}, adminUserId=${request.adminUserId}, error=${e.message}]", e)
            // Don't fail the operation if logging fails
        }
        
        logger.info("ADMIN_PROVISION_CREDITS_SUCCESS: Admin credit provisioning completed [subscriptionId=${request.subscriptionId}, userId=${subscription.userId}, creditsProvisioned=$creditsToProvision, newBalance=${addCreditsResponse.newCreditBalance}, adminUserId=${request.adminUserId}]")
        
        return ProvisionSubscriptionCreditsAdminResponse(
            subscriptionId = request.subscriptionId,
            userId = subscription.userId,
            planId = subscription.planId,
            creditsProvisioned = addCreditsResponse.creditsAdded,
            newCreditBalance = addCreditsResponse.newCreditBalance,
            userPlanUpdated = false, // Admin provision doesn't change user plan
            processed = true,
            message = "Credits successfully provisioned by admin ${request.adminUserId}",
            adminUserId = request.adminUserId,
            executedAt = Clock.System.now()
        )
    }
}