package dev.screenshotapi.core.domain.exceptions

class ConcurrentModificationException(
    message: String,
    val resourceId: String? = null,
    val lockedBy: String? = null,
    cause: Throwable? = null
) : BusinessException(message, cause) {

    constructor(resourceType: String, resourceId: String, lockedBy: String? = null) : this(
        message = "$resourceType is currently being processed by another worker",
        resourceId = resourceId,
        lockedBy = lockedBy
    )
}