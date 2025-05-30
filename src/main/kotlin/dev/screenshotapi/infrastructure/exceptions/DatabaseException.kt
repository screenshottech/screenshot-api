package dev.screenshotapi.infrastructure.exceptions

sealed class DatabaseException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConnectionFailed(cause: Throwable) : DatabaseException("Database connection failed", cause)
    class QueryFailed(val query: String, cause: Throwable) : DatabaseException("Query failed: $query", cause)
    class TransactionFailed(cause: Throwable) : DatabaseException("Transaction failed", cause)
    class MigrationFailed(val version: String, cause: Throwable) :
        DatabaseException("Migration failed for version: $version", cause)

    class DuplicateKeyException(val key: String, cause: Throwable? = null) :
        DatabaseException("Duplicate key: $key", cause)

    class ConstraintViolationException(val constraint: String, cause: Throwable? = null) :
        DatabaseException("Constraint violation: $constraint", cause)
        
    class OperationFailed(message: String, cause: Throwable? = null) :
        DatabaseException(message, cause)
}
