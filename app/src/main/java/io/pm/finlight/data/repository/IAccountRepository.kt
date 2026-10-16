package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IAccountRepository {
    val accountsWithBalance: Flow<List<AccountWithBalance>>
    val allAccounts: Flow<List<Account>>

    suspend fun getAllAccountsSnapshot(): List<Account>

    fun getAccountById(accountId: Int): Flow<Account?>

    suspend fun getAccountByIdSync(accountId: Int): Account?

    suspend fun insert(account: Account): Long

    suspend fun update(account: Account)

    suspend fun delete(account: Account)

    @Deprecated(
        message = "Use MergeAccountsUseCase directly from presentation/domain layer.",
        replaceWith = ReplaceWith("mergeAccountsUseCase(destinationAccountId, sourceAccountIds)"),
    )
    suspend fun mergeAccounts(
        destinationAccountId: Int,
        sourceAccountIds: List<Int>,
    )

    /**
     * Resolves an account by name or alias, creating a new account if one does not exist.
     * Guarantees thread-safe account resolution and handles concurrent creation conflicts.
     *
     * @param name The account name or alias.
     * @param type The account type classification. Defaults to [AccountType.OTHER].
     * @return The existing or newly created [Account].
     */
    suspend fun findOrCreateByName(
        name: String,
        type: AccountType = AccountType.OTHER,
    ): Account

    /**
     * Resolves an account by name or alias, creating a new account if one does not exist.
     *
     * @param name The account name or alias.
     * @param type The raw account type string representation.
     * @return The existing or newly created [Account].
     */
    suspend fun findOrCreateByName(
        name: String,
        type: String,
    ): Account
}
