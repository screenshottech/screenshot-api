package dev.screenshotapi.infrastructure.services

import dev.screenshotapi.core.domain.entities.EmailType
import dev.screenshotapi.core.domain.entities.User
import dev.screenshotapi.core.domain.services.EmailProvider
import dev.screenshotapi.core.domain.services.EmailDeliveryResult
import dev.screenshotapi.infrastructure.config.EmailConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class EmailService(
    private val emailProvider: EmailProvider,
    private val config: EmailConfig
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    private val templateCache = ConcurrentHashMap<String, String>()
    
    private fun createBrandingData(): Map<String, Any> = mapOf(
        "appName" to config.appName,
        "appLogo" to config.appLogo,
        "teamName" to config.teamName,
        "apiBaseUrl" to config.apiBaseUrl,
        "dashboardUrl" to config.dashboardUrl,
        "docsUrl" to config.docsUrl,
        "upgradeUrl" to config.upgradeUrl,
        "supportEmail" to config.replyTo
    )
    
    private suspend fun sendEmailWithLogging(
        user: User,
        subject: String,
        htmlContent: String,
        templateName: String,
        templateData: Map<String, Any>,
        additionalContext: String = ""
    ): EmailDeliveryResult {
        return try {
            val result = emailProvider.sendEmail(
                to = user.email,
                subject = subject,
                htmlContent = htmlContent,
                textContent = ""
            )
            
            if (result.success) {
                logger.info("EMAIL_SUCCESS: $templateName email sent [userId=${user.id}, messageId=${result.messageId}]$additionalContext")
            } else {
                logger.error("EMAIL_FAILED: $templateName email failed [userId=${user.id}, error=${result.error}]$additionalContext")
            }
            
            result
        } catch (e: Exception) {
            logger.error("EMAIL_ERROR: $templateName email exception [userId=${user.id}]$additionalContext", e)
            EmailDeliveryResult.failure("Exception sending $templateName email: ${e.message}")
        }
    }
    
    suspend fun sendWelcomeEmail(
        user: User,
        apiKey: String
    ): EmailDeliveryResult {
        
        val templateData = createBrandingData() + mapOf(
            "userName" to (user.name ?: "Developer"),
            "userEmail" to user.email,
            "apiKey" to apiKey,
            "creditsRemaining" to user.creditsRemaining.toString(),
            "planName" to user.planName
        )
        
        val subject = "Welcome to ${config.appName}! Your ${user.creditsRemaining} free credits are ready 🚀"
        val htmlContent = renderTemplate("welcome", templateData)
        
        return sendEmailWithLogging(user, subject, htmlContent, "welcome", templateData)
    }
    
    suspend fun sendFirstScreenshotEmail(
        user: User,
        screenshotUrl: String,
        processingTimeMs: Long
    ): EmailDeliveryResult {
        
        val templateData = createBrandingData() + mapOf(
            "userName" to (user.name ?: "Developer"),
            "screenshotUrl" to screenshotUrl,
            "processingTime" to processingTimeMs.toString(),
            "creditsUsed" to "1",
            "creditsRemaining" to user.creditsRemaining.toString()
        )
        
        val subject = "🎉 Your first screenshot is ready! What's next?"
        val htmlContent = renderTemplate("first-screenshot", templateData)
        
        return sendEmailWithLogging(user, subject, htmlContent, "first-screenshot", templateData)
    }
    
    suspend fun sendCreditAlertEmail(
        user: User,
        usagePercent: Int,
        creditsUsed: Int,
        creditsTotal: Int,
        resetDate: String
    ): EmailDeliveryResult {
        
        val templateName = "credit-alert-$usagePercent"
        val templateData = createBrandingData() + mapOf(
            "userName" to (user.name ?: "Developer"),
            "usagePercent" to usagePercent.toString(),
            "creditsUsed" to creditsUsed.toString(),
            "creditsTotal" to creditsTotal.toString(),
            "creditsRemaining" to (creditsTotal - creditsUsed).toString(),
            "resetDate" to resetDate,
            "planName" to user.planName
        )
        
        val subject = when (usagePercent) {
            50 -> "You're halfway through your monthly credits - great progress!"
            80 -> "⚠️ 80% of credits used - consider upgrading to avoid interruption"
            90 -> "🚨 URGENT: Only ${creditsTotal - creditsUsed} credits remaining"
            else -> "Credit usage alert - ${usagePercent}% used"
        }
        
        val htmlContent = renderTemplate(templateName, templateData)
        
        return sendEmailWithLogging(user, subject, htmlContent, templateName, templateData, "percent=$usagePercent")
    }
    
    suspend fun sendFirstMonthTransitionEmail(
        user: User,
        totalCreditsUsed: Int,
        newCreditLimit: Int,
        resetDate: String
    ): EmailDeliveryResult {
        
        val templateData = createBrandingData() + mapOf(
            "userName" to (user.name ?: "Developer"),
            "totalCreditsUsed" to totalCreditsUsed.toString(),
            "newCreditLimit" to newCreditLimit.toString(),
            "resetDate" to resetDate,
            "planName" to user.planName
        )
        
        val subject = "Important: Your free plan has changed to $newCreditLimit credits/month"
        val htmlContent = renderTemplate("first-month-transition", templateData)
        
        return sendEmailWithLogging(user, subject, htmlContent, "first-month-transition", templateData)
    }
    
    suspend fun sendUpgradeCampaignEmail(
        user: User,
        recommendedPlan: String,
        usageStats: Map<String, Any>,
        savings: String
    ): EmailDeliveryResult {
        
        val templateData = createBrandingData() + mapOf(
            "userName" to (user.name ?: "Developer"),
            "recommendedPlan" to recommendedPlan,
            "currentPlan" to user.planName,
            "savings" to savings
        ) + usageStats
        
        val subject = "Based on your usage, here's the perfect plan for you"
        val htmlContent = renderTemplate("upgrade-campaign", templateData)
        
        return sendEmailWithLogging(user, subject, htmlContent, "upgrade-campaign", templateData, "plan=$recommendedPlan")
    }
    
    private fun renderTemplate(templateName: String, data: Map<String, Any>): String {
        val cacheKey = "$templateName.html"
        val template = templateCache.computeIfAbsent(cacheKey) {
            loadTemplate("$templateName.html")
        }
        
        return template.replace(Regex("\\{\\{(\\w+)\\}\\}")) { matchResult ->
            val key = matchResult.groupValues[1]
            data[key]?.toString() ?: matchResult.value
        }
    }
    
    private fun renderTextTemplate(templateName: String, data: Map<String, Any>): String {
        val cacheKey = "$templateName.txt"
        val template = templateCache.computeIfAbsent(cacheKey) {
            loadTemplate("$templateName.txt")
        }
        
        return template.replace(Regex("\\{\\{(\\w+)\\}\\}")) { matchResult ->
            val key = matchResult.groupValues[1]
            data[key]?.toString() ?: matchResult.value
        }
    }
    
    private fun loadTemplate(fileName: String): String {
        val templatePath = "${config.templatePath}/$fileName"
        val templateFile = File(templatePath)
        
        return if (templateFile.exists()) {
            templateFile.readText()
        } else {
            logger.warn("EMAIL_TEMPLATE_NOT_FOUND: Template file not found [path=$templatePath]")
            getDefaultTemplate(fileName)
        }
    }
    
    private fun getDefaultTemplate(fileName: String): String {
        return when {
            fileName.startsWith("welcome") -> """
                <html>
                <body>
                    <h1>Welcome to Screenshot API!</h1>
                    <p>Hello {{userName}},</p>
                    <p>Welcome to Screenshot API! Your {{creditsRemaining}} free credits are ready.</p>
                    <p>Your API Key: {{apiKey}}</p>
                    <p>Get started: <a href="{{dashboardUrl}}">Dashboard</a></p>
                </body>
                </html>
            """.trimIndent()
            
            fileName.startsWith("first-screenshot") -> """
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <h1 style="color: #2563eb; margin-bottom: 10px;">🎉 Your First Screenshot is Ready!</h1>
                        <p style="color: #64748b; font-size: 18px;">Congratulations, {{userName}}!</p>
                    </div>
                    
                    <div style="background: #f8fafc; border-radius: 8px; padding: 25px; margin: 25px 0;">
                        <h2 style="color: #334155; margin-top: 0;">📸 Screenshot Details</h2>
                        <div style="margin-bottom: 15px;">
                            <a href="{{screenshotUrl}}" style="display: inline-block; background: #2563eb; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold;">View Your Screenshot</a>
                        </div>
                        
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 20px;">
                            <div>
                                <h4 style="margin: 0 0 5px 0; color: #475569;">Credits Used</h4>
                                <p style="margin: 0; font-size: 18px; font-weight: bold; color: #dc2626;">{{creditsUsed}}</p>
                            </div>
                            <div>
                                <h4 style="margin: 0 0 5px 0; color: #475569;">Credits Remaining</h4>
                                <p style="margin: 0; font-size: 18px; font-weight: bold; color: #059669;">{{creditsRemaining}}</p>
                            </div>
                        </div>
                        
                        <div style="margin-top: 20px;">
                            <h4 style="margin: 0 0 5px 0; color: #475569;">Processing Time</h4>
                            <p style="margin: 0; font-size: 16px; color: #64748b;">{{processingTime}}ms</p>
                        </div>
                    </div>
                    
                    <div style="background: #eff6ff; border-left: 4px solid #2563eb; padding: 20px; margin: 25px 0;">
                        <h3 style="color: #1e40af; margin-top: 0;">🚀 What's Next?</h3>
                        <div style="color: #475569;">
                            <div style="display: flex; align-items: flex-start; margin-bottom: 15px;">
                                <div style="background: #2563eb; color: white; width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; margin-right: 12px; flex-shrink: 0; margin-top: 2px;">1</div>
                                <div>View your screenshot using the result URL in the API response</div>
                            </div>
                            <div style="display: flex; align-items: flex-start; margin-bottom: 15px;">
                                <div style="background: #2563eb; color: white; width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; margin-right: 12px; flex-shrink: 0; margin-top: 2px;">2</div>
                                <div>Set up webhooks for real-time notifications</div>
                            </div>
                            <div style="display: flex; align-items: flex-start; margin-bottom: 15px;">
                                <div style="background: #2563eb; color: white; width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; margin-right: 12px; flex-shrink: 0; margin-top: 2px;">3</div>
                                <div>Monitor usage in your <a href="{{dashboardUrl}}" style="color: #2563eb; text-decoration: none;">analytics dashboard</a></div>
                            </div>
                            <div style="display: flex; align-items: flex-start; margin-bottom: 0;">
                                <div style="background: #2563eb; color: white; width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; margin-right: 12px; flex-shrink: 0; margin-top: 2px;">4</div>
                                <div>Explore formats - try PDF, WEBP, or different sizes</div>
                            </div>
                        </div>
                    </div>
                    
                    <div style="text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #e2e8f0;">
                        <p style="color: #64748b; margin: 0;">Happy screenshotting!</p>
                        <p style="color: #64748b; margin: 5px 0 0 0;">The {{teamName}} Team</p>
                    </div>
                </body>
                </html>
            """.trimIndent()
            
            fileName.startsWith("credit-alert") -> """
                <html>
                <body>
                    <h1>Credit Usage Alert</h1>
                    <p>Hello {{userName}},</p>
                    <p>You have used {{creditsUsed}} of {{creditsTotal}} credits ({{usagePercent}}%).</p>
                    <p>Credits remaining: {{creditsRemaining}}</p>
                    <p>Reset date: {{resetDate}}</p>
                    <p><a href="{{upgradeUrl}}">Upgrade your plan</a></p>
                </body>
                </html>
            """.trimIndent()
            
            else -> """
                <html>
                <body>
                    <h1>Screenshot API</h1>
                    <p>Hello {{userName}},</p>
                    <p>This is a notification from Screenshot API.</p>
                    <p>Visit your <a href="{{dashboardUrl}}">dashboard</a> for more details.</p>
                </body>
                </html>
            """.trimIndent()
        }
    }
}