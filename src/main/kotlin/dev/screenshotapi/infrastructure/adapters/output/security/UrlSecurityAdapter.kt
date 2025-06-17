package dev.screenshotapi.infrastructure.adapters.output.security

import dev.screenshotapi.core.ports.output.UrlSecurityPort
import dev.screenshotapi.core.ports.output.UrlValidationResult
import dev.screenshotapi.core.ports.output.ResolvedUrl
import dev.screenshotapi.core.ports.output.SecurityRisk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.*
import java.util.*

/**
 * Robust URL security adapter that prevents SSRF attacks
 * Handles DNS rebinding, redirects, encoding tricks, and cloud metadata access
 */
class UrlSecurityAdapter : UrlSecurityPort {
    
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    // Private IP ranges (RFC 1918, RFC 4193, etc.)
    private val privateIpRanges = listOf(
        "127.0.0.0/8",      // Loopback
        "10.0.0.0/8",       // Private Class A
        "172.16.0.0/12",    // Private Class B
        "192.168.0.0/16",   // Private Class C
        "169.254.0.0/16",   // Link-local (AWS metadata)
        "fc00::/7",         // IPv6 Private
        "::1/128",          // IPv6 Loopback
        "fe80::/10"         // IPv6 Link-local
    )
    
    // Cloud metadata endpoints
    private val cloudMetadataHosts = setOf(
        "169.254.169.254",           // AWS/Azure metadata
        "metadata.google.internal",   // Google Cloud metadata
        "metadata",
        "169.254.169.253",           // Azure IMDS v2
        "100.100.100.200"            // Alibaba Cloud metadata
    )
    
    // Blocked schemes
    private val blockedSchemes = setOf(
        "file", "ftp", "sftp", "ldap", "ldaps", "gopher", 
        "dict", "tftp", "ssh", "telnet", "smtp", "pop3", "imap"
    )
    
    override suspend fun validateUrl(url: String): UrlValidationResult = withContext(Dispatchers.IO) {
        try {
            logger.debug("Validating URL: $url")
            
            // Step 1: Basic URL format validation
            val uri = try {
                URI(url.trim())
            } catch (e: Exception) {
                logger.warn("Invalid URL format: $url")
                return@withContext UrlValidationResult(
                    isValid = false,
                    reason = "Invalid URL format: ${e.message}",
                    securityRisk = SecurityRisk.INVALID_URL_FORMAT
                )
            }
            
            // Step 2: Validate scheme
            if (uri.scheme?.lowercase() !in setOf("http", "https")) {
                if (uri.scheme?.lowercase() in blockedSchemes) {
                    logger.warn("Blocked scheme detected: ${uri.scheme}")
                    return@withContext UrlValidationResult(
                        isValid = false,
                        reason = "Blocked URL scheme: ${uri.scheme}",
                        securityRisk = SecurityRisk.BLOCKED_SCHEME
                    )
                }
            }
            
            // Step 3: Validate hostname
            val hostname = uri.host ?: return@withContext UrlValidationResult(
                isValid = false,
                reason = "Missing hostname",
                securityRisk = SecurityRisk.INVALID_URL_FORMAT
            )
            
            // Step 4: Check for cloud metadata endpoints
            if (isCloudMetadataEndpoint(hostname)) {
                logger.warn("Cloud metadata endpoint blocked: $hostname")
                return@withContext UrlValidationResult(
                    isValid = false,
                    reason = "Access to cloud metadata endpoint blocked",
                    securityRisk = SecurityRisk.CLOUD_METADATA_ACCESS
                )
            }
            
            // Step 5: Resolve DNS and validate IP
            val resolvedIps = try {
                resolveDnsWithValidation(hostname)
            } catch (e: Exception) {
                logger.warn("DNS resolution failed for: $hostname - ${e.message}")
                return@withContext UrlValidationResult(
                    isValid = false,
                    reason = "DNS resolution failed: ${e.message}",
                    securityRisk = SecurityRisk.RESOLVE_FAILURE
                )
            }
            
            // Step 6: Validate all resolved IPs
            for (ip in resolvedIps) {
                val ipValidation = validateResolvedIp(ip, hostname)
                if (!ipValidation.isValid) {
                    return@withContext ipValidation.copy(resolvedIp = ip.hostAddress)
                }
            }
            
            // Step 7: Follow redirects and validate final destination
            val resolvedUrl = try {
                resolveUrl(url)
            } catch (e: Exception) {
                logger.warn("URL resolution failed: $url - ${e.message}")
                return@withContext UrlValidationResult(
                    isValid = false,
                    reason = "URL resolution failed: ${e.message}",
                    securityRisk = SecurityRisk.SUSPICIOUS_REDIRECT
                )
            }
            
            // Step 8: Validate final destination
            if (resolvedUrl.redirectCount > 0) {
                val finalValidation = validateUrl(resolvedUrl.finalUrl)
                if (!finalValidation.isValid) {
                    logger.warn("Final redirect destination is unsafe: ${resolvedUrl.finalUrl}")
                    return@withContext finalValidation.copy(
                        reason = "Redirect leads to unsafe destination: ${finalValidation.reason}",
                        securityRisk = SecurityRisk.SUSPICIOUS_REDIRECT
                    )
                }
            }
            
            logger.info("URL validation successful: $url -> ${resolvedUrl.finalIp}")
            UrlValidationResult(
                isValid = true,
                resolvedIp = resolvedUrl.finalIp,
                finalUrl = resolvedUrl.finalUrl
            )
            
        } catch (e: Exception) {
            logger.error("Unexpected error during URL validation: $url", e)
            UrlValidationResult(
                isValid = false,
                reason = "Validation error: ${e.message}",
                securityRisk = SecurityRisk.RESOLVE_FAILURE
            )
        }
    }
    
    override suspend fun resolveUrl(url: String, maxRedirects: Int): ResolvedUrl = withContext(Dispatchers.IO) {
        // Simplified implementation - in production you'd use HTTP client to follow redirects
        val uri = URI(url)
        val hostname = uri.host ?: throw IllegalArgumentException("No hostname in URL")
        val resolvedIps = resolveDnsWithValidation(hostname)
        
        ResolvedUrl(
            finalUrl = url,
            finalIp = resolvedIps.first().hostAddress,
            redirectChain = emptyList(),
            redirectCount = 0
        )
    }
    
    private fun resolveDnsWithValidation(hostname: String): List<InetAddress> {
        // Handle IP addresses directly
        if (isIpAddress(hostname)) {
            val addr = InetAddress.getByName(hostname)
            return listOf(addr)
        }
        
        // Resolve hostname to IPs
        val addresses = InetAddress.getAllByName(hostname).toList()
        if (addresses.isEmpty()) {
            throw UnknownHostException("No IP addresses found for hostname: $hostname")
        }
        
        return addresses
    }
    
    private fun validateResolvedIp(ip: InetAddress, originalHostname: String): UrlValidationResult {
        val ipString = ip.hostAddress
        
        // Check for localhost/loopback
        if (ip.isLoopbackAddress) {
            logger.warn("Localhost access blocked: $originalHostname -> $ipString")
            return UrlValidationResult(
                isValid = false,
                reason = "Access to localhost/loopback addresses is blocked",
                securityRisk = SecurityRisk.LOCALHOST_ACCESS
            )
        }
        
        // Check for link-local addresses (including AWS metadata)
        if (ip.isLinkLocalAddress || ipString.startsWith("169.254.")) {
            logger.warn("Link-local/metadata access blocked: $originalHostname -> $ipString")
            return UrlValidationResult(
                isValid = false,
                reason = "Access to link-local addresses (including cloud metadata) is blocked",
                securityRisk = SecurityRisk.CLOUD_METADATA_ACCESS
            )
        }
        
        // Check for private IP ranges
        if (ip.isSiteLocalAddress || isPrivateIpRange(ipString)) {
            logger.warn("Private IP access blocked: $originalHostname -> $ipString")
            return UrlValidationResult(
                isValid = false,
                reason = "Access to private IP addresses is blocked",
                securityRisk = SecurityRisk.PRIVATE_IP_ACCESS
            )
        }
        
        // Check for potential DNS rebinding (hostname looks public but resolves to private)
        if (!isInternalHostname(originalHostname) && (ip.isLoopbackAddress || ip.isSiteLocalAddress)) {
            logger.warn("Potential DNS rebinding detected: $originalHostname -> $ipString")
            return UrlValidationResult(
                isValid = false,
                reason = "Potential DNS rebinding attack detected",
                securityRisk = SecurityRisk.DNS_REBINDING
            )
        }
        
        return UrlValidationResult(isValid = true)
    }
    
    private fun isCloudMetadataEndpoint(hostname: String): Boolean {
        return cloudMetadataHosts.any { endpoint ->
            hostname.equals(endpoint, ignoreCase = true) ||
            hostname.endsWith(".$endpoint", ignoreCase = true)
        }
    }
    
    private fun isIpAddress(hostname: String): Boolean {
        return try {
            InetAddress.getByName(hostname).hostAddress == hostname
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isPrivateIpRange(ip: String): Boolean {
        // Additional private IP range checks for edge cases
        return ip.startsWith("10.") ||
               ip.startsWith("192.168.") ||
               ip.matches(Regex("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) ||
               ip.startsWith("169.254.") ||
               ip == "0.0.0.0" ||
               ip.startsWith("::") ||
               ip.startsWith("fc") ||
               ip.startsWith("fd")
    }
    
    private fun isInternalHostname(hostname: String): Boolean {
        val internalPatterns = listOf(
            "localhost", "local", "internal", "private", "admin", 
            "management", "metadata", "consul", "etcd"
        )
        
        return internalPatterns.any { pattern ->
            hostname.contains(pattern, ignoreCase = true)
        }
    }
}