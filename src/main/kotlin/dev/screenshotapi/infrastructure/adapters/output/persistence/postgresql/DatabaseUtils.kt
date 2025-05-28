package dev.screenshotapi.infrastructure.adapters.output.persistence.postgresql

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Utility function to execute database operations in a transaction.
 * This function is used by all PostgreSQL repositories.
 */
suspend fun <T> dbQuery(database: Database? = null, block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO, database) {
        block()
    }
