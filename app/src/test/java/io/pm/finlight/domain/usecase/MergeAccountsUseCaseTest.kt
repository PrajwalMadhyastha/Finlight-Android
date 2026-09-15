package io.pm.finlight.domain.usecase

import android.os.Build
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.pm.finlight.Account
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.GoalDao
import io.pm.finlight.RecurringPatternDao
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountAliasDao
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.dao.MergeRecordDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.db.entity.AccountAlias
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MergeAccountsUseCaseTest : BaseViewModelTest() {
    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var accountDao: AccountDao

    @Mock
    private lateinit var accountAliasDao: AccountAliasDao

    @Mock
    private lateinit var recurringTransactionDao: RecurringTransactionDao

    @Mock
    private lateinit var goalDao: GoalDao

    @Mock
    private lateinit var transactionWriteDao: TransactionWriteDao

    @Mock
    private lateinit var mergeRecordDao: MergeRecordDao

    @Mock
    private lateinit var recurringPatternDao: RecurringPatternDao

    @Mock
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Mock
    private lateinit var writableDb: SupportSQLiteDatabase

    private lateinit var useCase: MergeAccountsUseCase

    @Before
    override fun setup() {
        super.setup()

        `when`(db.accountDao()).thenReturn(accountDao)
        `when`(db.accountAliasDao()).thenReturn(accountAliasDao)
        `when`(db.recurringTransactionDao()).thenReturn(recurringTransactionDao)
        `when`(db.goalDao()).thenReturn(goalDao)
        `when`(db.transactionWriteDao()).thenReturn(transactionWriteDao)
        `when`(db.mergeRecordDao()).thenReturn(mergeRecordDao)
        `when`(db.recurringPatternDao()).thenReturn(recurringPatternDao)

        `when`(db.openHelper).thenReturn(openHelper)
        `when`(openHelper.writableDatabase).thenReturn(writableDb)
        `when`(db.transactionExecutor).thenReturn(testDispatcher.asExecutor())

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

        useCase =
            MergeAccountsUseCase(
                accountDao = accountDao,
                accountAliasDao = accountAliasDao,
                recurringTransactionDao = recurringTransactionDao,
                goalDao = goalDao,
                transactionWriteDao = transactionWriteDao,
                mergeRecordDao = mergeRecordDao,
                recurringPatternDao = recurringPatternDao,
                db = db,
            )
    }

    @After
    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    @Test
    fun `invoke executes all merge steps atomically in strict order`() =
        runTest {
            val destinationId = 1
            val sourceIds = listOf(2, 3)
            val destinationAccount = Account(id = 1, name = "Destination Account", type = "Bank")
            val sourceAccount2 = Account(id = 2, name = "Source Account 2", type = "Bank")
            val sourceAccount3 = Account(id = 3, name = "Source Account 3", type = "Card")

            `when`(accountDao.getAccountByIdSync(1)).thenReturn(destinationAccount)
            `when`(accountDao.getAccountByIdSync(2)).thenReturn(sourceAccount2)
            `when`(accountDao.getAccountByIdSync(3)).thenReturn(sourceAccount3)

            val aliasCaptor = argumentCaptor<List<AccountAlias>>()

            useCase(destinationId, sourceIds)

            val inOrder =
                inOrder(
                    writableDb,
                    accountAliasDao,
                    recurringTransactionDao,
                    goalDao,
                    transactionWriteDao,
                    mergeRecordDao,
                    recurringPatternDao,
                    accountDao,
                )

            inOrder.verify(writableDb).beginTransaction()
            inOrder.verify(accountAliasDao).insertAll(aliasCaptor.capture())
            inOrder.verify(accountAliasDao).reassignAliases(eq(sourceIds), eq(destinationId))
            inOrder.verify(recurringTransactionDao).reassignRecurringTransactions(eq(sourceIds), eq(destinationId))
            inOrder.verify(goalDao).reassignGoals(eq(sourceIds), eq(destinationId))
            inOrder.verify(transactionWriteDao).reassignTransactions(eq(sourceIds), eq(destinationId))
            inOrder.verify(mergeRecordDao).reassignChildAccount(eq(sourceIds), eq(destinationId))
            inOrder.verify(recurringPatternDao).reassignRecurringPatterns(eq(sourceIds), eq(destinationId))
            inOrder.verify(accountDao).deleteByIds(eq(sourceIds))
            inOrder.verify(writableDb).setTransactionSuccessful()
            inOrder.verify(writableDb).endTransaction()

            val capturedAliases = aliasCaptor.firstValue
            assertEquals(2, capturedAliases.size)
            assertTrue(
                capturedAliases.any {
                    it.aliasName == "Source Account 2" && it.destinationAccountId == destinationId
                },
            )
            assertTrue(
                capturedAliases.any {
                    it.aliasName == "Source Account 3" && it.destinationAccountId == destinationId
                },
            )
        }

    @Test
    fun `invoke filters out destinationAccountId if present in sourceAccountIds`() =
        runTest {
            val destinationId = 1
            val sourceIds = listOf(1, 2)
            val destinationAccount = Account(id = 1, name = "Destination Account", type = "Bank")
            val sourceAccount2 = Account(id = 2, name = "Source Account 2", type = "Bank")

            `when`(accountDao.getAccountByIdSync(1)).thenReturn(destinationAccount)
            `when`(accountDao.getAccountByIdSync(2)).thenReturn(sourceAccount2)

            val aliasCaptor = argumentCaptor<List<AccountAlias>>()

            useCase(destinationId, sourceIds)

            val expectedSources = listOf(2)
            val inOrder =
                inOrder(
                    writableDb,
                    accountAliasDao,
                    recurringTransactionDao,
                    goalDao,
                    transactionWriteDao,
                    mergeRecordDao,
                    recurringPatternDao,
                    accountDao,
                )

            inOrder.verify(writableDb).beginTransaction()
            inOrder.verify(accountAliasDao).insertAll(aliasCaptor.capture())
            inOrder.verify(accountAliasDao).reassignAliases(eq(expectedSources), eq(destinationId))
            inOrder.verify(recurringTransactionDao).reassignRecurringTransactions(eq(expectedSources), eq(destinationId))
            inOrder.verify(goalDao).reassignGoals(eq(expectedSources), eq(destinationId))
            inOrder.verify(transactionWriteDao).reassignTransactions(eq(expectedSources), eq(destinationId))
            inOrder.verify(mergeRecordDao).reassignChildAccount(eq(expectedSources), eq(destinationId))
            inOrder.verify(recurringPatternDao).reassignRecurringPatterns(eq(expectedSources), eq(destinationId))
            inOrder.verify(accountDao).deleteByIds(eq(expectedSources))
            inOrder.verify(writableDb).setTransactionSuccessful()
            inOrder.verify(writableDb).endTransaction()

            val capturedAliases = aliasCaptor.firstValue
            assertEquals(1, capturedAliases.size)
            assertEquals("Source Account 2", capturedAliases[0].aliasName)
            assertEquals(destinationId, capturedAliases[0].destinationAccountId)
        }

    @Test
    fun `invoke sanitizes duplicate sourceAccountIds with distinct`() =
        runTest {
            val destinationId = 1
            val sourceIdsWithDuplicates = listOf(2, 2, 3, 2)
            val destinationAccount = Account(id = 1, name = "Destination Account", type = "Bank")
            val sourceAccount2 = Account(id = 2, name = "Source Account 2", type = "Bank")
            val sourceAccount3 = Account(id = 3, name = "Source Account 3", type = "Card")

            `when`(accountDao.getAccountByIdSync(1)).thenReturn(destinationAccount)
            `when`(accountDao.getAccountByIdSync(2)).thenReturn(sourceAccount2)
            `when`(accountDao.getAccountByIdSync(3)).thenReturn(sourceAccount3)

            useCase(destinationId, sourceIdsWithDuplicates)

            val uniqueSources = listOf(2, 3)
            verify(accountAliasDao).reassignAliases(eq(uniqueSources), eq(destinationId))
            verify(recurringTransactionDao).reassignRecurringTransactions(eq(uniqueSources), eq(destinationId))
            verify(goalDao).reassignGoals(eq(uniqueSources), eq(destinationId))
            verify(transactionWriteDao).reassignTransactions(eq(uniqueSources), eq(destinationId))
            verify(mergeRecordDao).reassignChildAccount(eq(uniqueSources), eq(destinationId))
            verify(recurringPatternDao).reassignRecurringPatterns(eq(uniqueSources), eq(destinationId))
            verify(accountDao).deleteByIds(eq(uniqueSources))
        }

    @Test
    fun `invoke when destination account does not exist early returns without transaction`() =
        runTest {
            val destinationId = 999
            val sourceIds = listOf(2, 3)

            `when`(accountDao.getAccountByIdSync(destinationId)).thenReturn(null)

            useCase(destinationId, sourceIds)

            verify(writableDb, never()).beginTransaction()
            verify(accountAliasDao, never()).insertAll(any())
            verify(accountAliasDao, never()).reassignAliases(any(), any())
            verify(recurringTransactionDao, never()).reassignRecurringTransactions(any(), any())
            verify(goalDao, never()).reassignGoals(any(), any())
            verify(transactionWriteDao, never()).reassignTransactions(any(), any())
            verify(mergeRecordDao, never()).reassignChildAccount(any(), any())
            verify(recurringPatternDao, never()).reassignRecurringPatterns(any(), any())
            verify(accountDao, never()).deleteByIds(any())
        }

    @Test
    fun `invoke with empty sourceAccountIds early returns without transaction`() =
        runTest {
            useCase(1, emptyList())

            verify(writableDb, never()).beginTransaction()
            verify(accountAliasDao, never()).insertAll(any())
            verify(accountAliasDao, never()).reassignAliases(any(), any())
            verify(recurringTransactionDao, never()).reassignRecurringTransactions(any(), any())
            verify(goalDao, never()).reassignGoals(any(), any())
            verify(transactionWriteDao, never()).reassignTransactions(any(), any())
            verify(mergeRecordDao, never()).reassignChildAccount(any(), any())
            verify(recurringPatternDao, never()).reassignRecurringPatterns(any(), any())
            verify(accountDao, never()).deleteByIds(any())
        }

    @Test
    fun `invoke when sourceAccountIds only contains destinationAccountId early returns without transaction`() =
        runTest {
            useCase(1, listOf(1))

            verify(writableDb, never()).beginTransaction()
            verify(accountAliasDao, never()).insertAll(any())
            verify(accountAliasDao, never()).reassignAliases(any(), any())
            verify(recurringTransactionDao, never()).reassignRecurringTransactions(any(), any())
            verify(goalDao, never()).reassignGoals(any(), any())
            verify(transactionWriteDao, never()).reassignTransactions(any(), any())
            verify(mergeRecordDao, never()).reassignChildAccount(any(), any())
            verify(recurringPatternDao, never()).reassignRecurringPatterns(any(), any())
            verify(accountDao, never()).deleteByIds(any())
        }

    @Test
    fun `invoke when source accounts not found skips alias insertion but completes reassignments and deletion`() =
        runTest {
            val destinationId = 1
            val sourceIds = listOf(2, 3)
            val destinationAccount = Account(id = 1, name = "Destination Account", type = "Bank")

            `when`(accountDao.getAccountByIdSync(1)).thenReturn(destinationAccount)
            `when`(accountDao.getAccountByIdSync(2)).thenReturn(null)
            `when`(accountDao.getAccountByIdSync(3)).thenReturn(null)

            useCase(destinationId, sourceIds)

            val inOrder =
                inOrder(
                    writableDb,
                    accountAliasDao,
                    recurringTransactionDao,
                    goalDao,
                    transactionWriteDao,
                    mergeRecordDao,
                    recurringPatternDao,
                    accountDao,
                )

            inOrder.verify(writableDb).beginTransaction()
            inOrder.verify(accountAliasDao).reassignAliases(eq(sourceIds), eq(destinationId))
            inOrder.verify(recurringTransactionDao).reassignRecurringTransactions(eq(sourceIds), eq(destinationId))
            inOrder.verify(goalDao).reassignGoals(eq(sourceIds), eq(destinationId))
            inOrder.verify(transactionWriteDao).reassignTransactions(eq(sourceIds), eq(destinationId))
            inOrder.verify(mergeRecordDao).reassignChildAccount(eq(sourceIds), eq(destinationId))
            inOrder.verify(recurringPatternDao).reassignRecurringPatterns(eq(sourceIds), eq(destinationId))
            inOrder.verify(accountDao).deleteByIds(eq(sourceIds))
            inOrder.verify(writableDb).setTransactionSuccessful()
            inOrder.verify(writableDb).endTransaction()

            verify(accountAliasDao, never()).insertAll(any())
        }

    @Test
    fun `invoke propagates exception and does not mark transaction successful on failure`() =
        runTest {
            val destinationId = 1
            val sourceIds = listOf(2)
            val destinationAccount = Account(id = 1, name = "Destination Account", type = "Bank")

            `when`(accountDao.getAccountByIdSync(1)).thenReturn(destinationAccount)
            `when`(accountDao.getAccountByIdSync(2)).thenReturn(Account(id = 2, name = "Source 2", type = "Bank"))
            `when`(goalDao.reassignGoals(eq(sourceIds), eq(destinationId))).thenThrow(RuntimeException("DB failure"))

            assertThrows(RuntimeException::class.java) {
                kotlinx.coroutines.test.runTest {
                    useCase(destinationId, sourceIds)
                }
            }

            verify(writableDb).beginTransaction()
            verify(writableDb, never()).setTransactionSuccessful()
            verify(writableDb).endTransaction()
        }

    @Test
    fun `secondary constructor with AppDatabase initializes successfully`() {
        val secondaryUseCase = MergeAccountsUseCase(db)
        assertNotNull(secondaryUseCase)
    }
}
