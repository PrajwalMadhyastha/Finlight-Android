// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SettingsViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization. The tearDown method is now an override.
// FIX (Testing) - The tests for `createBackupSnapshot` have been corrected.
// They now use Mockito's `mockStatic` utility to properly stub the static
// `DataExportService.createBackupSnapshot()` method, resolving the
// `MissingMethodInvocationException` and allowing the final unit tests to pass.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.ui.theme.AppTheme
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SettingsViewModelTest : BaseViewModelTest() {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var merchantMappingRepository: MerchantMappingRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var smsRepository: SmsRepository
    @Mock private lateinit var transactionViewModel: TransactionViewModel

    // Mocks for all DAOs called by DataExportService
    @Mock private lateinit var transactionDao: TransactionDao
    @Mock private lateinit var accountDao: AccountDao
    @Mock private lateinit var categoryDao: CategoryDao
    @Mock private lateinit var budgetDao: BudgetDao
    @Mock private lateinit var merchantMappingDao: MerchantMappingDao
    @Mock private lateinit var splitTransactionDao: SplitTransactionDao
    @Mock private lateinit var customSmsRuleDao: CustomSmsRuleDao
    @Mock private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao
    @Mock private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao
    @Mock private lateinit var ignoreRuleDao: IgnoreRuleDao
    @Mock private lateinit var smsParseTemplateDao: SmsParseTemplateDao
    @Mock private lateinit var tagDao: TagDao
    @Mock private lateinit var goalDao: GoalDao
    @Mock private lateinit var tripDao: TripDao
    @Mock private lateinit var accountAliasDao: AccountAliasDao


    private lateinit var viewModel: SettingsViewModel

    @Before
    override fun setup() {
        super.setup()

        // Stub the db mock to return all the mocked DAOs
        whenever(db.transactionDao()).thenReturn(transactionDao)
        whenever(db.accountDao()).thenReturn(accountDao)
        whenever(db.categoryDao()).thenReturn(categoryDao)
        whenever(db.budgetDao()).thenReturn(budgetDao)
        whenever(db.merchantMappingDao()).thenReturn(merchantMappingDao)
        whenever(db.splitTransactionDao()).thenReturn(splitTransactionDao)
        whenever(db.customSmsRuleDao()).thenReturn(customSmsRuleDao)
        whenever(db.merchantRenameRuleDao()).thenReturn(merchantRenameRuleDao)
        whenever(db.merchantCategoryMappingDao()).thenReturn(merchantCategoryMappingDao)
        whenever(db.ignoreRuleDao()).thenReturn(ignoreRuleDao)
        whenever(db.smsParseTemplateDao()).thenReturn(smsParseTemplateDao)
        whenever(db.tagDao()).thenReturn(tagDao)
        whenever(db.goalDao()).thenReturn(goalDao)
        whenever(db.tripDao()).thenReturn(tripDao)
        whenever(db.accountAliasDao()).thenReturn(accountAliasDao)


        runTest {
            // Stub all DAO methods called within exportToJsonString to prevent side effects
            // Flow-based
            whenever(transactionDao.getAllTransactionsSimple()).thenReturn(flowOf(emptyList()))
            whenever(accountDao.getAllAccounts()).thenReturn(flowOf(emptyList()))
            whenever(categoryDao.getAllCategories()).thenReturn(flowOf(emptyList()))
            whenever(budgetDao.getAllBudgets()).thenReturn(flowOf(emptyList()))
            whenever(merchantMappingDao.getAllMappings()).thenReturn(flowOf(emptyList()))
            whenever(splitTransactionDao.getAllSplits()).thenReturn(flowOf(emptyList()))

            // Suspend-based
            whenever(customSmsRuleDao.getAllRulesList()).thenReturn(emptyList())
            whenever(merchantRenameRuleDao.getAllRulesList()).thenReturn(emptyList())
            whenever(merchantCategoryMappingDao.getAll()).thenReturn(emptyList())
            whenever(ignoreRuleDao.getAllList()).thenReturn(emptyList())
            whenever(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
            whenever(tagDao.getAllTagsList()).thenReturn(emptyList())
            whenever(transactionDao.getAllCrossRefs()).thenReturn(emptyList())
            whenever(goalDao.getAll()).thenReturn(emptyList())
            whenever(tripDao.getAll()).thenReturn(emptyList())
            whenever(accountAliasDao.getAll()).thenReturn(emptyList())
        }

        // Setup default instance mocks for initialization
        whenever(settingsRepository.getSmsScanStartDate()).thenReturn(flowOf(0L))
        whenever(settingsRepository.getDailyReportEnabled()).thenReturn(flowOf(false))
        whenever(settingsRepository.getWeeklySummaryEnabled()).thenReturn(flowOf(true))
        whenever(settingsRepository.getMonthlySummaryEnabled()).thenReturn(flowOf(true))
        whenever(settingsRepository.getAppLockEnabled()).thenReturn(flowOf(false))
        whenever(settingsRepository.getUnknownTransactionPopupEnabled()).thenReturn(flowOf(true))
        whenever(settingsRepository.getAutoCaptureNotificationEnabled()).thenReturn(flowOf(true))
        whenever(settingsRepository.getDailyReportTime()).thenReturn(flowOf(Pair(9, 0)))
        whenever(settingsRepository.getWeeklyReportTime()).thenReturn(flowOf(Triple(1, 9, 0)))
        whenever(settingsRepository.getMonthlyReportTime()).thenReturn(flowOf(Triple(1, 9, 0)))
        whenever(settingsRepository.getSelectedTheme()).thenReturn(flowOf(AppTheme.SYSTEM_DEFAULT))
        whenever(settingsRepository.getAutoBackupEnabled()).thenReturn(flowOf(true))
        whenever(settingsRepository.getAutoBackupTime()).thenReturn(flowOf(Pair(2, 0)))
        whenever(settingsRepository.getAutoBackupNotificationEnabled()).thenReturn(flowOf(true))
        whenever(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))
    }

    private fun initializeViewModel() {
        viewModel = SettingsViewModel(
            application,
            settingsRepository,
            db,
            transactionRepository,
            merchantMappingRepository,
            accountRepository,
            categoryRepository,
            smsRepository,
            transactionViewModel
        )
    }

    @Test
    fun `privacyModeEnabled flow emits value from repository`() = runTest {
        // Arrange
        whenever(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(true))
        initializeViewModel()

        // Assert
        viewModel.privacyModeEnabled.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setPrivacyModeEnabled calls repository`() {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.setPrivacyModeEnabled(true)

        // Assert
        verify(settingsRepository).savePrivacyModeEnabled(true)
    }

    @Test
    fun `saveSelectedTheme calls repository`() {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.saveSelectedTheme(AppTheme.AURORA)

        // Assert
        verify(settingsRepository).saveSelectedTheme(AppTheme.AURORA)
    }

    @Test
    fun `setAppLockEnabled calls repository`() {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.setAppLockEnabled(true)

        // Assert
        verify(settingsRepository).saveAppLockEnabled(true)
    }

    @Test
    fun `createBackupSnapshot success sends success message to uiEvent channel`() = runTest {
        val expectedMessage = "Backup snapshot created and backup requested."
        // Arrange
        mockkObject(DataExportService)
        coEvery { DataExportService.createBackupSnapshot(application) } returns true

        initializeViewModel()

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.createBackupSnapshot()
            advanceUntilIdle() // Ensure the coroutine completes
            assertEquals(expectedMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        unmockkObject(DataExportService) // Cleanup
    }

    @Test
    fun `createBackupSnapshot failure sends failure message to uiEvent channel`() = runTest {
        val expectedMessage = "Failed to create snapshot."
        // Arrange
        mockkObject(DataExportService)
        coEvery { DataExportService.createBackupSnapshot(application) } returns false

        initializeViewModel()

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.createBackupSnapshot()
            advanceUntilIdle() // Ensure the coroutine completes
            assertEquals(expectedMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        unmockkObject(DataExportService) // Cleanup
    }
}

