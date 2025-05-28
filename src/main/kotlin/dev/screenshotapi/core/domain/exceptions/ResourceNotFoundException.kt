package dev.screenshotapi.core.domain.exceptions

class ResourceNotFoundException(
    message: String,
    val resourceType: String? = null,
    val resourceId: String? = null,
    cause: Throwable? = null
) : BusinessException(message, cause) {

    constructor(resourceType: String, resourceId: String) : this(
        message = "$resourceType not found with ID: $resourceId",
        resourceType = resourceType,
        resourceId = resourceId
    )
}
