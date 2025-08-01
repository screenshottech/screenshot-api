package dev.screenshotapi.core.domain.exceptions

import dev.screenshotapi.core.domain.entities.AnalysisType
import dev.screenshotapi.core.domain.entities.ErrorCategory

sealed class AnalysisException(
    message: String,
    cause: Throwable? = null,
    val analysisJobId: String? = null,
    val analysisType: AnalysisType? = null,
    val errorCategory: ErrorCategory = ErrorCategory.UNKNOWN,
    val retryable: Boolean = false
) : BusinessException(message, cause) {

    class ValidationError(
        message: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        cause: Throwable? = null
    ) : AnalysisException(
        message = message,
        cause = cause,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.VALIDATION,
        retryable = false
    )

    class ProcessingError(
        message: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        cause: Throwable? = null,
        retryable: Boolean = true
    ) : AnalysisException(
        message = message,
        cause = cause,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.PROCESSING,
        retryable = retryable
    )

    class ExternalServiceError(
        message: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        cause: Throwable? = null,
        val serviceName: String,
        retryable: Boolean = true
    ) : AnalysisException(
        message = message,
        cause = cause,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.EXTERNAL_SERVICE,
        retryable = retryable
    )

    class RateLimitExceeded(
        message: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        val retryAfterSeconds: Int? = null
    ) : AnalysisException(
        message = message,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.RATE_LIMIT,
        retryable = true
    )

    class AuthenticationError(
        message: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        cause: Throwable? = null,
        val provider: String? = null
    ) : AnalysisException(
        message = message,
        cause = cause,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.AUTHENTICATION,
        retryable = false
    )

    class ConfigurationError(
        message: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        cause: Throwable? = null,
        val configKey: String? = null
    ) : AnalysisException(
        message = message,
        cause = cause,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.CONFIGURATION,
        retryable = false
    )

    class ImageDownloadError(
        message: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        cause: Throwable? = null,
        val imageUrl: String? = null
    ) : AnalysisException(
        message = message,
        cause = cause,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.EXTERNAL_SERVICE,
        retryable = true
    )

    class InsufficientCreditsError(
        message: String,
        analysisJobId: String? = null,
        analysisType: AnalysisType? = null,
        val requiredCredits: Int,
        val availableCredits: Int
    ) : AnalysisException(
        message = message,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.VALIDATION,
        retryable = false
    )

    class JobNotFoundError(
        message: String,
        analysisJobId: String,
        analysisType: AnalysisType? = null
    ) : AnalysisException(
        message = message,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.VALIDATION,
        retryable = false
    )

    class InvalidJobStatusError(
        message: String,
        analysisJobId: String,
        analysisType: AnalysisType? = null,
        val currentStatus: String,
        val expectedStatus: String
    ) : AnalysisException(
        message = message,
        analysisJobId = analysisJobId,
        analysisType = analysisType,
        errorCategory = ErrorCategory.VALIDATION,
        retryable = false
    )
}

// Extension functions for easier exception creation
fun createAnalysisValidationError(
    message: String,
    jobId: String? = null,
    type: AnalysisType? = null
) = AnalysisException.ValidationError(message, jobId, type)

fun createAnalysisProcessingError(
    message: String,
    jobId: String? = null,
    type: AnalysisType? = null,
    cause: Throwable? = null,
    retryable: Boolean = true
) = AnalysisException.ProcessingError(message, jobId, type, cause, retryable)

fun createExternalServiceError(
    message: String,
    serviceName: String,
    jobId: String? = null,
    type: AnalysisType? = null,
    cause: Throwable? = null
) = AnalysisException.ExternalServiceError(message, jobId, type, cause, serviceName)