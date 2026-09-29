package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.IMerchantMappingRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.NerExtractor
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SettingsViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
        unmockkAll()
    }

    @Test
    fun create_withSettingsViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository =
            mockk(relaxed = true) {
                every { getDailyReportEnabled() } returns flowOf(false)
                every { getWeeklySummaryEnabled() } returns flowOf(true)
                every { getMonthlySummaryEnabled() } returns flowOf(true)
                every { getAppLockEnabled() } returns flowOf(false)
                every { getUnknownTransactionPopupEnabled() } returns flowOf(true)
                every { getAutoCaptureNotificationEnabled() } returns flowOf(true)
                every { getDailyReportTime() } returns flowOf(Pair(9, 0))
                every { getWeeklyReportTime() } returns flowOf(Triple(Calendar.MONDAY, 9, 0))
                every { getMonthlyReportTime() } returns flowOf(Triple(1, 9, 0))
                every { getSelectedTheme() } returns flowOf(AppTheme.SYSTEM_DEFAULT)
                every { getAutoBackupEnabled() } returns flowOf(true)
                every { getAutoBackupNotificationEnabled() } returns flowOf(false)
                every { getPrivacyModeEnabled() } returns flowOf(false)
                every { getSimulatorPrivacyModeEnabled() } returns flowOf(false)
                every { getLastBackupTimestamp() } returns flowOf(0L)
                every { getHasSeenOnboarding() } returns flowOf(false)
                every { getIsFirstLaunchComplete() } returns flowOf(false)
                every { getSmsScanStartDate() } returns flowOf(0L)
            }
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockMerchantMappingRepo: IMerchantMappingRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockSmsRepo: ISmsRepository = mockk(relaxed = true)
        val mockTransactionViewModel: TransactionViewModel = mockk(relaxed = true)

        mockkObject(MlModelFactory)
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        val mockNerExtractor: NerExtractor = mockk(relaxed = true)
        every { MlModelFactory.getClassifier(any()) } returns mockClassifier
        every { MlModelFactory.getNerExtractor(any()) } returns mockNerExtractor

        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setMerchantMappingRepository(mockMerchantMappingRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setSmsRepository(mockSmsRepo)

        val factory = SettingsViewModelFactory(application, mockTransactionViewModel)
        val viewModel = factory.create(SettingsViewModel::class.java)

        assertNotNull(viewModel)

        val mappingRepoField =
            SettingsViewModel::class.java.getDeclaredField("merchantMappingRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockMerchantMappingRepo, mappingRepoField)

        val settingsRepoField =
            SettingsViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsRepoField)

        val txnRepoField =
            SettingsViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnRepoField)

        val accountRepoField =
            SettingsViewModel::class.java.getDeclaredField("accountRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockAccountRepo, accountRepoField)

        val categoryRepoField =
            SettingsViewModel::class.java.getDeclaredField("categoryRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCategoryRepo, categoryRepoField)

        val smsRepoField =
            SettingsViewModel::class.java.getDeclaredField("smsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSmsRepo, smsRepoField)

        val txnVmField =
            SettingsViewModel::class.java.getDeclaredField("transactionViewModel").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTransactionViewModel, txnVmField)

        val classifierField =
            SettingsViewModel::class.java.getDeclaredField("smsClassifier").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockClassifier, classifierField)

        // NerExtractor is NOT eagerly instantiated on creation
        verify(exactly = 0) { MlModelFactory.getNerExtractor(any()) }

        // When provider is invoked on-demand, NerExtractor is loaded
        val resolvedExtractor = viewModel.nerExtractorProvider.invoke()
        assertSame(mockNerExtractor, resolvedExtractor)
        verify(exactly = 1) { MlModelFactory.getNerExtractor(any()) }
    }

    @Test
    fun create_withCustomNerExtractorProvider_passesProviderToViewModel() {
        val mockSettingsRepo: ISettingsRepository =
            mockk(relaxed = true) {
                every { getDailyReportEnabled() } returns flowOf(false)
                every { getWeeklySummaryEnabled() } returns flowOf(true)
                every { getMonthlySummaryEnabled() } returns flowOf(true)
                every { getAppLockEnabled() } returns flowOf(false)
                every { getUnknownTransactionPopupEnabled() } returns flowOf(true)
                every { getAutoCaptureNotificationEnabled() } returns flowOf(true)
                every { getDailyReportTime() } returns flowOf(Pair(9, 0))
                every { getWeeklyReportTime() } returns flowOf(Triple(Calendar.MONDAY, 9, 0))
                every { getMonthlyReportTime() } returns flowOf(Triple(1, 9, 0))
                every { getSelectedTheme() } returns flowOf(AppTheme.SYSTEM_DEFAULT)
                every { getAutoBackupEnabled() } returns flowOf(true)
                every { getAutoBackupNotificationEnabled() } returns flowOf(false)
                every { getPrivacyModeEnabled() } returns flowOf(false)
                every { getSimulatorPrivacyModeEnabled() } returns flowOf(false)
                every { getLastBackupTimestamp() } returns flowOf(0L)
                every { getHasSeenOnboarding() } returns flowOf(false)
                every { getIsFirstLaunchComplete() } returns flowOf(false)
                every { getSmsScanStartDate() } returns flowOf(0L)
            }
        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setTransactionRepository(mockk(relaxed = true))
        ServiceLocator.setMerchantMappingRepository(mockk(relaxed = true))
        ServiceLocator.setAccountRepository(mockk(relaxed = true))
        ServiceLocator.setCategoryRepository(mockk(relaxed = true))
        ServiceLocator.setSmsRepository(mockk(relaxed = true))

        mockkObject(MlModelFactory)
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        every { MlModelFactory.getClassifier(any()) } returns mockClassifier

        val customExtractor: NerExtractor = mockk(relaxed = true)
        val customProvider: () -> NerExtractor = { customExtractor }

        val factory = SettingsViewModelFactory(application, mockk(relaxed = true), nerExtractorProvider = customProvider)
        val viewModel = factory.create(SettingsViewModel::class.java)

        assertSame(customExtractor, viewModel.nerExtractorProvider.invoke())
        verify(exactly = 0) { MlModelFactory.getNerExtractor(any()) }
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        val mockTransactionViewModel: TransactionViewModel = mockk(relaxed = true)
        val factory = SettingsViewModelFactory(application, mockTransactionViewModel)

        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
