// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/data/repository/TransactionRepositoryTest.kt
// =================================================================================
package io.pm.finlight.data.repository

import android.os.Build
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var transactionDao: TransactionDao

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var manageReimbursementUseCase: ManageReimbursementUseCase

    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var repository: TransactionRepository

    @Before
    override fun setup() {
        super.setup()

        testDispatcherProvider = TestDispatcherProvider(testDispatcher)

        // Mock withTransaction
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<AppDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }
    }

    @After
    override fun tearDown() {
        unmockkAll()
    }

    private fun setupDefaultPropertyMocks() {
        `when`(transactionDao.getAllTransactions()).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getRecentTransactionDetails()).thenReturn(flowOf(emptyList()))
    }

    // ── Self Transfer Repository Methods Tests ───────────────────────────────────

    @Test
    fun `findPotentialTransfers delegates to transactionDao with correct parameters`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val candidate =
                Transaction(
                    id = 2,
                    description = "Deposit",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            whenever(
                transactionDao.findPotentialTransfers(
                    eq(500.0),
                    eq(1),
                    eq(TransactionType.EXPENSE),
                    eq(100L),
                    eq(200L),
                ),
            ).thenReturn(listOf(candidate))

            val result = repository.findPotentialTransfers(500.0, 1, TransactionType.EXPENSE, 100L, 200L)

            assertEquals(listOf(candidate), result)
            verify(transactionDao).findPotentialTransfers(500.0, 1, TransactionType.EXPENSE, 100L, 200L)
        }

    @Test
    fun `linkTransfer updates both transactions link status atomically`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            repository.linkTransfer(1, 2)

            verify(transactionDao).updateTransferLinkStatus(1, 2, true)
            verify(transactionDao).updateTransferLinkStatus(2, 1, true)
        }

    // ── Reimbursement / Offset Feature Tests ──────────────────────────────────

    @Test
    fun `linkReimbursement delegates directly to ManageReimbursementUseCase`() =
        runTest {
            setupDefaultPropertyMocks()
            repository =
                TransactionRepository(
                    transactionDao = transactionDao,
                    db = db,
                    dispatcherProvider = testDispatcherProvider,
                    manageReimbursementUseCase = manageReimbursementUseCase,
                )

            repository.linkReimbursement(incomeId = 10, expenseId = 20)

            verify(manageReimbursementUseCase).linkReimbursement(10, 20)
        }

    @Test
    fun `unlinkReimbursement delegates directly to ManageReimbursementUseCase`() =
        runTest {
            setupDefaultPropertyMocks()
            repository =
                TransactionRepository(
                    transactionDao = transactionDao,
                    db = db,
                    dispatcherProvider = testDispatcherProvider,
                    manageReimbursementUseCase = manageReimbursementUseCase,
                )

            repository.unlinkReimbursement(incomeId = 10)

            verify(manageReimbursementUseCase).unlinkReimbursement(10)
        }

    // ── Tag and Image Operations Tests ─────────────────────────────────────────

    @Test
    fun `insertTransactionWithTags saves transaction and initial tags`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val transaction =
                Transaction(
                    description = "Test",
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
            val initialTags = setOf(Tag(id = 1, name = "Work"), Tag(id = 2, name = "Trip"))

            @Suppress("UNCHECKED_CAST")
            val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

            `when`(transactionDao.insert(anyObject())).thenReturn(10L)

            val newId = repository.insertTransactionWithTags(transaction, initialTags)

            assertEquals(10L, newId)
            verify(transactionDao).insert(transaction)
            verify(transactionDao).addTagsToTransaction(crossRefCaptor.capture() ?: emptyList())

            val capturedRefs = crossRefCaptor.value
            assertEquals(2, capturedRefs.size)
            assertTrue(capturedRefs.any { it.tagId == 1 && it.transactionId == 10 })
            assertTrue(capturedRefs.any { it.tagId == 2 && it.transactionId == 10 })
        }

    @Test
    fun `insertTransactionWithTags with empty tags does not add cross references`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val transaction = Transaction(description = "Test", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = null)
            `when`(transactionDao.insert(anyObject())).thenReturn(5L)

            val newId = repository.insertTransactionWithTags(transaction, emptySet())

            assertEquals(5L, newId)
            verify(transactionDao).insert(transaction)
            verify(transactionDao, never()).addTagsToTransaction(any())
        }

    @Test
    fun `updateTransactionWithTags updates transaction and replaces tags`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val transaction = Transaction(id = 7, description = "Updated", amount = 200.0, date = 1000L, accountId = 1, categoryId = 1, notes = null)
            val newTags = setOf(Tag(id = 5, name = "Personal"))

            @Suppress("UNCHECKED_CAST")
            val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

            repository.updateTransactionWithTags(transaction, newTags)

            verify(transactionDao).update(transaction)
            verify(transactionDao).clearTagsForTransaction(7)
            verify(transactionDao).addTagsToTransaction(crossRefCaptor.capture() ?: emptyList())

            val capturedRefs = crossRefCaptor.value
            assertEquals(1, capturedRefs.size)
            assertEquals(5, capturedRefs.first().tagId)
            assertEquals(7, capturedRefs.first().transactionId)
        }

    @Test
    fun `insertTransactionWithTagsAndImages saves transaction, tags, and images`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val transaction = Transaction(description = "Bill", amount = 350.0, date = 1000L, accountId = 1, categoryId = 1, notes = null)
            val tags = setOf(Tag(id = 1, name = "Receipt"))
            val imagePaths = listOf("path/image1.jpg", "path/image2.jpg")
            val newTxId = 42L

            `when`(transactionDao.insert(anyObject())).thenReturn(newTxId)

            @Suppress("UNCHECKED_CAST")
            val tagsCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>
            val imageCaptor = ArgumentCaptor.forClass(TransactionImage::class.java)

            val resultId = repository.insertTransactionWithTagsAndImages(transaction, tags, imagePaths)

            assertEquals(42L, resultId)
            verify(transactionDao).insert(transaction)
            verify(transactionDao).addTagsToTransaction(tagsCaptor.capture() ?: emptyList())
            assertEquals(1, tagsCaptor.value.size)
            assertEquals(42, tagsCaptor.value.first().transactionId)

            verify(transactionDao, times(2)).insertImage(imageCaptor.capture() ?: TransactionImage(transactionId = 0, imageUri = ""))
            val capturedImages = imageCaptor.allValues
            assertEquals(2, capturedImages.size)
            assertEquals("path/image1.jpg", capturedImages[0].imageUri)
            assertEquals(42, capturedImages[0].transactionId)
            assertEquals("path/image2.jpg", capturedImages[1].imageUri)
            assertEquals(42, capturedImages[1].transactionId)
        }

    @Test
    fun `updateTagsForTransaction clears and adds cross references`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val tags = setOf(Tag(id = 10, name = "Food"))

            @Suppress("UNCHECKED_CAST")
            val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

            repository.updateTagsForTransaction(3, tags)

            verify(transactionDao).clearTagsForTransaction(3)
            verify(transactionDao).addTagsToTransaction(crossRefCaptor.capture() ?: emptyList())
            assertEquals(10, crossRefCaptor.value.first().tagId)
            assertEquals(3, crossRefCaptor.value.first().transactionId)

            repository.updateTagsForTransaction(3, emptySet())
            verify(transactionDao, times(2)).clearTagsForTransaction(3)
        }

    // ── Queries and Data Calculations Tests ────────────────────────────────────

    @Test
    fun `getTotalExpensesSince returns value from DAO or 0_0 when null`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            `when`(transactionDao.getTotalExpensesSince(1000L)).thenReturn(450.0)
            `when`(transactionDao.getTotalExpensesSince(2000L)).thenReturn(null)

            assertEquals(450.0, repository.getTotalExpensesSince(1000L))
            assertEquals(0.0, repository.getTotalExpensesSince(2000L))
        }

    @Test
    fun `searchMerchants emits predictions flow from DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val predictions =
                listOf(
                    MerchantPrediction(
                        description = "Starbucks",
                        categoryId = 1,
                        categoryName = "Food",
                        categoryIconKey = "coffee",
                        categoryColorKey = "#FF0000",
                        accountId = 1,
                        accountName = "Bank",
                    ),
                )
            `when`(transactionDao.searchMerchants("star")).thenReturn(flowOf(predictions))

            repository.searchMerchants("star").test {
                assertEquals(predictions, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getImagesForTransaction emits image list from DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val images = listOf(TransactionImage(id = 1, transactionId = 5, imageUri = "uri/test.jpg"))
            `when`(transactionDao.getImagesForTransaction(5)).thenReturn(flowOf(images))

            repository.getImagesForTransaction(5).test {
                assertEquals(images, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getFinancialSummaryForRangeFlow emits summary from DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val summary = FinancialSummary(totalIncome = 5000.0, totalExpenses = 2000.0)
            `when`(transactionDao.getFinancialSummaryForRangeFlow(100L, 200L)).thenReturn(flowOf(summary))

            repository.getFinancialSummaryForRangeFlow(100L, 200L).test {
                assertEquals(summary, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getSpendingByCategoryForMonth emits category spending list`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val spending = listOf(CategorySpending(categoryName = "Utilities", totalAmount = 150.0, colorKey = "#FF0000", iconKey = "zap"))
            `when`(transactionDao.getSpendingByCategoryForMonth(100L, 200L, "bill", 1, 2, TransactionType.EXPENSE)).thenReturn(flowOf(spending))

            repository.getSpendingByCategoryForMonth(100L, 200L, "bill", 1, 2, TransactionType.EXPENSE).test {
                assertEquals(spending, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getMonthlyTrends emits trends from DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val trends = listOf(MonthlyTrend("2025-10", 3000.0, 1500.0))
            `when`(transactionDao.getMonthlyTrends(500L)).thenReturn(flowOf(trends))

            repository.getMonthlyTrends(500L).test {
                assertEquals(trends, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `allTransactions and recentTransactions emit data from DAO`() =
        runTest {
            val txnDetails =
                listOf(
                    TransactionDetails(
                        transaction = Transaction(id = 1, description = "Recent", amount = 50.0, date = 1000L, accountId = 1, categoryId = 1, notes = null),
                        images = emptyList(),
                        accountName = "Bank",
                        categoryName = "Food",
                        categoryIconKey = "coffee",
                        categoryColorKey = "#FF0000",
                        tagNames = null,
                    ),
                )
            `when`(transactionDao.getAllTransactions()).thenReturn(flowOf(txnDetails))
            `when`(transactionDao.getRecentTransactionDetails()).thenReturn(flowOf(txnDetails))

            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            repository.allTransactions.test {
                assertEquals(txnDetails, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            repository.recentTransactions.test {
                assertEquals(txnDetails, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `findRecentTransactionForMerge delegates to DAO with TransactionType`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            val expected = Transaction(id = 1, description = "Uber", amount = 200.0, date = 1000L, accountId = 1, categoryId = 1, transactionType = TransactionType.EXPENSE, notes = null)
            `when`(transactionDao.findRecentTransactionForMerge("Uber", 1, TransactionType.EXPENSE, 500L, 2)).thenReturn(expected)

            val result = repository.findRecentTransactionForMerge("Uber", 1, TransactionType.EXPENSE, 500L, 2)

            assertEquals(expected, result)
            verify(transactionDao).findRecentTransactionForMerge("Uber", 1, TransactionType.EXPENSE, 500L, 2)
        }

    @Test
    fun `getDistinctOriginalDescriptions and getTransactionIdsByOriginalDescription return data from DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db, testDispatcherProvider)

            `when`(transactionDao.getDistinctOriginalDescriptions()).thenReturn(listOf("Swiggy", "Zomato"))
            `when`(transactionDao.getTransactionIdsByOriginalDescription("Swiggy")).thenReturn(listOf(1, 2, 3))

            val descs = repository.getDistinctOriginalDescriptions()
            val ids = repository.getTransactionIdsByOriginalDescription("Swiggy")

            assertEquals(listOf("Swiggy", "Zomato"), descs)
            assertEquals(listOf(1, 2, 3), ids)
        }

    // ── Constructors and Dispatcher Injection Tests ────────────────────────────

    @Suppress("DEPRECATION")
    @Test
    fun `legacy constructor initializes TransactionRepository properly with DefaultDispatcherProvider`() =
        runTest {
            setupDefaultPropertyMocks()
            val repo = TransactionRepository(transactionDao, db)
            assertNotNull(repo)
            assertTrue(repo.dispatcherProvider is DefaultDispatcherProvider)
        }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy constructor injects custom DispatcherProvider properly`() =
        runTest {
            setupDefaultPropertyMocks()
            val customDispatcher = TestDispatcherProvider(testDispatcher)
            val repo =
                TransactionRepository(
                    transactionDao = transactionDao,
                    db = db,
                    dispatcherProvider = customDispatcher,
                )
            assertNotNull(repo)
            assertEquals(customDispatcher, repo.dispatcherProvider)
        }

    @Test
    fun `domain dao constructor injects custom DispatcherProvider properly`() =
        runTest {
            setupDefaultPropertyMocks()
            val customDispatcher = TestDispatcherProvider(testDispatcher)
            val repo =
                TransactionRepository(
                    transactionWriteDao = transactionDao,
                    transactionQueryDao = transactionDao,
                    transactionAnalyticsDao = transactionDao,
                    transactionReimbursementDao = transactionDao,
                    db = db,
                    dispatcherProvider = customDispatcher,
                )
            assertNotNull(repo)
            assertEquals(customDispatcher, repo.dispatcherProvider)
        }
}
