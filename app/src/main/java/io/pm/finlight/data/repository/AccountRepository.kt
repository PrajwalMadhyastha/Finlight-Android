// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/AccountRepository.kt
// REASON: FEATURE - The `mergeAccounts` function has been enhanced. Before
// deleting the source accounts, it now creates and saves `AccountAlias` records.
// This teaches the app that the old account names should now map to the new
// destination account, forming the core of the new learning feature.
// =================================================================================
package io.pm.finlight

import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.entity.AccountAlias
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
            // --- NEW: Create aliases for the source accounts before deleting them ---
            val sourceAccounts = sourceAccountIds.mapNotNull { db.accountDao().getAccountByIdBlocking(it) }
            val aliases = sourceAccounts.map {
                AccountAlias(aliasName = it.name, destinationAccountId = destinationAccountId)
            }
            if (aliases.isNotEmpty()) {
                db.accountAliasDao().insertAll(aliases)
            }
            // --- End of new logic ---

            // 1. Re-assign goals from source accounts to the destination account.
            db.goalDao().reassignGoals(sourceAccountIds, destinationAccountId)

            // 2. Re-assign all transactions from source accounts to the destination account.
            db.transactionDao().reassignTransactions(sourceAccountIds, destinationAccountId)

            // 3. Delete the now-empty source accounts.
            db.accountDao().deleteByIds(sourceAccountIds)
        }
    }
}