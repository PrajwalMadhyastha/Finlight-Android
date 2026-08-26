package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IAccountRepository {
    val accountsWithBalance: Flow<List<AccountWithBalance>>
    val allAccounts: Flow<List<Account>>

    fun getAccountById(accountId: Int): Flow<Account?>

    suspend fun insert(account: Account): Long

    suspend fun update(account: Account)

    suspend fun delete(account: Account)

    suspend fun mergeAccounts(
        destinationAccountId: Int,
        sourceAccountIds: List<Int>,
    )
}
