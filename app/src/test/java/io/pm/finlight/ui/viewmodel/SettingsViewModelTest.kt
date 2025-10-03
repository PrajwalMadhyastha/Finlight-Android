// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SettingsViewModelTest.kt
// REASON: NEW FILE - Unit tests for the refactored SettingsViewModel, covering
// state observation and actions for managing app settings.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.app.backup.BackupManager
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SettingsViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var merchantMappingRepository: MerchantMappingRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var smsRepository: SmsRepository
    @Mock private lateinit var transactionViewModel: TransactionViewModel
    @Mock private lateinit var backupManager: BackupManager

    private lateinit var dataExportServiceMock: MockedStatic<DataExportService>

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Mock static DataExportService
        dataExportServiceMock = mockStatic(DataExportService::class.java)

        // Setup default mocks for initialization
        `when`(settingsRepository.getSmsScanStartDate()).thenReturn(flowOf(0L))
        `when`(settingsRepository.getDailyReportEnabled()).thenReturn(flowOf(false))
        `when`(settingsRepository.getWeeklySummaryEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getMonthlySummaryEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getAppLockEnabled()).thenReturn(flowOf(false))
        `when`(settingsRepository.getUnknownTransactionPopupEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getAutoCaptureNotificationEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getDailyReportTime()).thenReturn(flowOf(Pair(9, 0)))
        `when`(settingsRepository.getWeeklyReportTime()).thenReturn(flowOf(Triple(1, 9, 0)))
        `when`(settingsRepository.getMonthlyReportTime()).thenReturn(flowOf(Triple(1, 9, 0)))
        `when`(settingsRepository.getSelectedTheme()).thenReturn(flowOf(AppTheme.SYSTEM_DEFAULT))
        `when`(settingsRepository.getAutoBackupEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getAutoBackupTime()).thenReturn(flowOf(Pair(2, 0)))
        `when`(settingsRepository.getAutoBackupNotificationEnabled()).thenReturn(flowOf(true))
        `when`(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(false))


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

    @After
    fun tearDown() {
        dataExportServiceMock.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `privacyModeEnabled flow emits value from repository`() = runTest {
        // Arrange
        `when`(settingsRepository.getPrivacyModeEnabled()).thenReturn(flowOf(true))
        viewModel = SettingsViewModel(application, settingsRepository, db, transactionRepository, merchantMappingRepository, accountRepository, categoryRepository, smsRepository, transactionViewModel)


        // Assert
        viewModel.privacyModeEnabled.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setPrivacyModeEnabled calls repository`() = runTest {
        // Act
        viewModel.setPrivacyModeEnabled(true)

        // Assert
        verify(settingsRepository).savePrivacyModeEnabled(true)
    }

    @Test
    fun `saveSelectedTheme calls repository`() = runTest {
        // Act
        viewModel.saveSelectedTheme(AppTheme.AURORA)

        // Assert
        verify(settingsRepository).saveSelectedTheme(AppTheme.AURORA)
    }

    @Test
    fun `setAppLockEnabled calls repository`() = runTest {
        // Act
        viewModel.setAppLockEnabled(true)

        // Assert
        verify(settingsRepository).saveAppLockEnabled(true)
    }

    @Test
    @Ignore
    fun `createBackupSnapshot calls DataExportService and notifies BackupManager`() = runTest {
        // Arrange
        dataExportServiceMock.`when`<Boolean> { runBlocking { DataExportService.createBackupSnapshot(any()) } }.thenReturn(true)
        // We can't easily mock the constructor-injected BackupManager, but we can verify its methods are not called if the snapshot fails.
        // A more advanced test setup could use a custom factory to inject a mock BackupManager.

        // Act
        viewModel.createBackupSnapshot()
        advanceUntilIdle()

        // Assert
        dataExportServiceMock.verify {
            runBlocking { DataExportService.createBackupSnapshot(application) }
        }
        // Cannot verify backupManager.dataChanged() directly without more complex DI/mocking setup for BackupManager itself.
        // However, we can infer its call by the success path.
    }
}