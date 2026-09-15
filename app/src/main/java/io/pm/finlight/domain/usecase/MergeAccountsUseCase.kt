package io.pm.finlight.domain.usecase

import androidx.room.withTransaction
import io.pm.finlight.GoalDao
import io.pm.finlight.RecurringPatternDao
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountAliasDao
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.dao.MergeRecordDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.db.entity.AccountAlias

/**
 * Domain UseCase coordinating the multi-DAO atomic merge of accounts.
 *
 * Responsibilities:
 * 1. Create account aliases for source accounts mapping to destination account (for auto-learning).
 * 2. Repoint any existing aliases targeting source accounts to destination account.
 * 3. Reassign recurring transactions from source accounts to destination account.
 * 4. Reassign all savings goals from source accounts to destination account.
 * 5. Reassign all transactions from source accounts to destination account.
 * 6. Reassign child account references in merge records to destination account.
 * 7. Reassign suggested recurring patterns from source accounts to destination account.
 * 8. Delete the source accounts.
 *
 * All operations execute atomically inside a single database transaction.
 */
class MergeAccountsUseCase(
    private val accountDao: AccountDao,
    private val accountAliasDao: AccountAliasDao,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val goalDao: GoalDao,
    private val transactionWriteDao: TransactionWriteDao,
    private val mergeRecordDao: MergeRecordDao,
    private val recurringPatternDao: RecurringPatternDao,
    private val db: AppDatabase,
) {
    constructor(db: AppDatabase) : this(
        accountDao = db.accountDao(),
        accountAliasDao = db.accountAliasDao(),
        recurringTransactionDao = db.recurringTransactionDao(),
        goalDao = db.goalDao(),
        transactionWriteDao = db.transactionWriteDao(),
        mergeRecordDao = db.mergeRecordDao(),
        recurringPatternDao = db.recurringPatternDao(),
        db = db,
    )

    /**
     * Atomically merges multiple source accounts into a single destination account.
     *
     * @param destinationAccountId The ID of the account to keep.
     * @param sourceAccountIds The IDs of the accounts to merge and delete.
     */
    suspend operator fun invoke(
        destinationAccountId: Int,
        sourceAccountIds: List<Int>,
    ) {
        val targetSourceIds = sourceAccountIds.filter { it != destinationAccountId }.distinct()
        if (targetSourceIds.isEmpty()) return
        val destinationAccount = accountDao.getAccountByIdSync(destinationAccountId) ?: return

        db.withTransaction {
            // 1. Create aliases for the source accounts before deleting them
            val sourceAccounts = targetSourceIds.mapNotNull { accountDao.getAccountByIdSync(it) }
            val aliases =
                sourceAccounts.map {
                    AccountAlias(aliasName = it.name, destinationAccountId = destinationAccountId)
                }
            if (aliases.isNotEmpty()) {
                accountAliasDao.insertAll(aliases)
            }

            // 2. Repoint any existing aliases pointing to source accounts to the destination account
            accountAliasDao.reassignAliases(targetSourceIds, destinationAccountId)

            // 3. Re-assign recurring transactions to avoid cascade deletion
            recurringTransactionDao.reassignRecurringTransactions(targetSourceIds, destinationAccountId)

            // 4. Re-assign goals from source accounts to the destination account.
            goalDao.reassignGoals(targetSourceIds, destinationAccountId)

            // 5. Re-assign all transactions from source accounts to the destination account.
            transactionWriteDao.reassignTransactions(targetSourceIds, destinationAccountId)

            // 6. Re-assign child accounts in merge records to avoid dangling FK references on transaction unmerge
            mergeRecordDao.reassignChildAccount(targetSourceIds, destinationAccountId)

            // 7. Re-assign suggested recurring patterns to the destination account
            recurringPatternDao.reassignRecurringPatterns(targetSourceIds, destinationAccountId)

            // 8. Delete the now-empty source accounts.
            accountDao.deleteByIds(targetSourceIds)
        }
    }
}
