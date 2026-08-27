package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IRecurringTransactionRepository {
    fun getAll(): Flow<List<RecurringTransaction>>

    fun getById(id: Int): Flow<RecurringTransaction?>

    suspend fun insert(recurringTransaction: RecurringTransaction)

    suspend fun update(recurringTransaction: RecurringTransaction)

    suspend fun delete(recurringTransaction: RecurringTransaction)
}
