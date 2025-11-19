package io.pm.finlight.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.CategoryDao
import io.pm.finlight.SplitTransaction
import io.pm.finlight.SplitTransactionDao
import io.pm.finlight.SplitTransactionDetails
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitTransactionDaoTest : BaseDaoTest() {

    private lateinit var splitDao: SplitTransactionDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() {
        runBlocking {
            splitDao = db.splitTransactionDao()
            transactionDao = db.transactionDao()
            accountDao = db.accountDao()
            categoryDao = db.categoryDao()

            // Create prerequisites
            accountDao.insert(Account(id = 1, name = "Main", type = "Cash"))
            categoryDao.insert(Category(id = 1, name = "Food", iconKey = "a", colorKey = "b"))
            categoryDao.insert(Category(id = 2, name = "Fun", iconKey = "a", colorKey = "b"))
        }
    }

    @Test
    fun testCascadingDelete() {
        runBlocking {
            // 1. Create Parent Transaction
            val parentId = transactionDao.insert(
                Transaction(
                    description = "Supermarket",
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = null,
                    notes = null,
                    isSplit = true
                )
            ).toInt()

            // 2. Create Child Splits
            splitDao.insertAll(listOf(
                SplitTransaction(parentTransactionId = parentId, amount = 60.0, categoryId = 1, notes = "Groceries"),
                SplitTransaction(parentTransactionId = parentId, amount = 40.0, categoryId = 2, notes = "Magazines")
            ))

            // Verify splits exist
            var splits = splitDao.getSplitsForParent(parentId).first()
            assertEquals(2, splits.size)

            // 3. Delete Parent Transaction
            val parentTxnFlow = transactionDao.getTransactionById(parentId)
            val parentTxn = parentTxnFlow.first()

            assertNotNull(parentTxn)
            transactionDao.delete(parentTxn!!)

            // 4. Verify Splits are deleted
            splits = splitDao.getSplitsForParent(parentId).first()
            assertEquals("Splits should be deleted when parent is deleted", 0, splits.size)
        }
    }
}