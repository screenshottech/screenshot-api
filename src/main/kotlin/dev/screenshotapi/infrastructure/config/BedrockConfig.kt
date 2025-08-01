package dev.screenshotapi.infrastructure.config

import kotlinx.serialization.Serializable

/**
 * AWS Bedrock Configuration for OCR and AI Analysis services
 * Based on patterns from alpha-ragde project for production-ready AWS integration
 */
@Serializable
data class BedrockConfig(
    val enabled: Boolean = true,
    val region: String = "us-east-2",
    val aws: AwsConfig = AwsConfig(),
    val models: BedrockModelsConfig = BedrockModelsConfig(),
    val retry: RetryConfig = RetryConfig(),
    val timeout: TimeoutConfig = TimeoutConfig(),
    val fallback: FallbackConfig = FallbackConfig()
)

/**
 * AWS Configuration for credentials and session management
 */
@Serializable
data class AwsConfig(
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val sessionToken: String? = null,
    val profile: String? = null,
    val roleArn: String? = null,
    val assumeRoleSessionName: String = "screenshot-api-bedrock",
    val credentialsRefreshMinutes: Int = 45 // Refresh before 1-hour expiry
)

/**
 * Bedrock Models Configuration
 * Claude 3 Haiku as primary model for cost-effectiveness
 */
@Serializable
data class BedrockModelsConfig(
    val claude3Haiku: ClaudeModelConfig = ClaudeModelConfig(
        modelId = "us.anthropic.claude-3-haiku-20240307-v1:0",
        maxTokens = 4096,
        temperature = 0.1,
        topP = 0.9,
        topK = 250
    ),
    val claude3Sonnet: ClaudeModelConfig = ClaudeModelConfig(
        modelId = "us.anthropic.claude-3-sonnet-20240229-v1:0",
        maxTokens = 4096,
        temperature = 0.2,
        topP = 0.9,
        topK = 250
    ),
    val primaryModel: String = "claude3Haiku", // Default to most cost-effective
    val fallbackModel: String = "claude3Sonnet"
)

/**
 * Claude Model Specific Configuration
 */
@Serializable
data class ClaudeModelConfig(
    val modelId: String,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.1,
    val topP: Double = 0.9,
    val topK: Int = 250,
    val stopSequences: List<String> = emptyList()
)

/**
 * Retry Configuration for robust error handling
 * Based on alpha-ragde retry patterns
 */
@Serializable
data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
    val retryableStatusCodes: List<Int> = listOf(429, 500, 502, 503, 504),
    val retryableExceptions: List<String> = listOf(
        "ThrottlingException",
        "ServiceUnavailableException",
        "InternalServerException"
    )
)

/**
 * Timeout Configuration
 */
@Serializable
data class TimeoutConfig(
    val connectionTimeoutMs: Long = 30000, // 30 seconds
    val requestTimeoutMs: Long = 120000,   // 2 minutes (much better than 120s PaddleOCR)
    val socketTimeoutMs: Long = 60000      // 1 minute
)

/**
 * Fallback Configuration for when Bedrock is unavailable
 * Note: Fallback is disabled by default as PaddleOCR has reliability issues.
 * Classes preserved for potential future microservice integration.
 */
@Serializable
data class FallbackConfig(
    val enablePaddleOcrFallback: Boolean = false,  // Disabled by default - PaddleOCR has issues
    val fallbackForBasicOcrOnly: Boolean = true,   // Only BASIC_OCR can fallback (if enabled)
    val maxFallbackAttempts: Int = 1,
    val fallbackDelayMs: Long = 5000,
    val preservedForFutureUse: String = "PaddleOCR classes maintained for potential microservice"
)

/**
 * Feature Flags for gradual migration
 */
@Serializable
data class BedrockFeatureFlags(
    val enableBedrockOcr: Boolean = false,          // Master switch
    val enableAiAnalysis: Boolean = false,          // AI analysis features
    val forceBedrockForNewUsers: Boolean = false,   // Force new users to Bedrock
    val bedrockPercentageRollout: Int = 0,          // Percentage of users on Bedrock (0-100)
    val enableBatchProcessing: Boolean = false,     // Batch analysis features
    val enableAdvancedPrompts: Boolean = false      // Advanced prompt engineering
)

/**
 * Cost Tracking Configuration
 */
@Serializable
data class BedrockCostConfig(
    val trackTokenUsage: Boolean = true,
    val trackImageProcessingCost: Boolean = true,
    val costPerInputToken: Double = 0.00025,  // Claude 3 Haiku: $0.25 per 1M input tokens
    val costPerOutputToken: Double = 0.00125, // Claude 3 Haiku: $1.25 per 1M output tokens
    val costPerImage: Double = 0.0004,        // Estimated cost per image analysis
    val alertThresholdCredits: Int = 100,     // Alert when user below this threshold
    val maxDailyCostPerUser: Double = 10.0    // Daily spending limit per user
)

/**
 * Load Bedrock configuration from environment variables
 * Following the same pattern as other config loaders in the project
 */
fun loadBedrockConfig(): BedrockConfig {
    return BedrockConfig(
        enabled = System.getenv("BEDROCK_ENABLED")?.toBoolean() ?: false,
        region = System.getenv("BEDROCK_AWS_REGION") ?: "us-east-2",
        aws = AwsConfig(
            accessKeyId = System.getenv("BEDROCK_AWS_ACCESS_KEY_ID"),
            secretAccessKey = System.getenv("BEDROCK_AWS_SECRET_ACCESS_KEY"),
            sessionToken = System.getenv("BEDROCK_AWS_SESSION_TOKEN"),
            profile = System.getenv("BEDROCK_AWS_PROFILE"),
            roleArn = System.getenv("BEDROCK_AWS_ROLE_ARN"),
            assumeRoleSessionName = System.getenv("BEDROCK_AWS_ASSUME_ROLE_SESSION_NAME") ?: "screenshot-api-bedrock",
            credentialsRefreshMinutes = System.getenv("BEDROCK_AWS_CREDENTIALS_REFRESH_MINUTES")?.toInt() ?: 45
        ),
        models = BedrockModelsConfig(
            primaryModel = System.getenv("BEDROCK_PRIMARY_MODEL") ?: "claude3Haiku",
            fallbackModel = System.getenv("BEDROCK_FALLBACK_MODEL") ?: "claude3Sonnet"
        ),
        retry = RetryConfig(
            maxAttempts = System.getenv("BEDROCK_RETRY_MAX_ATTEMPTS")?.toInt() ?: 3,
            baseDelayMs = System.getenv("BEDROCK_RETRY_BASE_DELAY_MS")?.toLong() ?: 1000,
            maxDelayMs = System.getenv("BEDROCK_RETRY_MAX_DELAY_MS")?.toLong() ?: 30000
        ),
        timeout = TimeoutConfig(
            connectionTimeoutMs = System.getenv("BEDROCK_CONNECTION_TIMEOUT_MS")?.toLong() ?: 30000,
            requestTimeoutMs = System.getenv("BEDROCK_REQUEST_TIMEOUT_MS")?.toLong() ?: 120000,
            socketTimeoutMs = System.getenv("BEDROCK_SOCKET_TIMEOUT_MS")?.toLong() ?: 60000
        ),
        fallback = FallbackConfig(
            enablePaddleOcrFallback = System.getenv("BEDROCK_ENABLE_PADDLE_FALLBACK")?.toBoolean() ?: false,  // Default disabled
            fallbackForBasicOcrOnly = System.getenv("BEDROCK_FALLBACK_BASIC_ONLY")?.toBoolean() ?: true
        )
    )
}

/**
 * Load Bedrock feature flags from environment variables
 */
fun loadBedrockFeatureFlags(): BedrockFeatureFlags {
    return BedrockFeatureFlags(
        enableBedrockOcr = System.getenv("FEATURE_BEDROCK_OCR")?.toBoolean() ?: false,
        enableAiAnalysis = System.getenv("FEATURE_AI_ANALYSIS")?.toBoolean() ?: false,
        forceBedrockForNewUsers = System.getenv("FEATURE_FORCE_BEDROCK_NEW_USERS")?.toBoolean() ?: false,
        bedrockPercentageRollout = System.getenv("FEATURE_BEDROCK_PERCENTAGE")?.toInt() ?: 0,
        enableBatchProcessing = System.getenv("FEATURE_BATCH_PROCESSING")?.toBoolean() ?: false,
        enableAdvancedPrompts = System.getenv("FEATURE_ADVANCED_PROMPTS")?.toBoolean() ?: false
    )
}

/**
 * Load Bedrock cost configuration from environment variables
 */
fun loadBedrockCostConfig(): BedrockCostConfig {
    return BedrockCostConfig(
        trackTokenUsage = System.getenv("BEDROCK_TRACK_TOKEN_USAGE")?.toBoolean() ?: true,
        trackImageProcessingCost = System.getenv("BEDROCK_TRACK_IMAGE_COST")?.toBoolean() ?: true,
        costPerInputToken = System.getenv("BEDROCK_COST_PER_INPUT_TOKEN")?.toDouble() ?: 0.00025,
        costPerOutputToken = System.getenv("BEDROCK_COST_PER_OUTPUT_TOKEN")?.toDouble() ?: 0.00125,
        costPerImage = System.getenv("BEDROCK_COST_PER_IMAGE")?.toDouble() ?: 0.0004,
        alertThresholdCredits = System.getenv("BEDROCK_ALERT_THRESHOLD_CREDITS")?.toInt() ?: 100,
        maxDailyCostPerUser = System.getenv("BEDROCK_MAX_DAILY_COST_PER_USER")?.toDouble() ?: 10.0
    )
}

/**
 * Validate region-model compatibility for Claude 3 models
 */
fun validateRegionModelCompatibility(region: String, modelId: String): ValidationResult {
    val claudeAvailableRegions = setOf(
        "us-east-2", "us-west-2", "eu-west-1", "eu-central-1", 
        "ap-southeast-1", "ap-northeast-1", "ca-central-1"
    )
    
    val problemRegions = mapOf(
        "us-east-1" to "Claude 3 models are not available in us-east-1. Use us-east-2 instead.",
        "eu-west-2" to "Limited Claude 3 availability. Use eu-west-1 for better support.",
        "ap-south-1" to "Claude 3 models not available. Use ap-southeast-1 or ap-northeast-1."
    )
    
    return when {
        modelId.contains("claude-3") && region in problemRegions -> {
            ValidationResult.Warning(problemRegions[region]!!)
        }
        modelId.contains("claude-3") && region !in claudeAvailableRegions -> {
            ValidationResult.Error("Claude 3 models not confirmed in region $region. Recommended regions: ${claudeAvailableRegions.joinToString()}")
        }
        else -> ValidationResult.Success
    }
}

/**
 * Result of region-model validation
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
