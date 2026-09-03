package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

import io.pm.finlight.utils.TestDispatcherProvider

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionRepositoryDomainDaoTest {
    private val writeDao: TransactionWriteDao = mockk(relaxed = true)
    private val queryDao: TransactionQueryDao = mockk(relaxed = true)
    private val analyticsDao: TransactionAnalyticsDao = mockk(relaxed = true)
    private val reimbursementDao: TransactionReimbursementDao = mockk(relaxed = true)
    private val db: AppDatabase = mockk(relaxed = true)
    private val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var repository: TransactionRepository

    @Before
    fun setup() {
        every { queryDao.getAllTransactions() } returns flowOf(emptyList())

        repository =
            TransactionRepository(
                transactionWriteDao = writeDao,
                transactionQueryDao = queryDao,
                transactionAnalyticsDao = analyticsDao,
                transactionReimbursementDao = reimbursementDao,
                db = db,
                dispatcherProvider = testDispatcherProvider,
            )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDelegationToWriteDao() =
        runTest {
            val txn =
                Transaction(
                    id = 1,
                    description = "Test Txn",
                    amount = 100.0,
                    date = 1000L,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    source = "Manual",
                )
            coEvery { writeDao.insert(any()) } returns 10L

            val id = repository.insertTransactionWithTags(txn, emptySet())
            assertEquals(10L, id)
            coVerify(exactly = 1) { writeDao.insert(txn) }

            repository.updateTransactionWithTags(txn, emptySet())
            coVerify(exactly = 1) { writeDao.update(txn) }

            repository.delete(txn)
            coVerify(exactly = 1) { writeDao.delete(txn) }
        }

    @Test
    fun testDelegationToQueryDao() =
        runTest {
            every { queryDao.getFirstTransactionDate() } returns flowOf(123456L)

            val result = repository.getFirstTransactionDate().first()
            assertEquals(123456L, result)
            verify(exactly = 1) { queryDao.getFirstTransactionDate() }
        }

    @Test
    fun testDelegationToAnalyticsDao() =
        runTest {
            val summary = FinancialSummary(totalIncome = 500.0, totalExpenses = 200.0)
            every { analyticsDao.getFinancialSummaryForRangeFlow(any(), any()) } returns flowOf(summary)

            val result = repository.getFinancialSummaryForRangeFlow(100L, 200L).first()
            assertNotNull(result)
            assertEquals(500.0, result.totalIncome)
            assertEquals(200.0, result.totalExpenses)
            verify(exactly = 1) { analyticsDao.getFinancialSummaryForRangeFlow(100L, 200L) }
        }

    @Test
    fun testDelegationToReimbursementDao() =
        runTest {
            val incomeTxn = Transaction(id = 1, description = "Income", amount = 50.0, date = 1000L, accountId = 1, categoryId = 1, transactionType = TransactionType.INCOME, notes = null, parentReimbursementId = 2)
            val expenseTxn = Transaction(id = 2, description = "Expense", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, transactionType = TransactionType.EXPENSE, notes = null)

            coEvery { queryDao.getTransactionByIdSync(1) } returns incomeTxn
            coEvery { queryDao.getTransactionByIdSync(2) } returns expenseTxn
            coJustRun { reimbursementDao.linkReimbursement(any(), any(), any()) }

            repository.linkReimbursement(1, 2)
            coVerify(exactly = 1) { reimbursementDao.linkReimbursement(1, 2, null) }

            coJustRun { reimbursementDao.unlinkReimbursement(any()) }
            repository.unlinkReimbursement(1)
            coVerify(exactly = 1) { reimbursementDao.unlinkReimbursement(1) }
        }
}
