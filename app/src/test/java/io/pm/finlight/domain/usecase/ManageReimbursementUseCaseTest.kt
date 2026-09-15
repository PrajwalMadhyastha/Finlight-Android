package io.pm.finlight.domain.usecase

import android.os.Build
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ManageReimbursementUseCaseTest : BaseViewModelTest() {
    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var transactionQueryDao: TransactionQueryDao

    @Mock
    private lateinit var transactionWriteDao: TransactionWriteDao

    @Mock
    private lateinit var transactionReimbursementDao: TransactionReimbursementDao

    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var useCase: ManageReimbursementUseCase

    @Before
    override fun setup() {
        super.setup()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)

        `when`(db.transactionQueryDao()).thenReturn(transactionQueryDao)
        `when`(db.transactionWriteDao()).thenReturn(transactionWriteDao)
        `when`(db.transactionReimbursementDao()).thenReturn(transactionReimbursementDao)

        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<AppDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }

        useCase =
            ManageReimbursementUseCase(
                transactionQueryDao = transactionQueryDao,
                transactionWriteDao = transactionWriteDao,
                transactionReimbursementDao = transactionReimbursementDao,
                db = db,
                dispatcherProvider = testDispatcherProvider,
            )
    }

    @After
    override fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `constructor with db initializes properly`() {
        val useCaseFromDb = ManageReimbursementUseCase(db, testDispatcherProvider)
        assertNotNull(useCaseFromDb)
    }

    // ── Link Reimbursement Tests ─────────────────────────────────────────────

    @Test
    fun `linkReimbursement exact repayment zeroes expense amount and links with null surplus`() =
        runTest {
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Dinner",
                    amount = 500.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Friend share",
                    amount = 500.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                )

            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao).linkReimbursement(incomeId = 2, expenseId = 1, surplusTxnId = null)
            verify(transactionWriteDao).updateAmount(1, 0.0)
            verify(transactionWriteDao, never()).insert(any())
            verify(transactionWriteDao, never()).updateAmount(2, 500.0)
        }

    @Test
    fun `linkReimbursement partial repayment reduces expense amount and links with null surplus`() =
        runTest {
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Dinner",
                    amount = 1000.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Friend share",
                    amount = 400.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                )

            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao).linkReimbursement(incomeId = 2, expenseId = 1, surplusTxnId = null)
            // 1000.0 - 400.0 = 600.0
            verify(transactionWriteDao).updateAmount(1, 600.0)
            verify(transactionWriteDao, never()).insert(any())
        }

    @Test
    fun `linkReimbursement over-repayment generates surplus transaction, offsets income, and zeroes expense`() =
        runTest {
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Lunch",
                    amount = 300.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 10,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Repayment",
                    amount = 500.0,
                    date = 2000L,
                    accountId = 2,
                    categoryId = 20,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                )

            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)
            `when`(transactionWriteDao.insert(any())).thenReturn(99L)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            val captor = argumentCaptor<Transaction>()
            verify(transactionWriteDao).insert(captor.capture())
            val surplus = captor.firstValue

            assertEquals("Repayment (Surplus)", surplus.description)
            assertEquals(200.0, surplus.amount, 0.001)
            assertEquals(2000L, surplus.date)
            assertEquals(2, surplus.accountId)
            assertEquals(20, surplus.categoryId)
            assertEquals(TransactionType.INCOME, surplus.transactionType)
            assertFalse(surplus.isExcluded)
            assertEquals("Surplus from repayment for Lunch", surplus.notes)
            assertEquals("Surplus Allocation", surplus.source)
            assertEquals(TransactionStatus.CONFIRMED, surplus.status)

            // Offset portion is 300.0 -> income updated to 300.0
            verify(transactionWriteDao).updateAmount(2, 300.0)
            // Reimbursement linked with surplusId = 99
            verify(transactionReimbursementDao).linkReimbursement(incomeId = 2, expenseId = 1, surplusTxnId = 99)
            // Expense fully settled to 0.0
            verify(transactionWriteDao).updateAmount(1, 0.0)
        }

    @Test
    fun `linkReimbursement returns early when income transaction does not exist`() =
        runTest {
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(null)
            `when`(
                transactionQueryDao.getTransactionByIdSync(1),
            ).thenReturn(
                Transaction(
                    id = 1,
                    description = "Expense",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                ),
            )

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
            verify(transactionWriteDao, never()).insert(any())
        }

    @Test
    fun `linkReimbursement returns early when expense transaction does not exist`() =
        runTest {
            `when`(
                transactionQueryDao.getTransactionByIdSync(2),
            ).thenReturn(
                Transaction(
                    id = 2,
                    description = "Income",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                ),
            )
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(null)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
            verify(transactionWriteDao, never()).insert(any())
        }

    @Test
    fun `linkReimbursement returns early when incomeId equals expenseId`() =
        runTest {
            useCase.linkReimbursement(incomeId = 1, expenseId = 1)

            verify(transactionQueryDao, never()).getTransactionByIdSync(any())
            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
        }

    @Test
    fun `linkReimbursement returns early when income transaction has non-INCOME type`() =
        runTest {
            val nonIncomeTxn =
                Transaction(
                    id = 2,
                    description = "Expense as Income",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(nonIncomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
        }

    @Test
    fun `linkReimbursement returns early when expense transaction has non-EXPENSE type`() =
        runTest {
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Income",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                )
            val nonExpenseTxn =
                Transaction(
                    id = 1,
                    description = "Income as Expense",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(nonExpenseTxn)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
        }

    @Test
    fun `linkReimbursement returns early when income is already linked to a parent expense`() =
        runTest {
            val alreadyLinkedIncome =
                Transaction(
                    id = 2,
                    description = "Income",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                    parentReimbursementId = 99,
                )
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(alreadyLinkedIncome)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
        }

    @Test
    fun `linkReimbursement returns early when income already has a linked surplus transaction`() =
        runTest {
            val incomeWithSurplus =
                Transaction(
                    id = 2,
                    description = "Income",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                    linkedSurplusTxnId = 55,
                )
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeWithSurplus)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
        }

    @Test
    fun `linkReimbursement returns early when expense amount is zero or negative`() =
        runTest {
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Income",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                )
            val expenseTxnZero =
                Transaction(
                    id = 1,
                    description = "Expense",
                    amount = 0.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxnZero)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
            verify(transactionWriteDao, never()).insert(any())
        }

    @Test
    fun `linkReimbursement returns early when income amount is zero or negative`() =
        runTest {
            val incomeTxnZero =
                Transaction(
                    id = 2,
                    description = "Income",
                    amount = 0.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                )
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxnZero)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionReimbursementDao, never()).linkReimbursement(any(), any(), any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
            verify(transactionWriteDao, never()).insert(any())
        }

    // ── Unlink Reimbursement Tests ───────────────────────────────────────────

    @Test
    fun `unlinkReimbursement without surplus restores original expense and unlinks reimbursement`() =
        runTest {
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Repayment",
                    amount = 500.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                    parentReimbursementId = 1,
                    linkedSurplusTxnId = null,
                )
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Dinner",
                    amount = 1000.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = "",
                )

            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.unlinkReimbursement(incomeId = 2)

            verify(transactionWriteDao).updateAmount(2, 500.0)
            verify(transactionReimbursementDao).unlinkReimbursement(2)
            // 1000.0 + 500.0 = 1500.0 restored to expense
            verify(transactionWriteDao).updateAmount(1, 1500.0)
            verify(transactionWriteDao, never()).delete(any())
        }

    @Test
    fun `unlinkReimbursement with linked surplus merges surplus back into income, deletes surplus, and restores expense`() =
        runTest {
            val surplusTxn =
                Transaction(
                    id = 99,
                    description = "Repayment (Surplus)",
                    amount = 200.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = null,
                )
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Repayment",
                    amount = 300.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = null,
                    parentReimbursementId = 1,
                    linkedSurplusTxnId = 99,
                )
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Lunch",
                    amount = 0.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = null,
                )

            `when`(transactionQueryDao.getTransactionByIdSync(99)).thenReturn(surplusTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.unlinkReimbursement(incomeId = 2)

            // Surplus transaction deleted
            verify(transactionWriteDao).delete(surplusTxn)
            // 300.0 + 200.0 = 500.0 restored to income
            verify(transactionWriteDao).updateAmount(2, 500.0)
            // Reimbursement unlinked in DAO
            verify(transactionReimbursementDao).unlinkReimbursement(2)
            // 0.0 + 300.0 = 300.0 restored to parent expense
            verify(transactionWriteDao).updateAmount(1, 300.0)
        }

    @Test
    fun `unlinkReimbursement with linked surplus ID but surplus not found in DB handles gracefully`() =
        runTest {
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Repayment",
                    amount = 300.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = null,
                    parentReimbursementId = 1,
                    linkedSurplusTxnId = 99,
                )
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Lunch",
                    amount = 0.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = null,
                )

            `when`(transactionQueryDao.getTransactionByIdSync(99)).thenReturn(null)
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            useCase.unlinkReimbursement(incomeId = 2)

            verify(transactionWriteDao, never()).delete(any())
            verify(transactionWriteDao).updateAmount(2, 300.0)
            verify(transactionReimbursementDao).unlinkReimbursement(2)
            verify(transactionWriteDao).updateAmount(1, 300.0)
        }

    @Test
    fun `unlinkReimbursement returns early when income transaction does not exist`() =
        runTest {
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(null)

            useCase.unlinkReimbursement(incomeId = 2)

            verify(transactionReimbursementDao, never()).unlinkReimbursement(any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
            verify(transactionWriteDao, never()).delete(any())
        }

    @Test
    fun `unlinkReimbursement returns early when income transaction has no parentReimbursementId`() =
        runTest {
            val unlinkedIncome =
                Transaction(
                    id = 2,
                    description = "Normal Income",
                    amount = 500.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                    parentReimbursementId = null,
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(unlinkedIncome)

            useCase.unlinkReimbursement(incomeId = 2)

            verify(transactionReimbursementDao, never()).unlinkReimbursement(any())
            verify(transactionWriteDao, never()).updateAmount(any(), any())
            verify(transactionWriteDao, never()).delete(any())
        }

    @Test
    fun `unlinkReimbursement when parent expense is deleted restores income and clears link`() =
        runTest {
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Repayment",
                    amount = 500.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = "",
                    parentReimbursementId = 1,
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(null)

            useCase.unlinkReimbursement(incomeId = 2)

            verify(transactionWriteDao).updateAmount(2, 500.0)
            verify(transactionReimbursementDao).unlinkReimbursement(2)
            verify(transactionWriteDao, never()).updateAmount(org.mockito.kotlin.eq(1), any())
            verify(transactionWriteDao, never()).delete(any())
        }

    @Test
    fun `unlinkReimbursement when parent expense is deleted with linked surplus merges surplus back and clears link`() =
        runTest {
            val surplusTxn =
                Transaction(
                    id = 99,
                    description = "Repayment (Surplus)",
                    amount = 200.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = null,
                )
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Repayment",
                    amount = 300.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.INCOME,
                    notes = null,
                    parentReimbursementId = 1,
                    linkedSurplusTxnId = 99,
                )
            `when`(transactionQueryDao.getTransactionByIdSync(99)).thenReturn(surplusTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(null)

            useCase.unlinkReimbursement(incomeId = 2)

            verify(transactionWriteDao).delete(surplusTxn)
            verify(transactionWriteDao).updateAmount(2, 500.0)
            verify(transactionReimbursementDao).unlinkReimbursement(2)
            verify(transactionWriteDao, never()).updateAmount(org.mockito.kotlin.eq(1), any())
        }
}
