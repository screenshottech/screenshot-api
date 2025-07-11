package dev.screenshotapi.infrastructure.adapters.output.email

import dev.screenshotapi.core.domain.services.EmailProvider
import dev.screenshotapi.core.domain.services.EmailDeliveryResult
import dev.screenshotapi.core.domain.services.BulkEmailRequest
import dev.screenshotapi.core.domain.services.EmailProviderInfo
import dev.screenshotapi.infrastructure.config.EmailConfig
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Date
import java.util.Properties
import java.util.UUID

class GmailEmailProvider(
    private val config: EmailConfig
) : EmailProvider {
    private val logger = LoggerFactory.getLogger(GmailEmailProvider::class.java)
    
    private val session: Session by lazy {
        val username = config.gmailUsername ?: config.fromAddress
        val password = config.gmailAppPassword ?: config.apiKey
        
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
            put("mail.smtp.ssl.trust", "smtp.gmail.com")
            put("mail.smtp.ssl.protocols", "TLSv1.2")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000") 
            put("mail.smtp.writetimeout", "10000")
            put("mail.debug", "true")
            put("mail.smtp.user", username)
            put("mail.user", username)
        }
        
        
        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })
    }
    
    override suspend fun sendEmail(
        to: String,
        subject: String,
        htmlContent: String,
        textContent: String?
    ): EmailDeliveryResult = withContext(Dispatchers.IO) {
        
        try {
            if (!validateEmailAddress(to)) {
                logger.error("GMAIL_INVALID_EMAIL: Invalid recipient email address: $to")
                return@withContext EmailDeliveryResult.failure("Invalid email address: $to", 400)
            }
            
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.fromAddress, config.fromName))
                
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                
                setReplyTo(InternetAddress.parse(config.replyTo))
                
                setSubject(subject)
                
                val messageId = "gmail_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
                setHeader("Message-ID", "<$messageId@${config.fromAddress.substringAfter('@')}>")
                
                val multipart = MimeMultipart("alternative").apply {
                    if (!textContent.isNullOrBlank()) {
                        val textPart = MimeBodyPart().apply {
                            setText(textContent, "UTF-8")
                        }
                        addBodyPart(textPart)
                    }
                    
                    val htmlPart = MimeBodyPart().apply {
                        setContent(htmlContent, "text/html; charset=UTF-8")
                    }
                    addBodyPart(htmlPart)
                }
                
                setContent(multipart)
                
                setSentDate(Date())
            }
            
            Transport.send(message)
            
            val messageId = message.getHeader("Message-ID")?.firstOrNull()?.trim('<', '>')
                ?: "gmail_${System.currentTimeMillis()}"
            
            logger.info("GMAIL_SEND_SUCCESS: Email sent successfully [to=$to, messageId=$messageId]")
            
            EmailDeliveryResult.success(
                messageId = messageId,
                providerMessageId = messageId
            )
            
        } catch (e: AuthenticationFailedException) {
            logger.error("GMAIL_AUTH_FAILED: Authentication failed - check Gmail credentials", e)
            EmailDeliveryResult.failure("Authentication failed: ${e.message}", 401)
            
        } catch (e: MessagingException) {
            logger.error("GMAIL_SEND_FAILED: Failed to send email [to=$to]", e)
            EmailDeliveryResult.failure("Failed to send email: ${e.message}", 500)
            
        } catch (e: Exception) {
            logger.error("GMAIL_SEND_ERROR: Unexpected error sending email [to=$to]", e)
            EmailDeliveryResult.failure("Unexpected error: ${e.message}", 500)
        }
    }
    
    override suspend fun sendTemplateEmail(
        to: String,
        templateId: String,
        templateData: Map<String, Any>,
        subject: String?
    ): EmailDeliveryResult {
        return EmailDeliveryResult.failure(
            "Gmail provider doesn't support native templates. Use sendEmail with rendered content.",
            501
        )
    }
    
    override suspend fun sendBulkEmails(emails: List<BulkEmailRequest>): List<EmailDeliveryResult> {
        logger.info("GMAIL_BULK_SEND: Sending ${emails.size} emails")
        return emails.map { emailRequest ->
            try {
                if (emails.indexOf(emailRequest) > 0) {
                    kotlinx.coroutines.delay(100) // 100ms between emails
                }
                
                sendEmail(
                    to = emailRequest.to,
                    subject = emailRequest.subject,
                    htmlContent = emailRequest.htmlContent,
                    textContent = emailRequest.textContent
                )
            } catch (e: Exception) {
                logger.error("GMAIL_BULK_ITEM_FAILED: Failed to send bulk email to ${emailRequest.to}", e)
                EmailDeliveryResult.failure("Failed to send to ${emailRequest.to}: ${e.message}", 500)
            }
        }
    }
    
    override fun validateEmailAddress(email: String): Boolean {
        return try {
            val addr = InternetAddress(email)
            addr.validate()
            true
        } catch (e: AddressException) {
            logger.debug("GMAIL_EMAIL_VALIDATION_FAILED: Invalid email format: $email", e)
            false
        }
    }
    
    override fun getProviderInfo(): EmailProviderInfo {
        return EmailProviderInfo(
            name = "Gmail SMTP",
            version = "1.0.0",
            supportsBulkSending = true,
            supportsTemplates = false, // Gmail doesn't have native template support
            supportsTracking = false, // No native tracking
            maxRecipientsPerEmail = 100, // Gmail limit for TO field
            maxEmailsPerHour = 20, // Conservative rate limit
            maxEmailsPerDay = 500 // Gmail daily limit
        )
    }
}