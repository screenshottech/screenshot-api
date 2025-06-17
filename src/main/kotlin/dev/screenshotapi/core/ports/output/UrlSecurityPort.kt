package dev.screenshotapi.core.ports.output

/**
 * Port for URL security validation
 * Prevents SSRF attacks by validating URLs before accessing them
 */
interface UrlSecurityPort {
    
    /**
     * Validates if a URL is safe to access (prevents SSRF attacks)
     * @param url The URL to validate
     * @return UrlValidationResult with validation details
     */
    suspend fun validateUrl(url: String): UrlValidationResult
    
    /**
     * Resolves URL to its final destination after following redirects
     * @param url The URL to resolve
     * @param maxRedirects Maximum number of redirects to follow (default: 5)
     * @return ResolvedUrl with final destination and redirect chain
     */
    suspend fun resolveUrl(url: String, maxRedirects: Int = 5): ResolvedUrl
}

/**
 * Result of URL validation
 */
data class UrlValidationResult(
    val isValid: Boolean,
    val reason: String? = null,
    val resolvedIp: String? = null,
    val finalUrl: String? = null,
    val securityRisk: SecurityRisk? = null
)

/**
 * Resolved URL with redirect information
 */
data class ResolvedUrl(
    val finalUrl: String,
    val finalIp: String,
    val redirectChain: List<String> = emptyList(),
    val redirectCount: Int = 0
)

/**
 * Types of security risks detected
 */
enum class SecurityRisk {
    PRIVATE_IP_ACCESS,          // Accessing private/internal IPs
    LOCALHOST_ACCESS,           // Accessing localhost/loopback
    CLOUD_METADATA_ACCESS,      // AWS/GCP/Azure metadata endpoints
    SUSPICIOUS_REDIRECT,        // Redirect to private/internal resource
    BLOCKED_SCHEME,            // Non-HTTP schemes (file://, ftp://, etc.)
    DNS_REBINDING,             // Potential DNS rebinding attack
    INVALID_URL_FORMAT,        // Malformed URL
    RESOLVE_FAILURE           // DNS resolution failed
}