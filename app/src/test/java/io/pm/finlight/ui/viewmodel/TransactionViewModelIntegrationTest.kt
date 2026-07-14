package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionViewModelIntegrationTest : BaseViewModelTest() {
    private lateinit var db: AppDatabase
    private lateinit var viewModel: TransactionViewModel

    // Real Repositories
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var merchantRenameRuleRepository: MerchantRenameRuleRepository
    private lateinit var merchantCategoryMappingRepository: MerchantCategoryMappingRepository
    private lateinit var merchantMappingRepository: MerchantMappingRepository
    private lateinit var splitTransactionRepository: SplitTransactionRepository

    // Mocked Repositories (Non-DB / External)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var smsRepository: SmsRepository

    @Before
    override fun setup() {
        super.setup()

        val context = ApplicationProvider.getApplicationContext<Application>()

        // 1. Initialize real in-memory Room Database
        db =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries() // Allowed for testing
                .build()

        // 2. Initialize Mock Repositories
        settingsRepository = mockk(relaxed = true)
        smsRepository = mockk(relaxed = true)

        // Setup default mocks for SettingsRepository so flows don't hang
        every { settingsRepository.getTravelModeSettings() } returns flowOf(null)
        every { settingsRepository.getPrivacyModeEnabled() } returns flowOf(false)
        every { settingsRepository.getOverallBudgetForMonth(any(), any()) } returns flowOf(0f)

        // 3. Initialize Real Repositories with DB DAOs
        tagRepository = TagRepository(db.tagDao(), db.transactionDao())
        transactionRepository =
            TransactionRepository(
                transactionDao = db.transactionDao(),
                settingsRepository = settingsRepository,
                tagRepository = tagRepository,
                deletedSmsHashDao = db.deletedSmsHashDao(),
                mergeRecordDao = db.mergeRecordDao(),
            )
        accountRepository = AccountRepository(db)
        categoryRepository = CategoryRepository(db.categoryDao())
        merchantRenameRuleRepository = MerchantRenameRuleRepository(db.merchantRenameRuleDao())
        merchantCategoryMappingRepository = MerchantCategoryMappingRepository(db.merchantCategoryMappingDao())
        merchantMappingRepository = MerchantMappingRepository(db.merchantMappingDao())
        splitTransactionRepository = SplitTransactionRepository(db.splitTransactionDao())

        // 4. Initialize ViewModel
        viewModel =
            TransactionViewModel(
                application = context,
                db = db,
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                tagRepository = tagRepository,
                settingsRepository = settingsRepository,
                smsRepository = smsRepository,
                merchantRenameRuleRepository = merchantRenameRuleRepository,
                merchantCategoryMappingRepository = merchantCategoryMappingRepository,
                merchantMappingRepository = merchantMappingRepository,
                splitTransactionRepository = splitTransactionRepository,
                smsParseTemplateDao = db.smsParseTemplateDao(),
            )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `add transaction successfully updates transactionsForSelectedMonth flow`() =
        runTest {
            // Arrange: Pre-seed required entities (Account and Category)
            db.accountDao().insert(Account(id = 1, name = "Test Wallet", type = "Wallet"))
            db.categoryDao().insert(Category(id = 1, name = "Food", iconKey = "food", colorKey = "red"))

            // Wait for initial flows to settle
            advanceUntilIdle()

            // Act: Save a new transaction using the ViewModel's method
            var onSaveCalled = false
            viewModel.onSaveTapped(
                description = "Test Integration Meal",
                amountStr = "120.50",
                accountId = 1,
                categoryId = 1,
                notes = "Delicious",
                date = System.currentTimeMillis(),
                transactionType = "expense",
                imageUris = emptyList(),
            ) {
                onSaveCalled = true
            }

            advanceUntilIdle()
            assertTrue("Callback should be invoked upon successful save", onSaveCalled)

            // Assert: The transactionsForSelectedMonth Flow should emit the new data from the DB
            viewModel.transactionsForSelectedMonth.test(timeout = 5.seconds) {
                // Turbine will emit the current state.
                // Depending on when it collects, it might emit an empty list first, then the populated list.
                var latestEmissions = awaitItem()

                // If the first emission is empty (initial state), wait for the next one containing the data
                if (latestEmissions.isEmpty()) {
                    latestEmissions = awaitItem()
                }

                assertEquals("Should have exactly 1 transaction", 1, latestEmissions.size)

                val savedTxnDetails = latestEmissions.first()
                assertEquals("Test Integration Meal", savedTxnDetails.transaction.description)
                assertEquals(120.50, savedTxnDetails.transaction.amount, 0.0)
                assertEquals("Delicious", savedTxnDetails.transaction.notes)
                assertEquals("Test Wallet", savedTxnDetails.accountName)
                assertEquals("Food", savedTxnDetails.categoryName)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `feature transaction merge correctly deletes child and records smsHash`() =
        runTest {
            // Arrange
            db.accountDao().insert(Account(id = 1, name = "Test Wallet", type = "Wallet"))
            db.categoryDao().insert(Category(id = 1, name = "Food", iconKey = "food", colorKey = "red"))

            val parentTxnId =
                transactionRepository.insertTransactionWithTagsAndImages(
                    Transaction(
                        description = "Parent",
                        amount = 100.0,
                        date = System.currentTimeMillis(),
                        accountId = 1,
                        categoryId = 1,
                        notes = "Parent note",
                        transactionType = "expense"
                    ),
                    emptySet(),
                    emptyList()
                ).toLong()

            val childTxnId =
                transactionRepository.insertTransactionWithTagsAndImages(
                    Transaction(
                        description = "Child",
                        amount = 50.0,
                        date = System.currentTimeMillis(),
                        accountId = 1,
                        categoryId = 1,
                        notes = "Child note",
                        transactionType = "expense",
                        sourceSmsHash = "xyz_hash_123"
                    ),
                    emptySet(),
                    emptyList()
                ).toLong()

            // Act
            transactionRepository.mergeTransactions(parentTxnId.toInt(), childTxnId.toInt())

            // Assert
            val deletedHashes = db.deletedSmsHashDao().getAllHashes()
            assertTrue("Deleted hash should be recorded", deletedHashes.contains("xyz_hash_123"))

            val parentTxn = transactionRepository.getTransactionSync(parentTxnId.toInt())
            assertNotNull(parentTxn)
            assertEquals(150.0, parentTxn?.amount ?: 0.0, 0.0)

            val childTxn = transactionRepository.getTransactionSync(childTxnId.toInt())
            assertNull("Child should be deleted", childTxn)
        }
}
