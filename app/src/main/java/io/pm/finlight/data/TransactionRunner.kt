package io.pm.finlight.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Interface for running database transactions.
 * This abstraction allows for easier testing by mocking the transaction execution
 * without needing to mock static Room/Coroutines extension functions.
 */
interface TransactionRunner {
    suspend fun <R> run(db: RoomDatabase, block: suspend () -> R): R
}

/**
 * Default implementation that delegates to Room's withTransaction extension function.
 */
class RoomTransactionRunner : TransactionRunner {
    override suspend fun <R> run(db: RoomDatabase, block: suspend () -> R): R {
        return db.withTransaction(block)
    }
}
