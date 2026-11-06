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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionQueryDaoTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var queryDao: TransactionQueryDao
    private lateinit var writeDao: TransactionWriteDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var tagDao: TagDao

    private val account1 = Account(id = 1, name = "HDFC Bank", type = "Bank")
    private val category1 = Category(id = 1, name = "Food", iconKey = "food", colorKey = "red")
    private val tag1 = Tag(id = 1, name = "Office")

    @Before
    fun setup() =
        runTest {
            queryDao = dbRule.db.transactionQueryDao()
            writeDao = dbRule.db.transactionWriteDao()
            accountDao = dbRule.db.accountDao()
            categoryDao = dbRule.db.categoryDao()
            tagDao = dbRule.db.tagDao()

            accountDao.insertAll(listOf(account1))
            categoryDao.insertAll(listOf(category1))
            tagDao.insertAll(listOf(tag1))
        }

    @Test
    fun testGetRecentAndAllTransactions() =
        runTest {
            val t1 = Transaction(description = "Lunch", amount = 150.0, date = 1000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val t2 = Transaction(description = "Dinner", amount = 250.0, date = 2000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")

            val id1 = writeDao.insert(t1).toInt()
            val id2 = writeDao.insert(t2).toInt()

            val all = queryDao.getAllTransactions().first()
            assertEquals(2, all.size)
            // Descending date order
            assertEquals(id2, all[0].transaction.id)
            assertEquals(id1, all[1].transaction.id)

            val byId = queryDao.getTransactionById(id1).first()
            assertNotNull(byId)
            assertEquals(id1, byId.id)
        }

    @Test
    fun testSearchTransactions() =
        runTest {
            val t1 = Transaction(description = "Amazon Store", amount = 500.0, date = 1000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = "Laptop stand", source = "Manual")
            val t2 = Transaction(description = "Flipkart", amount = 300.0, date = 2000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")

            writeDao.insert(t1)
            writeDao.insert(t2)

            val searchResult =
                queryDao.searchTransactions(
                    keyword = "Amazon",
                    accountId = null,
                    categoryId = null,
                    tagId = null,
                    transactionType = null,
                    startDate = null,
                    endDate = null
                ).first()

            assertEquals(1, searchResult.size)
            assertEquals("Amazon Store", searchResult.first().transaction.description)
        }

    @Test
    fun testSmsHashAndSignatureQueries() =
        runTest {
            val t =
                Transaction(
                    description = "Swiggy",
                    amount = 350.0,
                    date = 5000L,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    source = "SMS",
                    sourceSmsHash = "hash123",
                    smsSignature = "sig_swiggy"
                )
            val id = writeDao.insert(t).toInt()

            val hashes = queryDao.getAllSmsHashes().first()
            assertEquals(listOf("hash123"), hashes)

            val hashesByIds = queryDao.getSmsHashesByIds(listOf(id))
            assertEquals(listOf("hash123"), hashesByIds)

            val since = queryDao.getTransactionsWithSignatureSince(1000L)
            assertEquals(1, since.size)
            assertEquals(id, since.first().id)

            val bySig = queryDao.getTransactionsBySignature("sig_swiggy")
            assertEquals(1, bySig.size)
            assertEquals(id, bySig.first().id)
        }

    @Test
    fun testMerchantVisitCount() =
        runTest {
            val t1 = Transaction(description = "Starbucks", amount = 300.0, date = 1000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val t2 = Transaction(description = "Starbucks", amount = 320.0, date = 2000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            writeDao.insert(t1)
            writeDao.insert(t2)

            val countFlow = queryDao.getTransactionCountForMerchant("Starbucks").first()
            assertEquals(2, countFlow)
        }

    @Test
    fun testMerchantTotalSpent() =
        runTest {
            val t1 = Transaction(description = "Starbucks", amount = 300.0, date = 1000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val t2 = Transaction(description = "Starbucks", amount = 320.0, date = 2000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val tIncome = Transaction(description = "Starbucks", amount = 1000.0, date = 3000L, transactionType = TransactionType.INCOME, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            writeDao.insert(t1)
            writeDao.insert(t2)
            writeDao.insert(tIncome)

            val totalSpentFlow = queryDao.getTotalSpentForMerchant("Starbucks").first()
            assertEquals(620.0, totalSpentFlow, 0.0)
        }

    @Test
    fun testFindRecentTransactionForMerge() =
        runTest {
            val t1 = Transaction(description = "Zomato", amount = 400.0, date = 5000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val id1 = writeDao.insert(t1).toInt()

            val recent =
                queryDao.findRecentTransactionForMerge(
                    merchant = "Zomato",
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    timeWindowStart = 1000L,
                    newTxnId = 999
                )
            assertNotNull(recent)
            assertEquals(id1, recent.id)
        }

    @Test
    fun testHasExecutedTransactionForRuleSince() =
        runTest {
            val ruleId = 42
            val cycleStart = 2000L

            // 1. Initially no transactions for ruleId 42
            assertFalse(queryDao.hasExecutedTransactionForRuleSince(ruleId, cycleStart))

            // 2. Insert transaction before cycle start
            val pastTxn =
                Transaction(
                    description = "Past Bill",
                    amount = 100.0,
                    date = 1000L,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    status = TransactionStatus.CONFIRMED,
                    recurringRuleId = ruleId,
                )
            writeDao.insert(pastTxn)
            assertFalse(queryDao.hasExecutedTransactionForRuleSince(ruleId, cycleStart))

            // 3. Insert PENDING transaction within cycle -> should still be false
            val pendingTxn =
                Transaction(
                    description = "Draft Bill",
                    amount = 100.0,
                    date = 3000L,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    status = TransactionStatus.PENDING,
                    recurringRuleId = ruleId,
                )
            writeDao.insert(pendingTxn)
            assertFalse(queryDao.hasExecutedTransactionForRuleSince(ruleId, cycleStart))

            // 4. Insert CONFIRMED transaction within cycle -> should return true
            val confirmedTxn =
                Transaction(
                    description = "Confirmed Bill",
                    amount = 100.0,
                    date = 2500L,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    status = TransactionStatus.CONFIRMED,
                    recurringRuleId = ruleId,
                )
            writeDao.insert(confirmedTxn)
            assertTrue(queryDao.hasExecutedTransactionForRuleSince(ruleId, cycleStart))

            // 5. Test another rule with SKIPPED transaction within cycle -> should also return true
            val skippedRuleId = 43
            assertFalse(queryDao.hasExecutedTransactionForRuleSince(skippedRuleId, cycleStart))
            val skippedTxn =
                Transaction(
                    description = "Skipped Bill",
                    amount = 50.0,
                    date = 2200L,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    status = TransactionStatus.SKIPPED,
                    recurringRuleId = skippedRuleId,
                )
            writeDao.insert(skippedTxn)
            assertTrue(queryDao.hasExecutedTransactionForRuleSince(skippedRuleId, cycleStart))
        }
}
