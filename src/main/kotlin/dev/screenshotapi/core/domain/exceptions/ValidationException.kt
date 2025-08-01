package dev.screenshotapi.core.domain.exceptions

sealed class ValidationException(
    message: String,
    val field: String? = null,
    cause: Throwable? = null
) : BusinessException(message, cause) {
    
    class Required(field: String) : 
        ValidationException("$field is required", field)
    
    class InvalidFormat(field: String, format: String) : 
        ValidationException("Invalid $field format. $format", field)
    
    class InvalidRange(field: String, min: Any? = null, max: Any? = null) : 
        ValidationException(
            when {
                min != null && max != null -> "$field must be between $min and $max"
                min != null -> "$field must be at least $min"
                max != null -> "$field must be at most $max"
                else -> "$field is out of valid range"
            }, 
            field
        )
    
    class InvalidState(entity: String, currentState: String, requiredState: String? = null) :
        ValidationException(
            if (requiredState != null) 
                "$entity must be $requiredState. Current state: $currentState"
            else 
                "$entity is in invalid state: $currentState"
        )
    
    class UnauthorizedAccess(resource: String, resourceId: String? = null) :
        ValidationException(
            if (resourceId != null) 
                "Unauthorized access to $resource: $resourceId"
            else 
                "Unauthorized access to $resource"
        )
    
    class LimitExceeded(resource: String, limit: Int, current: Int? = null) :
        ValidationException(
            if (current != null)
                "Maximum number of $resource ($limit) exceeded. Current: $current"
            else
                "Maximum number of $resource ($limit) exceeded"
        )
        
    class Positive(field: String) :
        ValidationException("$field must be positive", field)
        
    class NonNegative(field: String) :
        ValidationException("$field must be non-negative", field)
        
    // For custom validations that don't fit the patterns above
    class Custom(message: String, field: String? = null, cause: Throwable? = null) :
        ValidationException(message, field, cause)
}
