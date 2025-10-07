package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.entity.Trip
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TripDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var tripDao: TripDao
    private lateinit var tagDao: TagDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() = runTest {
        tripDao = dbRule.db.tripDao()
        tagDao = dbRule.db.tagDao()
        transactionDao = dbRule.db.transactionDao()
        accountDao = dbRule.db.accountDao()
        categoryDao = dbRule.db.categoryDao()

        accountDao.insert(Account(id = 1, name = "Savings", type = "Bank"))
        categoryDao.insert(Category(id = 1, name = "Food", iconKey = "restaurant", colorKey = "red"))
        categoryDao.insert(Category(id = 2, name = "Shopping", iconKey = "shopping", colorKey = "blue"))
    }

    @Test
    fun `getAllTripsWithStats calculates totalSpend and transactionCount correctly`() = runTest {
        // Arrange
        val tripTagId = tagDao.insert(Tag(name = "Goa Trip")).toInt()
        tripDao.insert(Trip(name = "Goa Trip", startDate = 1000L, endDate = 2000L, tagId = tripTagId, tripType = TripType.DOMESTIC, currencyCode = null, conversionRate = null))

        val tx1Id = transactionDao.insert(Transaction(description = "Lunch", amount = 500.0, date = 1100L, transactionType = "expense", accountId = 1, categoryId = 1, notes = null)).toInt()
        val tx2Id = transactionDao.insert(Transaction(description = "Dinner", amount = 800.0, date = 1200L, transactionType = "expense", accountId = 1, categoryId = 1, notes = null)).toInt()
        val tx3Id = transactionDao.insert(Transaction(description = "Refund", amount = 100.0, date = 1300L, transactionType = "income", accountId = 1, categoryId = 1, notes = null)).toInt() // Should be ignored from total spend

        transactionDao.addTagsToTransaction(listOf(
            TransactionTagCrossRef(transactionId = tx1Id, tagId = tripTagId),
            TransactionTagCrossRef(transactionId = tx2Id, tagId = tripTagId),
            TransactionTagCrossRef(transactionId = tx3Id, tagId = tripTagId)
        ))

        // Act & Assert
        tripDao.getAllTripsWithStats().test {
            val trips = awaitItem()
            assertEquals(1, trips.size)
            val tripStats = trips.first()
            assertEquals("Goa Trip", tripStats.tripName)
            assertEquals(1300.0, tripStats.totalSpend, 0.01) // 500 + 800
            assertEquals(3, tripStats.transactionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllTripsWithStats handles split transactions correctly`() = runTest {
        // Arrange
        val tripTagId = tagDao.insert(Tag(name = "Europe Trip")).toInt()
        tripDao.insert(Trip(name = "Europe Trip", startDate = 1000L, endDate = 2000L, tagId = tripTagId, tripType = TripType.INTERNATIONAL, currencyCode = "EUR", conversionRate = 90.0f))

        val parentTxId = transactionDao.insert(Transaction(description = "Market Visit", amount = 9000.0, date = 1100L, transactionType = "expense", accountId = 1, categoryId = null, notes = null, isSplit = true)).toInt()
        transactionDao.addTagsToTransaction(listOf(TransactionTagCrossRef(transactionId = parentTxId, tagId = tripTagId)))
        dbRule.db.splitTransactionDao().insertAll(listOf(
            SplitTransaction(parentTransactionId = parentTxId, amount = 2700.0, categoryId = 1, notes = "Food"), // 30 EUR
            SplitTransaction(parentTransactionId = parentTxId, amount = 6300.0, categoryId = 2, notes = "Souvenirs") // 70 EUR
        ))

        // Act & Assert
        tripDao.getAllTripsWithStats().test {
            val trips = awaitItem()
            assertEquals(1, trips.size)
            val tripStats = trips.first()
            // Total spend should be the sum of splits, not the parent amount
            assertEquals(9000.0, tripStats.totalSpend, 0.01)
            // Transaction count should still be 1 (for the parent transaction)
            assertEquals(1, tripStats.transactionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isTagUsedByTrip returns correct count`() = runTest {
        // Arrange
        val usedTagId = tagDao.insert(Tag(name = "Used Tag")).toInt()
        val unusedTagId = tagDao.insert(Tag(name = "Unused Tag")).toInt()
        tripDao.insert(Trip(name = "Test Trip", startDate = 1L, endDate = 2L, tagId = usedTagId, tripType = TripType.DOMESTIC, currencyCode = null, conversionRate = null))

        // Act
        val isUsed = tripDao.isTagUsedByTrip(usedTagId) > 0
        val isUnused = tripDao.isTagUsedByTrip(unusedTagId) > 0

        // Assert
        assertTrue(isUsed)
        assertEquals(false, isUnused)
    }
}