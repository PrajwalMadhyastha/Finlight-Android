package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionReimbursementDaoTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var reimbursementDao: TransactionReimbursementDao
    private lateinit var writeDao: TransactionWriteDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    private val account1 = Account(id = 1, name = "Salary Account", type = "Bank")
    private val category1 = Category(id = 1, name = "Business", iconKey = "work", colorKey = "blue")

    @Before
    fun setup() =
        runTest {
            reimbursementDao = dbRule.db.transactionReimbursementDao()
            writeDao = dbRule.db.transactionWriteDao()
            accountDao = dbRule.db.accountDao()
            categoryDao = dbRule.db.categoryDao()

            accountDao.insertAll(listOf(account1))
            categoryDao.insertAll(listOf(category1))
        }

    @Test
    fun testReimbursementLinking() =
        runTest {
            val expenseTxn =
                Transaction(
                    description = "Flight Ticket",
                    amount = 5000.0,
                    date = 1000L,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    source = "Manual"
                )
            val incomeTxn =
                Transaction(
                    description = "Flight Reimbursement",
                    amount = 5000.0,
                    date = 2000L,
                    transactionType = TransactionType.INCOME,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    source = "Manual"
                )

            val expenseId = writeDao.insert(expenseTxn).toInt()
            val incomeId = writeDao.insert(incomeTxn).toInt()

            val candidates = reimbursementDao.getCandidateReimbursements(excludeExpenseId = expenseId).first()
            assertEquals(1, candidates.size)
            assertEquals(incomeId, candidates.first().transaction.id)

            // Link them
            reimbursementDao.linkReimbursement(incomeId = incomeId, expenseId = expenseId)

            val reimbursementsForExpense = reimbursementDao.getReimbursementsForExpense(expenseId).first()
            assertEquals(1, reimbursementsForExpense.size)
            assertEquals(incomeId, reimbursementsForExpense.first().transaction.id)

            val linkedExpense = reimbursementDao.getLinkedExpenseForReimbursement(incomeId).first()
            assertNotNull(linkedExpense)
            assertEquals(expenseId, linkedExpense.transaction.id)

            val countSync = reimbursementDao.getReimbursementsCountSync(expenseId)
            assertEquals(1, countSync)

            // Unlink
            reimbursementDao.unlinkReimbursement(incomeId = incomeId)
            val unlinkedAfter = reimbursementDao.getLinkedExpenseForReimbursement(incomeId).first()
            assertNull(unlinkedAfter)
        }

    @Test
    fun testReimbursementsWhenNoneExist() =
        runTest {
            val reimbursements = reimbursementDao.getReimbursementsForExpense(999).first()
            assertTrue(reimbursements.isEmpty())

            val count = reimbursementDao.getReimbursementsCountSync(999)
            assertEquals(0, count)

            val linked = reimbursementDao.getLinkedExpenseForReimbursement(999).first()
            assertNull(linked)
        }
}
