// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/TransactionViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class has been updated to extend the new
// `BaseViewModelTest`. All boilerplate for JUnit rules, coroutine dispatchers,
// and Mockito initialization has been removed and is now inherited from the base
// class.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionViewModelTest : BaseViewModelTest() {

    private val applicationContext: Application = ApplicationProvider.getApplicationContext()

    // Mocks for all dependencies
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var tagRepository: TagRepository
    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var smsRepository: SmsRepository
    @Mock private lateinit var merchantRenameRuleRepository: MerchantRenameRuleRepository
    @Mock private lateinit var merchantCategoryMappingRepository: MerchantCategoryMappingRepository
    @Mock private lateinit var merchantMappingRepository: MerchantMappingRepository
    @Mock private lateinit var splitTransactionRepository: SplitTransactionRepository
    @Mock private lateinit var smsParseTemplateDao: SmsParseTemplateDao
    @Mock private lateinit var accountDao: AccountDao
    @Mock private lateinit var categoryDao: CategoryDao
    @Mock private lateinit var tagDao: TagDao
    @Mock private lateinit var transactionDao: TransactionDao

    private lateinit var viewModel: TransactionViewModel

    @Before
    override fun setup() {
        super.setup()

        // Mock DAO access from the AppDatabase mock
        `when`(db.accountDao()).thenReturn(accountDao)
        `when`(db.categoryDao()).thenReturn(categoryDao)
        `when`(db.tagDao()).thenReturn(tagDao)
        `when`(db.transactionDao()).thenReturn(transactionDao)

        // Setup default mock behaviors for ViewModel initialization
        `when`(transactionRepository.searchMerchants(anyString())).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
        `when`(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(emptyMap()))
        `when`(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(null))
        `when`(transactionRepository.getSpendingByCategoryForMonth(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getSpendingByMerchantForMonth(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(emptyList()))
        `when`(accountRepository.allAccounts).thenReturn(flowOf(emptyList()))
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        `when`(tagRepository.allTags).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getFirstTransactionDate()).thenReturn(flowOf(null))
        `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getOverallBudgetForMonth(anyInt(), anyInt())).thenReturn(flowOf(0f))

        // Create the ViewModel instance with all mocked dependencies
        viewModel = TransactionViewModel(
            application = applicationContext,
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
            smsParseTemplateDao = smsParseTemplateDao
        )
    }

    @Test
    fun `updateFilterKeyword updates filterState correctly`() = runTest {
        // ARRANGE
        val newKeyword = "Coffee"

        // ACT
        viewModel.updateFilterKeyword(newKeyword)

        // ASSERT
        val updatedState = viewModel.filterState.first()
        assertEquals(newKeyword, updatedState.keyword)
    }

    @Test
    fun `onAttemptToLeaveScreen prepares retro update sheet when changes are detected`() = runTest {
        // ARRANGE
        val initialTxn = Transaction(id = 1, description = "Starbucks", categoryId = 1, amount = 10.0, date = 0L, accountId = 1, notes = null, originalDescription = "Starbucks")
        val currentTxn = initialTxn.copy(description = "Starbucks Coffee") // Description has changed
        val similarTxns = listOf(Transaction(id = 2, description = "Starbucks", categoryId = 1, amount = 12.0, date = 0L, accountId = 1, notes = null))

        // Mock repository calls
        `when`(transactionRepository.getTransactionById(1)).thenReturn(flowOf(initialTxn), flowOf(currentTxn))
        `when`(transactionRepository.findSimilarTransactions("Starbucks", 1)).thenReturn(similarTxns)
        // Add mocks for the methods called inside loadTransactionForDetailScreen
        `when`(transactionRepository.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
        `when`(transactionRepository.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
        `when`(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
        `when`(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))


        // ACT
        viewModel.loadTransactionForDetailScreen(1)
        var hasNavigated = false
        viewModel.onAttemptToLeaveScreen { hasNavigated = true }
        advanceUntilIdle()

        // ASSERT
        val sheetState = viewModel.retroUpdateSheetState.first()
        assertNotNull("Sheet state should not be null", sheetState)
        assertEquals(false, hasNavigated) // Should not navigate yet
        assertEquals("Starbucks", sheetState?.originalDescription)
        assertEquals("Starbucks Coffee", sheetState?.newDescription)
        assertEquals(null, sheetState?.newCategoryId) // Category didn't change
        assertEquals(1, sheetState?.similarTransactions?.size)
        assertEquals(2, sheetState?.selectedIds?.first()) // The ID of the similar transaction
    }
}