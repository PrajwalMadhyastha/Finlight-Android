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
import io.pm.finlight.data.db.entity.MergeType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var mergeTransactionsUseCase: io.pm.finlight.domain.usecase.MergeTransactionsUseCase

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
        tagRepository = TagRepository(db.tagDao(), db.transactionQueryDao())
        transactionRepository =
            TransactionRepository(
                transactionWriteDao = db.transactionWriteDao(),
                transactionQueryDao = db.transactionQueryDao(),
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
                transactionReimbursementDao = db.transactionReimbursementDao(),
                db = db,
            )
        accountRepository = AccountRepository(db)
        categoryRepository = CategoryRepository(db.categoryDao())
        merchantRenameRuleRepository = MerchantRenameRuleRepository(db.merchantRenameRuleDao())
        merchantCategoryMappingRepository = MerchantCategoryMappingRepository(db.merchantCategoryMappingDao())
        merchantMappingRepository = MerchantMappingRepository(db.merchantMappingDao())
        splitTransactionRepository = SplitTransactionRepository(db.splitTransactionDao())
        mergeTransactionsUseCase =
            io.pm.finlight.domain.usecase.MergeTransactionsUseCase(
                transactionQueryDao = db.transactionQueryDao(),
                transactionWriteDao = db.transactionWriteDao(),
                transactionReimbursementDao = db.transactionReimbursementDao(),
                mergeRecordDao = db.mergeRecordDao(),
                deletedSmsHashDao = db.deletedSmsHashDao(),
                db = db,
            )

        // 4. Initialize ViewModel
        val resolveTravelModeTagUseCase = io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase(tagRepository)
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
                resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
                mergeTransactionsUseCase = mergeTransactionsUseCase,
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
                        transactionType = TransactionType.EXPENSE
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
                        transactionType = TransactionType.EXPENSE,
                        sourceSmsHash = "xyz_hash_123"
                    ),
                    emptySet(),
                    emptyList()
                ).toLong()

            // Act
            mergeTransactionsUseCase(parentTxnId.toInt(), childTxnId.toInt())

            // Assert
            val deletedHashes = db.deletedSmsHashDao().getAllHashes()
            assertTrue("Deleted hash should be recorded", deletedHashes.contains("xyz_hash_123"))

            val parentTxn = transactionRepository.getTransactionSync(parentTxnId.toInt())
            assertNotNull(parentTxn)
            assertEquals(150.0, parentTxn?.amount ?: 0.0, 0.0)

            val childTxn = transactionRepository.getTransactionSync(childTxnId.toInt())
            assertNull("Child should be deleted", childTxn)
        }

    // ── Manual Merge Integration Tests ───────────────────────────────────────
    //
    // These tests exercise manualMergeTransactions / unmergeTransactions against
    // the real in-memory Room database, covering FK constraints, the mergeGroupId
    // index, and the cascade delete on the merge_records table.

    @Test
    fun `integration manualMergeTransactions merges N children into anchor in real DB`() =
        runTest {
            // Arrange
            db.accountDao().insert(Account(id = 1, name = "Test Wallet", type = "Wallet"))
            db.categoryDao().insert(Category(id = 1, name = "Food", iconKey = "food", colorKey = "red"))

            val anchorId =
                transactionRepository.insertTransactionWithTagsAndImages(
                    Transaction(
                        description = "Anchor",
                        amount = 100.0,
                        date = System.currentTimeMillis() - 3000L,
                        accountId = 1,
                        categoryId = 1,
                        notes = "anchor-note",
                        transactionType = TransactionType.EXPENSE,
                    ),
                    emptySet(),
                    emptyList(),
                ).toInt()

            val child1Id =
                transactionRepository.insertTransactionWithTagsAndImages(
                    Transaction(
                        description = "Child One",
                        amount = 60.0,
                        date = System.currentTimeMillis() - 2000L,
                        accountId = 1,
                        categoryId = 1,
                        notes = "child-note-1",
                        transactionType = TransactionType.EXPENSE,
                        sourceSmsHash = "sms_hash_child1",
                    ),
                    emptySet(),
                    emptyList(),
                ).toInt()

            val child2Id =
                transactionRepository.insertTransactionWithTagsAndImages(
                    Transaction(
                        description = "Child Two",
                        amount = 40.0,
                        date = System.currentTimeMillis() - 1000L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                        transactionType = TransactionType.EXPENSE,
                    ),
                    emptySet(),
                    emptyList(),
                ).toInt()

            // Act
            mergeTransactionsUseCase.manualMerge(anchorId, listOf(child1Id, child2Id))

            advanceUntilIdle()

            // Assert: anchor amount = 100 + 60 + 40 = 200 (all expense)
            val anchor = transactionRepository.getTransactionSync(anchorId)
            assertNotNull("Anchor must still exist", anchor)
            assertEquals("Net amount must be summed", 200.0, anchor!!.amount, 0.001)

            // Assert: both children are deleted
            assertNull("Child 1 must be deleted", transactionRepository.getTransactionSync(child1Id))
            assertNull("Child 2 must be deleted", transactionRepository.getTransactionSync(child2Id))

            // Assert: two MergeRecords written, sharing a non-blank groupId
            val records = db.mergeRecordDao().getAll()
            assertEquals("Two MergeRecords should exist", 2, records.size)
            val groupIds = records.map { it.mergeGroupId }.toSet()
            assertEquals("All records share one non-blank groupId", 1, groupIds.size)
            assertTrue("groupId must not be blank", groupIds.first().isNotBlank())
            assertTrue("All records have mergeType=MANUAL", records.all { it.mergeType == MergeType.MANUAL })

            // Assert: child SMS hash recorded in deny-list
            val deletedHashes = db.deletedSmsHashDao().getAllHashes()
            assertTrue("SMS hash of child1 should be in deny-list", deletedHashes.contains("sms_hash_child1"))

            // Assert: anchor notes contain the structured [Merged] prefix for each child
            val anchorNotes = anchor.notes ?: ""
            assertTrue("Notes should contain [Merged] tag for Child One", anchorNotes.contains("[Merged] Child One"))
            assertTrue("Notes should contain [Merged] tag for Child Two", anchorNotes.contains("[Merged] Child Two"))
        }

    @Test
    fun `integration unmergeTransactions fully reverses a manual merge in real DB`() =
        runTest {
            // Arrange: seed and merge first
            db.accountDao().insert(Account(id = 1, name = "Test Wallet", type = "Wallet"))
            db.categoryDao().insert(Category(id = 1, name = "Food", iconKey = "food", colorKey = "red"))

            val anchorId =
                transactionRepository.insertTransactionWithTagsAndImages(
                    Transaction(
                        description = "Anchor",
                        amount = 200.0,
                        date = System.currentTimeMillis() - 2000L,
                        accountId = 1,
                        categoryId = 1,
                        notes = "original-note",
                        transactionType = TransactionType.EXPENSE,
                    ),
                    emptySet(),
                    emptyList(),
                ).toInt()

            val child1Id =
                transactionRepository.insertTransactionWithTagsAndImages(
                    Transaction(
                        description = "Child A",
                        amount = 75.0,
                        date = System.currentTimeMillis() - 1000L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                        transactionType = TransactionType.EXPENSE,
                        sourceSmsHash = "undo_hash_A",
                    ),
                    emptySet(),
                    emptyList(),
                ).toInt()

            val child2Id =
                transactionRepository.insertTransactionWithTagsAndImages(
                    Transaction(
                        description = "Child B",
                        amount = 25.0,
                        date = System.currentTimeMillis(),
                        accountId = 1,
                        categoryId = 1,
                        notes = "child-b-note",
                        transactionType = TransactionType.EXPENSE,
                    ),
                    emptySet(),
                    emptyList(),
                ).toInt()

            mergeTransactionsUseCase.manualMerge(anchorId, listOf(child1Id, child2Id))
            advanceUntilIdle()

            // Verify pre-condition: anchor exists, children deleted
            assertEquals(300.0, transactionRepository.getTransactionSync(anchorId)!!.amount, 0.001)
            assertNull(transactionRepository.getTransactionSync(child1Id))
            assertNull(transactionRepository.getTransactionSync(child2Id))

            // Act: unmerge
            mergeTransactionsUseCase.unmerge(anchorId)
            advanceUntilIdle()

            // Assert: anchor restored to original values
            val restoredAnchor = transactionRepository.getTransactionSync(anchorId)
            assertNotNull("Anchor must still exist after unmerge", restoredAnchor)
            assertEquals("Anchor amount must be restored", 200.0, restoredAnchor!!.amount, 0.001)
            assertEquals("Anchor notes must be restored", "original-note", restoredAnchor.notes)

            // Assert: both children re-inserted with correct descriptions
            val allTxns = db.transactionQueryDao().getAllTransactionsSimple().first()
            val childDescs = allTxns.map { it.description }
            assertTrue("Child A must be re-inserted", childDescs.contains("Child A"))
            assertTrue("Child B must be re-inserted", childDescs.contains("Child B"))

            // Assert: all MergeRecords cleaned up
            assertEquals("No MergeRecords should remain", 0, db.mergeRecordDao().getAll().size)

            // Assert: SMS deny-list entry for child A removed
            assertFalse(
                "SMS hash should be removed from deny-list after unmerge",
                db.deletedSmsHashDao().getAllHashes().contains("undo_hash_A"),
            )
        }
}
