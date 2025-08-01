package dev.screenshotapi.core.domain.services

/**
 * Custom Prompt Validator - Domain service for validating user-provided custom prompts
 * 
 * Pure domain service that implements business rules for custom prompt validation
 * without any infrastructure dependencies (following Hexagonal Architecture)
 */
class CustomPromptValidator {
    
    companion object {
        // Business rules - character limits
        const val MAX_SYSTEM_PROMPT_LENGTH = 1000
        const val MAX_USER_PROMPT_LENGTH = 2000
        const val MIN_PROMPT_LENGTH = 10
        
        // Business rules - security patterns
        private val BANNED_PATTERNS = listOf(
            "ignore previous", "ignore all previous", "forget previous",
            "disregard previous", "override previous", "new instructions",
            "system:", "assistant:", "user:", "human:", "ai:",
            "anthropic", "claude", "training data", "jailbreak",
            "developer mode", "admin mode", "pretend", "roleplay"
        )
        
        private val SUSPICIOUS_SEQUENCES = listOf(
            "```", "{{", "}}", "<%", "%>", "<script", "</script>"
        )
        
        const val MAX_REPETITIVE_CHARS = 50
        const val MAX_SPECIAL_CHAR_RATIO = 0.3
    }
    
    /**
     * Validates custom prompts according to business rules
     */
    fun validate(
        systemPrompt: String?,
        userPrompt: String?
    ): ValidationResult {
        
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val securityFlags = mutableMapOf<String, String>()
        
        // Business rule: At least one prompt must be provided
        if (systemPrompt.isNullOrBlank() && userPrompt.isNullOrBlank()) {
            errors.add("At least one custom prompt must be provided for custom analysis")
        }
        
        // Validate system prompt if provided
        systemPrompt?.let { prompt ->
            validatePromptRules(prompt, "System prompt", MAX_SYSTEM_PROMPT_LENGTH, errors, warnings, securityFlags)
        }
        
        // Validate user prompt if provided
        userPrompt?.let { prompt ->
            validatePromptRules(prompt, "User prompt", MAX_USER_PROMPT_LENGTH, errors, warnings, securityFlags)
        }
        
        // Calculate security score based on business rules
        val securityScore = calculateSecurityScore(securityFlags, systemPrompt, userPrompt)
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            sanitizedSystemPrompt = systemPrompt?.let { sanitize(it) },
            sanitizedUserPrompt = userPrompt?.let { sanitize(it) },
            securityScore = securityScore,
            errors = errors,
            warnings = warnings,
            securityFlags = securityFlags
        )
    }
    
    /**
     * Applies business rules for prompt validation
     */
    private fun validatePromptRules(
        prompt: String,
        promptType: String,
        maxLength: Int,
        errors: MutableList<String>,
        warnings: MutableList<String>,
        securityFlags: MutableMap<String, String>
    ) {
        val trimmedLength = prompt.trim().length
        
        // Business rule: Length constraints
        if (trimmedLength < MIN_PROMPT_LENGTH) {
            errors.add("$promptType must be at least $MIN_PROMPT_LENGTH characters")
        }
        
        if (trimmedLength > maxLength) {
            errors.add("$promptType exceeds maximum length of $maxLength characters")
        }
        
        val lowerPrompt = prompt.lowercase()
        
        // Business rule: Security patterns not allowed
        BANNED_PATTERNS.forEach { pattern ->
            if (lowerPrompt.contains(pattern.lowercase())) {
                errors.add("$promptType contains prohibited content")
                securityFlags["banned_pattern"] = pattern
            }
        }
        
        // Business rule: Suspicious content warnings
        SUSPICIOUS_SEQUENCES.forEach { sequence ->
            if (prompt.contains(sequence)) {
                warnings.add("$promptType contains potentially risky content")
                securityFlags["suspicious_sequence"] = sequence
            }
        }
        
        // Business rule: Excessive repetition not allowed
        val repetitiveCount = findMaxRepetitiveChars(prompt)
        if (repetitiveCount > MAX_REPETITIVE_CHARS) {
            warnings.add("$promptType contains excessive repetition")
            securityFlags["repetitive_chars"] = repetitiveCount.toString()
        }
        
        // Business rule: Special character ratio limit
        val specialCharRatio = calculateSpecialCharRatio(prompt)
        if (specialCharRatio > MAX_SPECIAL_CHAR_RATIO) {
            warnings.add("$promptType has high special character density")
            securityFlags["special_char_ratio"] = String.format("%.2f", specialCharRatio)
        }
    }
    
    /**
     * Sanitizes prompt according to business rules
     */
    private fun sanitize(prompt: String): String {
        return prompt
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .trim()
    }
    
    /**
     * Calculates security score based on business rules
     */
    private fun calculateSecurityScore(
        securityFlags: Map<String, String>,
        systemPrompt: String?,
        userPrompt: String?
    ): Double {
        var score = 1.0
        
        // Apply penalties based on security flags
        securityFlags.forEach { (flag, _) ->
            score -= when (flag) {
                "banned_pattern" -> 0.5
                "suspicious_sequence" -> 0.1
                "repetitive_chars" -> 0.05
                "special_char_ratio" -> 0.1
                else -> 0.05
            }
        }
        
        // Penalty for excessive length
        val totalLength = (systemPrompt?.length ?: 0) + (userPrompt?.length ?: 0)
        if (totalLength > 2500) {
            score -= 0.1
        }
        
        return maxOf(0.0, score)
    }
    
    private fun findMaxRepetitiveChars(text: String): Int {
        if (text.isEmpty()) return 0
        
        var maxCount = 1
        var currentCount = 1
        
        for (i in 1 until text.length) {
            if (text[i] == text[i - 1]) {
                currentCount++
                maxCount = maxOf(maxCount, currentCount)
            } else {
                currentCount = 1
            }
        }
        
        return maxCount
    }
    
    private fun calculateSpecialCharRatio(text: String): Double {
        if (text.isEmpty()) return 0.0
        
        val specialChars = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        return specialChars.toDouble() / text.length
    }
    
    /**
     * Domain model representing validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val sanitizedSystemPrompt: String?,
        val sanitizedUserPrompt: String?,
        val securityScore: Double,
        val errors: List<String>,
        val warnings: List<String>,
        val securityFlags: Map<String, String>
    ) {
        fun hasHighSecurityRisk(): Boolean = securityScore < 0.5
        fun hasSecurityFlags(): Boolean = securityFlags.isNotEmpty()
    }
}