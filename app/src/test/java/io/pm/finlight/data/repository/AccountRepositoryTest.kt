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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private fun mockWithTransaction() {
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
    }

    @Test
    fun `findOrCreateByName returns existing account when found by exact name`() =
        runTest {
            mockWithTransaction()
            val existing = Account(id = 1, name = "HDFC", type = "Bank Account")
            `when`(accountAliasDao.findByAlias("HDFC")).thenReturn(null)
            `when`(accountDao.findByName("HDFC")).thenReturn(existing)

            val result = repository.findOrCreateByName("HDFC", AccountType.BANK)

            assertEquals(existing, result)
            verify(accountDao, never()).insert(org.mockito.kotlin.any())
        }

    @Test
    fun `findOrCreateByName resolves via alias when alias exists`() =
        runTest {
            mockWithTransaction()
            val alias = AccountAlias(aliasName = "HDFC-123", destinationAccountId = 2)
            val destinationAccount = Account(id = 2, name = "HDFC Main", type = "Bank Account")
            `when`(accountAliasDao.findByAlias("HDFC-123")).thenReturn(alias)
            `when`(accountDao.getAccountByIdSync(2)).thenReturn(destinationAccount)

            val result = repository.findOrCreateByName("HDFC-123")

            assertEquals(destinationAccount, result)
            verify(accountDao, never()).findByName(org.mockito.kotlin.any())
            verify(accountDao, never()).insert(org.mockito.kotlin.any())
        }

    @Test
    fun `findOrCreateByName falls back to name lookup when alias target account is null`() =
        runTest {
            mockWithTransaction()
            val alias = AccountAlias(aliasName = "OldAlias", destinationAccountId = 99)
            val created = Account(id = 3, name = "OldAlias", type = "Other")
            `when`(accountAliasDao.findByAlias("OldAlias")).thenReturn(alias)
            `when`(accountDao.getAccountByIdSync(99)).thenReturn(null)
            `when`(accountDao.findByName("OldAlias")).thenReturn(null)
            `when`(accountDao.insert(Account(name = "OldAlias", type = "Other"))).thenReturn(3L)
            `when`(accountDao.getAccountByIdSync(3)).thenReturn(created)

            val result = repository.findOrCreateByName("OldAlias")

            assertEquals(created, result)
            verify(accountDao).insert(Account(name = "OldAlias", type = "Other"))
        }

    @Test
    fun `findOrCreateByName creates new account with default AccountType OTHER`() =
        runTest {
            mockWithTransaction()
            val newAccount = Account(id = 5, name = "New Bank", type = "Other")
            `when`(accountAliasDao.findByAlias("New Bank")).thenReturn(null)
            `when`(accountDao.findByName("New Bank")).thenReturn(null)
            `when`(accountDao.insert(Account(name = "New Bank", type = "Other"))).thenReturn(5L)
            `when`(accountDao.getAccountByIdSync(5)).thenReturn(newAccount)

            val result = repository.findOrCreateByName("New Bank")

            assertEquals(newAccount, result)
            verify(accountDao).insert(Account(name = "New Bank", type = "Other"))
        }

    @Test
    fun `findOrCreateByName creates new account with custom string type`() =
        runTest {
            mockWithTransaction()
            val newAccount = Account(id = 6, name = "Wallet", type = "CustomWallet")
            `when`(accountAliasDao.findByAlias("Wallet")).thenReturn(null)
            `when`(accountDao.findByName("Wallet")).thenReturn(null)
            `when`(accountDao.insert(Account(name = "Wallet", type = "CustomWallet"))).thenReturn(6L)
            `when`(accountDao.getAccountByIdSync(6)).thenReturn(newAccount)

            val result = repository.findOrCreateByName("Wallet", "CustomWallet")

            assertEquals(newAccount, result)
            verify(accountDao).insert(Account(name = "Wallet", type = "CustomWallet"))
        }

    @Test
    fun `findOrCreateByName handles IGNORE conflict on insert by falling back to findByName`() =
        runTest {
            mockWithTransaction()
            val existing = Account(id = 7, name = "ConcurrentBank", type = "General")
            `when`(accountAliasDao.findByAlias("ConcurrentBank")).thenReturn(null)
            `when`(accountDao.findByName("ConcurrentBank"))
                .thenReturn(null)
                .thenReturn(existing)
            `when`(accountDao.insert(Account(name = "ConcurrentBank", type = "General"))).thenReturn(-1L)

            val result = repository.findOrCreateByName("ConcurrentBank", "General")

            assertEquals(existing, result)
            verify(accountDao, times(2)).findByName("ConcurrentBank")
        }

    @Test
    fun `findOrCreateByName throws IllegalStateException when insert conflict cannot find account`() =
        runTest {
            mockWithTransaction()
            `when`(accountAliasDao.findByAlias("GhostBank")).thenReturn(null)
            `when`(accountDao.findByName("GhostBank")).thenReturn(null)
            `when`(accountDao.insert(Account(name = "GhostBank", type = "General"))).thenReturn(-1L)

            assertFailsWith<IllegalStateException> {
                repository.findOrCreateByName("GhostBank", "General")
            }
        }

    @Test
    fun `findOrCreateByName throws IllegalArgumentException when name is blank`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                repository.findOrCreateByName("")
            }
            assertFailsWith<IllegalArgumentException> {
                repository.findOrCreateByName("   ")
            }
        }

    @Test
    fun `findOrCreateByName handles concurrent requests thread-safely`() =
        runTest {
            mockWithTransaction()
            val existing = Account(id = 10, name = "SharedBank", type = "Other")
            `when`(accountAliasDao.findByAlias("SharedBank")).thenReturn(null)
            `when`(accountDao.findByName("SharedBank")).thenReturn(existing)

            val results =
                coroutineScope {
                    (1..5).map {
                        async {
                            repository.findOrCreateByName("SharedBank")
                        }
                    }.awaitAll()
                }

            results.forEach { assertEquals(existing, it) }
        }
}
