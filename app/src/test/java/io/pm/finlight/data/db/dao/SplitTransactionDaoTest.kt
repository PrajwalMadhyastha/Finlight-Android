package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SplitTransactionDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var splitTransactionDao: SplitTransactionDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var accountDao: AccountDao

    private val foodCategory = Category(id = 1, name = "Food", iconKey = "restaurant", colorKey = "red")
    private val shoppingCategory = Category(id = 2, name = "Shopping", iconKey = "shopping", colorKey = "blue")

    @Before
    fun setup() = runTest {
        splitTransactionDao = dbRule.db.splitTransactionDao()
        transactionDao = dbRule.db.transactionDao()
        categoryDao = dbRule.db.categoryDao()
        accountDao = dbRule.db.accountDao()

        accountDao.insert(Account(id = 1, name = "Savings", type = "Bank"))
        categoryDao.insertAll(listOf(foodCategory, shoppingCategory))
    }

    @Test
    fun `getSplitsForParent returns correctly joined details`() = runTest {
        // Arrange
        val parentId = transactionDao.insert(Transaction(description = "Parent", amount = 150.0, date = 0L, accountId = 1, categoryId = null, isSplit = true, notes = null)).toInt()
        val splits = listOf(
            SplitTransaction(parentTransactionId = parentId, amount = 100.0, categoryId = 1, notes = "Lunch"),
            SplitTransaction(parentTransactionId = parentId, amount = 50.0, categoryId = 2, notes = "Snacks")
        )
        splitTransactionDao.insertAll(splits)

        // Act & Assert
        splitTransactionDao.getSplitsForParent(parentId).test {
            val details = awaitItem()
            assertEquals(2, details.size)

            val foodSplit = details.find { it.splitTransaction.categoryId == 1 }
            assertNotNull(foodSplit)
            assertEquals("Food", foodSplit?.categoryName)
            assertEquals("restaurant", foodSplit?.categoryIconKey)
            assertEquals("red", foodSplit?.categoryColorKey)

            val shoppingSplit = details.find { it.splitTransaction.categoryId == 2 }
            assertNotNull(shoppingSplit)
            assertEquals("Shopping", shoppingSplit?.categoryName)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteSplitsForParent removes all children of a transaction`() = runTest {
        // Arrange
        val parentId1 = transactionDao.insert(Transaction(description = "Parent 1", amount = 150.0, date = 0L, accountId = 1, categoryId = null, isSplit = true, notes = null)).toInt()
        val parentId2 = transactionDao.insert(Transaction(description = "Parent 2", amount = 200.0, date = 0L, accountId = 1, categoryId = null, isSplit = true, notes = null)).toInt()

        splitTransactionDao.insertAll(listOf(
            SplitTransaction(parentTransactionId = parentId1, amount = 100.0, categoryId = 1, notes = null),
            SplitTransaction(parentTransactionId = parentId1, amount = 50.0, categoryId = 2, notes = null),
            SplitTransaction(parentTransactionId = parentId2, amount = 200.0, categoryId = 1, notes = null)
        ))

        // Act
        splitTransactionDao.deleteSplitsForParent(parentId1)

        // Assert
        splitTransactionDao.getSplitsForParent(parentId1).test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
        splitTransactionDao.getSplitsForParent(parentId2).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
