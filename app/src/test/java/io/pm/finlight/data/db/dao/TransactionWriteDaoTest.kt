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
class TransactionWriteDaoTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var writeDao: TransactionWriteDao
    private lateinit var queryDao: TransactionQueryDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var tagDao: TagDao

    private val account1 = Account(id = 1, name = "Savings", type = "Bank")
    private val account2 = Account(id = 2, name = "Credit Card", type = "Card")
    private val category1 = Category(id = 1, name = "Food", iconKey = "food", colorKey = "red")
    private val tag1 = Tag(id = 1, name = "Personal")
    private val tag2 = Tag(id = 2, name = "Trip")

    @Before
    fun setup() =
        runTest {
            writeDao = dbRule.db.transactionWriteDao()
            queryDao = dbRule.db.transactionQueryDao()
            accountDao = dbRule.db.accountDao()
            categoryDao = dbRule.db.categoryDao()
            tagDao = dbRule.db.tagDao()

            accountDao.insertAll(listOf(account1, account2))
            categoryDao.insertAll(listOf(category1))
            tagDao.insertAll(listOf(tag1, tag2))
        }

    @Test
    fun testInsertAndUpdateAndGetById() =
        runTest {
            val txn =
                Transaction(
                    description = "Groceries",
                    amount = 50.0,
                    date = System.currentTimeMillis(),
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = "Test Note",
                    source = "Manual"
                )
            val id = writeDao.insert(txn).toInt()
            val loaded = queryDao.getTransactionById(id).first()
            assertNotNull(loaded)
            assertEquals("Groceries", loaded.description)
            assertEquals(50.0, loaded.amount)

            val updated = loaded.copy(description = "Supermarket", amount = 75.0)
            writeDao.update(updated)

            val loadedUpdated = queryDao.getTransactionById(id).first()
            assertNotNull(loadedUpdated)
            assertEquals("Supermarket", loadedUpdated.description)
            assertEquals(75.0, loadedUpdated.amount)
        }

    @Test
    fun testDeleteTransaction() =
        runTest {
            val txn =
                Transaction(
                    description = "Dinner",
                    amount = 30.0,
                    date = System.currentTimeMillis(),
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    source = "Manual"
                )
            val id = writeDao.insert(txn).toInt()
            val loaded = queryDao.getTransactionById(id).first()
            assertNotNull(loaded)

            writeDao.delete(loaded)
            val afterDelete = queryDao.getTransactionById(id).first()
            assertNull(afterDelete)
        }

    @Test
    fun testPendingTransactionWorkflow() =
        runTest {
            val pendingTxn =
                Transaction(
                    description = "Electricity Bill",
                    amount = 120.0,
                    date = System.currentTimeMillis(),
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    status = TransactionStatus.PENDING,
                    recurringRuleId = 10,
                    source = "Recurring Rule"
                )
            val id = writeDao.insert(pendingTxn).toInt()

            val pendingList = queryDao.getPendingTransactions().first()
            assertEquals(1, pendingList.size)
            assertEquals(id, pendingList.first().id)

            writeDao.updateAmount(id, 150.0)
            val afterAmountUpdate = queryDao.getTransactionById(id).first()
            assertNotNull(afterAmountUpdate)
            assertEquals(150.0, afterAmountUpdate.amount)

            writeDao.confirmTransaction(id)
            val afterConfirm = queryDao.getTransactionById(id).first()
            assertNotNull(afterConfirm)
            assertEquals(TransactionStatus.CONFIRMED, afterConfirm.status)

            val pendingAfterConfirm = queryDao.getPendingTransactions().first()
            assertTrue(pendingAfterConfirm.isEmpty())

            // Test skip
            val anotherDraft =
                Transaction(
                    description = "Water Bill",
                    amount = 40.0,
                    date = System.currentTimeMillis(),
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    status = TransactionStatus.PENDING,
                    recurringRuleId = 11,
                    source = "Recurring Rule"
                )
            val draftId = writeDao.insert(anotherDraft).toInt()
            writeDao.skipTransaction(draftId)
            val skipped = queryDao.getTransactionById(draftId).first()
            assertNotNull(skipped)
            assertEquals(TransactionStatus.SKIPPED, skipped.status)
        }

    @Test
    fun testTagCrossRefOperations() =
        runTest {
            val txn =
                Transaction(
                    description = "Shopping",
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    source = "Manual"
                )
            val txnId = writeDao.insert(txn).toInt()

            val crossRefs =
                listOf(
                    TransactionTagCrossRef(txnId, tag1.id),
                    TransactionTagCrossRef(txnId, tag2.id),
                )
            writeDao.addTagsToTransaction(crossRefs)

            val tags = queryDao.getTagsForTransaction(txnId).first()
            assertEquals(2, tags.size)

            writeDao.clearTagsForTransaction(txnId)
            val tagsAfterClear = queryDao.getTagsForTransaction(txnId).first()
            assertTrue(tagsAfterClear.isEmpty())
        }

    @Test
    fun testReassignTransactions() =
        runTest {
            val txn1 =
                Transaction(
                    description = "Expense 1",
                    amount = 10.0,
                    date = System.currentTimeMillis(),
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    source = "Manual"
                )
            val id1 = writeDao.insert(txn1).toInt()

            writeDao.reassignTransactions(listOf(1), 2)
            val reassigned = queryDao.getTransactionById(id1).first()
            assertNotNull(reassigned)
            assertEquals(2, reassigned.accountId)
        }

    @Test
    fun testMarkAndUnmarkAsSplit() =
        runTest {
            val txn =
                Transaction(
                    description = "Team Lunch",
                    amount = 200.0,
                    date = System.currentTimeMillis(),
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    source = "Manual"
                )
            val id = writeDao.insert(txn).toInt()

            writeDao.markAsSplit(id, true)
            val splitTxn = queryDao.getTransactionById(id).first()
            assertNotNull(splitTxn)
            assertTrue(splitTxn.isSplit)

            writeDao.unmarkAsSplit(id, "Team Lunch Reverted", 1)
            val reverted = queryDao.getTransactionById(id).first()
            assertNotNull(reverted)
            assertEquals(false, reverted.isSplit)
            assertEquals("Team Lunch Reverted", reverted.description)
        }

    @Test
    fun testDeleteAll() =
        runTest {
            writeDao.insertAll(
                listOf(
                    Transaction(description = "A", amount = 1.0, date = System.currentTimeMillis(), transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual"),
                    Transaction(description = "B", amount = 2.0, date = System.currentTimeMillis(), transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
                )
            )
            val count = queryDao.getAllTransactionsSimple().first().size
            assertEquals(2, count)

            writeDao.deleteAll()
            val afterDelete = queryDao.getAllTransactionsSimple().first().size
            assertEquals(0, afterDelete)
        }
}
