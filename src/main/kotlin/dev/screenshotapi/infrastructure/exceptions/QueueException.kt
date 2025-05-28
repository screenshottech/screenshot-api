package dev.screenshotapi.infrastructure.exceptions

sealed class QueueException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConnectionFailed(cause: Throwable) : QueueException("Queue connection failed", cause)
    class EnqueueFailed(val jobId: String, cause: Throwable) : QueueException("Failed to enqueue job: $jobId", cause)
    class DequeueFailed(cause: Throwable) : QueueException("Failed to dequeue job", cause)
    class QueueFullException(val maxSize: Long) : QueueException("Queue is full (max: $maxSize)")
    class SerializationFailed(val jobId: String, cause: Throwable) :
        QueueException("Failed to serialize job: $jobId", cause)

    class DeserializationFailed(cause: Throwable) : QueueException("Failed to deserialize job", cause)
}
