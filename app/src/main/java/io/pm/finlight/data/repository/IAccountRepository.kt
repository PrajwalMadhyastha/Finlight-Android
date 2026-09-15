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
}
