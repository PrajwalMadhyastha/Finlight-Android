package io.pm.finlight.data.repository

import android.os.Build
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountAliasDao
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.entity.AccountAlias
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AccountRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var db: AppDatabase
    @Mock
    private lateinit var accountDao: AccountDao
    @Mock
    private lateinit var goalDao: GoalDao
    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var accountAliasDao: AccountAliasDao

    // Mocks for dependencies of withTransaction
    @Mock
    private lateinit var openHelper: SupportSQLiteOpenHelper
    @Mock
    private lateinit var writableDb: SupportSQLiteDatabase


    private lateinit var repository: AccountRepository

    @Before
    override fun setup() {
        super.setup()
        // Stub the database to return mocked DAOs
        `when`(db.accountDao()).thenReturn(accountDao)
        `when`(db.goalDao()).thenReturn(goalDao)
        `when`(db.transactionDao()).thenReturn(transactionDao)
        `when`(db.accountAliasDao()).thenReturn(accountAliasDao)

        // Mock the underlying components that `withTransaction` uses,
        // instead of mocking the static extension function itself.
        `when`(db.openHelper).thenReturn(openHelper)
        `when`(openHelper.writableDatabase).thenReturn(writableDb)
        `when`(db.transactionExecutor).thenReturn(testDispatcher.asExecutor())

        repository = AccountRepository(db)
    }

    @Test
    fun `accountsWithBalance calls DAO`() = runTest {
        repository.accountsWithBalance
        verify(accountDao).getAccountsWithBalance()
    }

    @Test
    fun `allAccounts calls DAO`() = runTest {
        repository.allAccounts
        verify(accountDao).getAllAccounts()
    }

    @Test
    fun `getAccountById calls DAO`() = runTest {
        repository.getAccountById(1)
        verify(accountDao).getAccountById(1)
    }

    @Test
    fun `insert calls DAO`() = runTest {
        val account = Account(name = "Test", type = "Bank")
        repository.insert(account)
        verify(accountDao).insert(account)
    }

    @Test
    fun `update calls DAO`() = runTest {
        val account = Account(id = 1, name = "Test", type = "Bank")
        repository.update(account)
        verify(accountDao).update(account)
    }

    @Test
    fun `delete calls DAO`() = runTest {
        val account = Account(id = 1, name = "Test", type = "Bank")
        repository.delete(account)
        verify(accountDao).delete(account)
    }

    @Test
    @Ignore
    fun `mergeAccounts performs all steps in correct order`() = runTest {
        // Arrange
        val destinationId = 1
        val sourceIds = listOf(2, 3)
        val sourceAccount2 = Account(id = 2, name = "Source Account 2", type = "Bank")
        val sourceAccount3 = Account(id = 3, name = "Source Account 3", type = "Card")

        `when`(accountDao.getAccountByIdBlocking(2)).thenReturn(sourceAccount2)
        `when`(accountDao.getAccountByIdBlocking(3)).thenReturn(sourceAccount3)

        @Suppress("UNCHECKED_CAST")
        val aliasCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<AccountAlias>>

        // Act
        repository.mergeAccounts(destinationId, sourceIds)

        // Assert
        // Use inOrder to verify the sequence of operations within the transaction
        val inOrder = inOrder(goalDao, transactionDao, accountAliasDao, accountDao, writableDb)

        // Verify transaction block execution
        inOrder.verify(writableDb).beginTransaction()

        // 1. Verify aliases are created first
        inOrder.verify(accountAliasDao).insertAll(aliasCaptor.capture())
        val capturedAliases = aliasCaptor.value
        assertEquals(2, capturedAliases.size)
        assertTrue(capturedAliases.any { it.aliasName == "Source Account 2" && it.destinationAccountId == destinationId })
        assertTrue(capturedAliases.any { it.aliasName == "Source Account 3" && it.destinationAccountId == destinationId })


        // 2. Verify goals are reassigned
        inOrder.verify(goalDao).reassignGoals(eq(sourceIds), eq(destinationId))

        // 3. Verify transactions are reassigned
        inOrder.verify(transactionDao).reassignTransactions(eq(sourceIds), eq(destinationId))

        // 4. Verify source accounts are deleted last
        inOrder.verify(accountDao).deleteByIds(eq(sourceIds))

        // Verify transaction block completion
        inOrder.verify(writableDb).setTransactionSuccessful()
        inOrder.verify(writableDb).endTransaction()
    }
}

