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
import io.pm.finlight.data.db.dao.AccountAliasDao
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AccountRepository(
    private val accountDao: AccountDao,
    private val accountAliasDao: AccountAliasDao,
    private val db: AppDatabase,
    private val mergeAccountsUseCase: MergeAccountsUseCase? = null,
) : IAccountRepository {
    private val accountResolutionMutex = Mutex()

    constructor(
        db: AppDatabase,
        mergeAccountsUseCase: MergeAccountsUseCase? = null,
    ) : this(
        accountDao = db.accountDao(),
        accountAliasDao = db.accountAliasDao(),
        db = db,
        mergeAccountsUseCase = mergeAccountsUseCase,
    )

    override val accountsWithBalance: Flow<List<AccountWithBalance>> = accountDao.getAccountsWithBalance()

    override val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()

    override suspend fun getAllAccountsSnapshot(): List<Account> {
        return accountDao.getAllAccountsSnapshot()
    }

    override fun getAccountById(accountId: Int): Flow<Account?> {
        return accountDao.getAccountById(accountId)
    }

    override suspend fun getAccountByIdSync(accountId: Int): Account? {
        return accountDao.getAccountByIdSync(accountId)
    }

    override suspend fun insert(account: Account): Long {
        return accountDao.insert(account)
    }

    override suspend fun update(account: Account) {
        db.withTransaction {
            // Check if the account name is being changed
            val oldAccount = accountDao.getAccountByIdSync(account.id)
            if (oldAccount != null && oldAccount.name != account.name) {
                // Name changed: create an alias from the old name to this account
                val alias = AccountAlias(aliasName = oldAccount.name, destinationAccountId = account.id)
                accountAliasDao.insertAll(listOf(alias))
            }
            accountDao.update(account)
        }
    }

    override suspend fun delete(account: Account) {
        accountDao.delete(account)
    }

    /**
     * Atomically merges multiple source accounts into a single destination account.
     * Reassigns all associated goals and transactions before deleting the source accounts.
     *
     * @param destinationAccountId The ID of the account to keep.
     * @param sourceAccountIds The IDs of the accounts to merge and delete.
     */
    @Deprecated(
        message = "Use MergeAccountsUseCase directly from presentation/domain layer.",
        replaceWith = ReplaceWith("mergeAccountsUseCase(destinationAccountId, sourceAccountIds)"),
    )
    override suspend fun mergeAccounts(
        destinationAccountId: Int,
        sourceAccountIds: List<Int>,
    ) {
        val useCase =
            mergeAccountsUseCase
                ?: throw IllegalStateException("MergeAccountsUseCase must be provided to call mergeAccounts on AccountRepository")
        useCase(destinationAccountId, sourceAccountIds)
    }

    override suspend fun findOrCreateByName(
        name: String,
        type: AccountType,
    ): Account = findOrCreateByName(name, type.typeName)

    override suspend fun findOrCreateByName(
        name: String,
        type: String,
    ): Account {
        require(name.isNotBlank()) { "Account name cannot be blank" }
        val trimmedName = name.trim()
        val accountType = type.ifBlank { "General" }

        return accountResolutionMutex.withLock {
            db.withTransaction {
                // 1. Check for an alias
                val alias = accountAliasDao.findByAlias(trimmedName)
                if (alias != null) {
                    val aliasedAccount = accountDao.getAccountByIdSync(alias.destinationAccountId)
                    if (aliasedAccount != null) {
                        return@withTransaction aliasedAccount
                    }
                }

                // 2. No alias (or alias destination missing), check for an exact account name match
                val existingAccount = accountDao.findByName(trimmedName)
                if (existingAccount != null) {
                    return@withTransaction existingAccount
                }

                // 3. No exact match, create a new account
                val newAccount = Account(name = trimmedName, type = accountType)
                val newId = accountDao.insert(newAccount)

                // Handle OnConflictStrategy.IGNORE: if newId == -1L (already created concurrently
                // or case-insensitive clash), query by name to retrieve the existing account.
                if (newId != -1L) {
                    accountDao.getAccountByIdSync(newId.toInt()) ?: newAccount.copy(id = newId.toInt())
                } else {
                    accountDao.findByName(trimmedName)
                        ?: throw IllegalStateException("Failed to find or create account '$trimmedName'")
                }
            }
        }
    }
}
