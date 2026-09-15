package io.pm.finlight.data.repository

import android.os.Build
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountAliasDao
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AccountRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var accountDao: AccountDao

    @Mock
    private lateinit var accountAliasDao: AccountAliasDao

    @Mock
    private lateinit var mergeAccountsUseCase: MergeAccountsUseCase

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
        `when`(db.accountAliasDao()).thenReturn(accountAliasDao)

        // Mock the underlying components that `withTransaction` uses.
        // Note: For unit tests with mocks, we often need to mock the extension function itself
        // to avoid Room's internal transaction machinery which can hang.
        `when`(db.openHelper).thenReturn(openHelper)
        `when`(openHelper.writableDatabase).thenReturn(writableDb)
        `when`(db.transactionExecutor).thenReturn(testDispatcher.asExecutor())

        repository = AccountRepository(accountDao, accountAliasDao, db, mergeAccountsUseCase)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Ensure we clear any static mocks to avoid affecting other tests
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `accountsWithBalance calls DAO`() =
        runTest {
            repository.accountsWithBalance
            verify(accountDao).getAccountsWithBalance()
        }

    @Test
    fun `allAccounts calls DAO`() =
        runTest {
            repository.allAccounts
            verify(accountDao).getAllAccounts()
        }

    @Test
    fun `getAllAccountsSnapshot calls DAO`() =
        runTest {
            val accounts = listOf(Account(name = "Test", type = "Bank"))
            `when`(accountDao.getAllAccountsSnapshot()).thenReturn(accounts)
            val result = repository.getAllAccountsSnapshot()
            verify(accountDao).getAllAccountsSnapshot()
            assertEquals(accounts, result)
        }

    @Test
    fun `getAccountById calls DAO`() =
        runTest {
            repository.getAccountById(1)
            verify(accountDao).getAccountById(1)
        }

    @Test
    fun `getAccountByIdSync calls DAO`() =
        runTest {
            val account = Account(id = 1, name = "Test", type = "Bank")
            `when`(accountDao.getAccountByIdSync(1)).thenReturn(account)
            val result = repository.getAccountByIdSync(1)
            verify(accountDao).getAccountByIdSync(1)
            assertEquals(account, result)
        }

    @Test
    fun `insert calls DAO`() =
        runTest {
            val account = Account(name = "Test", type = "Bank")
            repository.insert(account)
            verify(accountDao).insert(account)
        }

    @Test
    fun `update calls DAO and creates alias if name changed`() =
        runTest {
            val oldAccount = Account(id = 1, name = "Old Name", type = "Bank")
            val newAccount = Account(id = 1, name = "New Name", type = "Bank")

            `when`(accountDao.getAccountByIdSync(1)).thenReturn(oldAccount)

            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { db.withTransaction<Any?>(any()) } coAnswers {
                writableDb.beginTransaction()
                try {
                    @Suppress("UNCHECKED_CAST")
                    val block = it.invocation.args[1] as suspend () -> Any?
                    val result = block()
                    writableDb.setTransactionSuccessful()
                    result
                } finally {
                    writableDb.endTransaction()
                }
            }

            val aliasCaptor = argumentCaptor<List<AccountAlias>>()

            repository.update(newAccount)

            val inOrder = inOrder(accountDao, accountAliasDao, writableDb)
            inOrder.verify(writableDb).beginTransaction()
            inOrder.verify(accountDao).getAccountByIdSync(1)
            inOrder.verify(accountAliasDao).insertAll(capture(aliasCaptor))
            inOrder.verify(accountDao).update(newAccount)
            inOrder.verify(writableDb).setTransactionSuccessful()
            inOrder.verify(writableDb).endTransaction()

            assertEquals(1, aliasCaptor.value.size)
            assertEquals("Old Name", aliasCaptor.value[0].aliasName)
            assertEquals(1, aliasCaptor.value[0].destinationAccountId)
        }

    @Test
    fun `update calls DAO and does not create alias if name unchanged`() =
        runTest {
            val account = Account(id = 1, name = "Same Name", type = "Bank")

            `when`(accountDao.getAccountByIdSync(1)).thenReturn(account)

            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { db.withTransaction<Any?>(any()) } coAnswers {
                writableDb.beginTransaction()
                try {
                    // In mockk for extension functions, args[1] is typically the block, args[0] is the receiver
                    @Suppress("UNCHECKED_CAST")
                    val block = it.invocation.args[1] as suspend () -> Any?
                    val result = block()
                    writableDb.setTransactionSuccessful()
                    result
                } finally {
                    writableDb.endTransaction()
                }
            }

            repository.update(account)

            val inOrder = inOrder(accountDao, accountAliasDao, writableDb)
            inOrder.verify(writableDb).beginTransaction()
            inOrder.verify(accountDao).getAccountByIdSync(1)
            inOrder.verify(accountDao).update(account)
            inOrder.verify(writableDb).setTransactionSuccessful()
            inOrder.verify(writableDb).endTransaction()
            verify(accountAliasDao, never()).insertAll(org.mockito.kotlin.any())
        }

    @Test
    fun `delete calls DAO`() =
        runTest {
            val account = Account(id = 1, name = "Test", type = "Bank")
            repository.delete(account)
            verify(accountDao).delete(account)
        }

    @Test
    fun `mergeAccounts delegates to MergeAccountsUseCase`() =
        runTest {
            val destinationId = 1
            val sourceIds = listOf(2, 3)

            repository.mergeAccounts(destinationId, sourceIds)

            verify(mergeAccountsUseCase).invoke(destinationId, sourceIds)
        }

    @Test
    fun `constructor with db creates instance successfully without coupling to other DAOs`() {
        val repoFromDb = AccountRepository(db)
        assertNotNull(repoFromDb)
    }

    @Test
    fun `mergeAccounts throws IllegalStateException when mergeAccountsUseCase is null`() =
        runTest {
            val repoWithoutUseCase = AccountRepository(accountDao, accountAliasDao, db, mergeAccountsUseCase = null)
            val exception =
                assertFailsWith<IllegalStateException> {
                    repoWithoutUseCase.mergeAccounts(1, listOf(2))
                }
            assertEquals("MergeAccountsUseCase must be provided to call mergeAccounts on AccountRepository", exception.message)
        }
}
