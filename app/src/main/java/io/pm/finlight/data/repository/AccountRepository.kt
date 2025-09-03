// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/AccountRepository.kt
// REASON: FEATURE - The repository now encapsulates the entire atomic account
// merging logic. The new `mergeAccounts` function runs a database transaction
// that reassigns goals, reassigns transactions, and finally deletes the source
// accounts, providing a safe and clean API for the ViewModel.
// =================================================================================
package io.pm.finlight

import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow

class AccountRepository(private val db: AppDatabase) {
    private val accountDao = db.accountDao()

    val accountsWithBalance: Flow<List<AccountWithBalance>> = accountDao.getAccountsWithBalance()

    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()

    fun getAccountById(accountId: Int): Flow<Account?> {
        return accountDao.getAccountById(accountId)
    }

    suspend fun insert(account: Account): Long {
        return accountDao.insert(account)
    }

    suspend fun update(account: Account) {
        accountDao.update(account)
    }

    suspend fun delete(account: Account) {
        accountDao.delete(account)
    }

    /**
     * Atomically merges multiple source accounts into a single destination account.
     * Reassigns all associated goals and transactions before deleting the source accounts.
     *
     * @param destinationAccountId The ID of the account to keep.
     * @param sourceAccountIds The IDs of the accounts to merge and delete.
     */
    suspend fun mergeAccounts(destinationAccountId: Int, sourceAccountIds: List<Int>) {
        db.withTransaction {
            // 1. Re-assign goals from source accounts to the destination account.
            db.goalDao().reassignGoals(sourceAccountIds, destinationAccountId)

            // 2. Re-assign all transactions from source accounts to the destination account.
            db.transactionDao().reassignTransactions(sourceAccountIds, destinationAccountId)

            // 3. Delete the now-empty source accounts.
            db.accountDao().deleteByIds(sourceAccountIds)
        }
    }
}
