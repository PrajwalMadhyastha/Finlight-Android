package io.pm.finlight.di

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.pm.finlight.BudgetDao
import io.pm.finlight.BudgetRepository
import io.pm.finlight.CustomSmsRuleDao
import io.pm.finlight.CustomSmsRuleRepository
import io.pm.finlight.DashboardViewModel
import io.pm.finlight.IAccountRepository
import io.pm.finlight.IBudgetRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.IGoalRepository
import io.pm.finlight.IIgnoreRuleRepository
import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IgnoreRuleDao
import io.pm.finlight.IgnoreRuleRepository
import io.pm.finlight.IMerchantMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.MerchantCategoryMappingDao
import io.pm.finlight.MerchantCategoryMappingRepository
import io.pm.finlight.MerchantMappingDao
import io.pm.finlight.MerchantRenameRuleDao
import io.pm.finlight.MerchantRenameRuleRepository
import io.pm.finlight.RecurringPatternDao
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.RecurringTransactionRepository
import io.pm.finlight.SplitTransactionDao
import io.pm.finlight.SplitTransactionRepository
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.NerExtractor
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.AnalysisDetailViewModel
import io.pm.finlight.ui.viewmodel.AnalysisDimension
import io.pm.finlight.ui.viewmodel.BatchAnalysisViewModel
import io.pm.finlight.ui.viewmodel.BudgetViewModel
import io.pm.finlight.ui.viewmodel.CategoryViewModel
import io.pm.finlight.ui.viewmodel.CurrencyViewModel
import io.pm.finlight.ui.viewmodel.DrilldownType
import io.pm.finlight.ui.viewmodel.DrilldownViewModel
import io.pm.finlight.ui.viewmodel.GoalViewModel
import io.pm.finlight.ui.viewmodel.HistoricTripsViewModel
import io.pm.finlight.ui.viewmodel.IncomeViewModel
import io.pm.finlight.ui.viewmodel.LinkRecurringTransactionViewModel
import io.pm.finlight.ui.viewmodel.LinkTransactionViewModel
import io.pm.finlight.ui.viewmodel.OnboardingViewModel
import io.pm.finlight.ui.viewmodel.ProfileViewModel
import io.pm.finlight.ui.viewmodel.RuleCreationViewModel
import io.pm.finlight.ui.viewmodel.SearchViewModel
import io.pm.finlight.ui.viewmodel.SplitTransactionViewModel
import io.pm.finlight.ui.viewmodel.TagViewModel
import io.pm.finlight.ui.viewmodel.TimePeriodReportViewModel
import io.pm.finlight.ui.viewmodel.TripDetailViewModel
import io.pm.finlight.domain.usecase.AnalyzeRecurringPatternsUseCase
import io.pm.finlight.domain.usecase.AutoSaveSmsTransactionUseCase
import io.pm.finlight.domain.usecase.DefaultAnalyzeRecurringPatternsUseCase
import io.pm.finlight.domain.usecase.DefaultProcessDueRecurringTransactionsUseCase
import io.pm.finlight.domain.usecase.DefaultScheduleRecurringTransactionWorkerUseCase
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import io.pm.finlight.domain.usecase.ProcessDueRecurringTransactionsUseCase
import io.pm.finlight.domain.usecase.ScheduleRecurringTransactionWorkerUseCase
import io.pm.finlight.utils.DefaultWorkScheduler
import io.pm.finlight.utils.IWorkScheduler
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ServiceLocatorTest {
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
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        val mockNerExtractor: SmsEntityExtractor = mockk(relaxed = true)
        ServiceLocator.setSmsClassifier(mockClassifier)
        ServiceLocator.setNerExtractorProvider { mockNerExtractor }
        ServiceLocator.setDispatcherProvider(TestDispatcherProvider())
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
        unmockkAll()
    }

    // --- Singleton Resolution Tests ---

    @Test
    fun provideTransactionRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideTransactionRepository(application)
        val repo2 = ServiceLocator.provideTransactionRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideAccountRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideAccountRepository(application)
        val repo2 = ServiceLocator.provideAccountRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideCategoryRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideCategoryRepository(application)
        val repo2 = ServiceLocator.provideCategoryRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideTagRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideTagRepository(application)
        val repo2 = ServiceLocator.provideTagRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideSmsRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideSmsRepository(application)
        val repo2 = ServiceLocator.provideSmsRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideMergeAccountsUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideMergeAccountsUseCase(application)
        val useCase2 = ServiceLocator.provideMergeAccountsUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
    }

    @Test
    fun provideManageReimbursementUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideManageReimbursementUseCase(application)
        val useCase2 = ServiceLocator.provideManageReimbursementUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
    }

    @Test
    fun provideMergeTransactionsUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideMergeTransactionsUseCase(application)
        val useCase2 = ServiceLocator.provideMergeTransactionsUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
    }

    @Test
    fun provideAutoSaveSmsTransactionUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideAutoSaveSmsTransactionUseCase(application)
        val useCase2 = ServiceLocator.provideAutoSaveSmsTransactionUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
    }

    @Test
    fun provideGetMonthlyConsistencyDataUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application)
        val useCase2 = ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
    }

    @Test
    fun provideMerchantMappingRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideMerchantMappingRepository(application)
        val repo2 = ServiceLocator.provideMerchantMappingRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun provideBudgetRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideBudgetRepository(application)
        val repo2 = ServiceLocator.provideBudgetRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is BudgetRepository)
    }

    @Test
    fun provideRecurringTransactionRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideRecurringTransactionRepository(application)
        val repo2 = ServiceLocator.provideRecurringTransactionRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is RecurringTransactionRepository)
    }

    @Test
    fun provideSplitTransactionRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideSplitTransactionRepository(application)
        val repo2 = ServiceLocator.provideSplitTransactionRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is SplitTransactionRepository)
    }

    @Test
    fun provideMerchantRenameRuleRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideMerchantRenameRuleRepository(application)
        val repo2 = ServiceLocator.provideMerchantRenameRuleRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is MerchantRenameRuleRepository)
    }

    @Test
    fun provideMerchantCategoryMappingRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideMerchantCategoryMappingRepository(application)
        val repo2 = ServiceLocator.provideMerchantCategoryMappingRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is MerchantCategoryMappingRepository)
    }

    @Test
    fun provideCustomSmsRuleRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideCustomSmsRuleRepository(application)
        val repo2 = ServiceLocator.provideCustomSmsRuleRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is CustomSmsRuleRepository)
    }

    @Test
    fun provideIgnoreRuleRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideIgnoreRuleRepository(application)
        val repo2 = ServiceLocator.provideIgnoreRuleRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
        assertTrue(repo1 is IgnoreRuleRepository)
    }

    // --- Resolved Database Parameter Branching Tests ---

    @Test
    fun provideBudgetRepository_withResolvedDb_usesSuppliedDatabase() {
        val customDao: BudgetDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { budgetDao() } returns customDao
            }

        val repo = ServiceLocator.provideBudgetRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.budgetDao() }
        assertSame(repo, ServiceLocator.provideBudgetRepository(application))
    }

    @Test
    fun provideRecurringTransactionRepository_withResolvedDb_usesSuppliedDatabase() {
        val customDao: RecurringTransactionDao = mockk(relaxed = true)
        val customPatternDao: RecurringPatternDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { recurringTransactionDao() } returns customDao
                every { recurringPatternDao() } returns customPatternDao
            }

        val repo = ServiceLocator.provideRecurringTransactionRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.recurringTransactionDao() }
        verify(exactly = 1) { customDb.recurringPatternDao() }
        assertSame(repo, ServiceLocator.provideRecurringTransactionRepository(application))
    }

    @Test
    fun provideSplitTransactionRepository_withResolvedDb_usesSuppliedDatabase() {
        val customDao: SplitTransactionDao = mockk(relaxed = true)
        val customWriteDao: io.pm.finlight.data.db.dao.TransactionWriteDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { splitTransactionDao() } returns customDao
                every { transactionWriteDao() } returns customWriteDao
            }

        val repo = ServiceLocator.provideSplitTransactionRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.splitTransactionDao() }
        assertSame(repo, ServiceLocator.provideSplitTransactionRepository(application))
    }

    @Test
    fun provideMerchantRenameRuleRepository_withResolvedDb_usesSuppliedDatabase() {
        val customDao: MerchantRenameRuleDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { merchantRenameRuleDao() } returns customDao
            }

        val repo = ServiceLocator.provideMerchantRenameRuleRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.merchantRenameRuleDao() }
        assertSame(repo, ServiceLocator.provideMerchantRenameRuleRepository(application))
    }

    @Test
    fun provideMerchantMappingRepository_withResolvedDb_usesSuppliedDatabase() {
        val customMappingDao: MerchantMappingDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { merchantMappingDao() } returns customMappingDao
            }

        val repo = ServiceLocator.provideMerchantMappingRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.merchantMappingDao() }
        assertSame(repo, ServiceLocator.provideMerchantMappingRepository(application))
    }

    @Test
    fun provideMerchantCategoryMappingRepository_withResolvedDb_usesSuppliedDatabase() {
        val customDao: MerchantCategoryMappingDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { merchantCategoryMappingDao() } returns customDao
            }

        val repo = ServiceLocator.provideMerchantCategoryMappingRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.merchantCategoryMappingDao() }
        assertSame(repo, ServiceLocator.provideMerchantCategoryMappingRepository(application))
    }

    @Test
    fun provideCustomSmsRuleRepository_withResolvedDb_usesSuppliedDatabase() {
        val customDao: CustomSmsRuleDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { customSmsRuleDao() } returns customDao
            }

        val repo = ServiceLocator.provideCustomSmsRuleRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.customSmsRuleDao() }
        assertSame(repo, ServiceLocator.provideCustomSmsRuleRepository(application))
    }

    @Test
    fun provideIgnoreRuleRepository_withResolvedDb_usesSuppliedDatabase() {
        val customDao: IgnoreRuleDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { ignoreRuleDao() } returns customDao
            }

        val repo = ServiceLocator.provideIgnoreRuleRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.ignoreRuleDao() }
        assertSame(repo, ServiceLocator.provideIgnoreRuleRepository(application))
    }

    @Test
    fun provideSmsParseTemplateRepository_withResolvedDb_usesSuppliedDatabase() {
        val customDao: io.pm.finlight.SmsParseTemplateDao = mockk(relaxed = true)
        val customDb: AppDatabase =
            mockk {
                every { smsParseTemplateDao() } returns customDao
            }

        val repo = ServiceLocator.provideSmsParseTemplateRepository(application, customDb)
        assertNotNull(repo)
        verify(exactly = 1) { customDb.smsParseTemplateDao() }
        assertSame(repo, ServiceLocator.provideSmsParseTemplateRepository(application))
    }

    // --- VisibleForTesting Overriding Setter Tests ---

    @Test
    fun setTransactionRepository_overridesInstance() {
        val mockRepo: ITransactionRepository = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideTransactionRepository(application))

        val mockRepo2: ITransactionRepository = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideTransactionRepository(application))

        ServiceLocator.setTransactionRepository(null)
        val defaultRepo = ServiceLocator.provideTransactionRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setAccountRepository_overridesInstance() {
        val mockRepo: IAccountRepository = mockk(relaxed = true)
        ServiceLocator.setAccountRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideAccountRepository(application))

        val mockRepo2: IAccountRepository = mockk(relaxed = true)
        ServiceLocator.setAccountRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideAccountRepository(application))

        ServiceLocator.setAccountRepository(null)
        val defaultRepo = ServiceLocator.provideAccountRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setCategoryRepository_overridesInstance() {
        val mockRepo: ICategoryRepository = mockk(relaxed = true)
        ServiceLocator.setCategoryRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideCategoryRepository(application))

        val mockRepo2: ICategoryRepository = mockk(relaxed = true)
        ServiceLocator.setCategoryRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideCategoryRepository(application))

        ServiceLocator.setCategoryRepository(null)
        val defaultRepo = ServiceLocator.provideCategoryRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setTagRepository_overridesInstance() {
        val mockRepo: ITagRepository = mockk(relaxed = true)
        ServiceLocator.setTagRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideTagRepository(application))

        val mockRepo2: ITagRepository = mockk(relaxed = true)
        ServiceLocator.setTagRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideTagRepository(application))

        ServiceLocator.setTagRepository(null)
        val defaultRepo = ServiceLocator.provideTagRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setSmsRepository_overridesInstance() {
        val mockRepo: ISmsRepository = mockk(relaxed = true)
        ServiceLocator.setSmsRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideSmsRepository(application))

        val mockRepo2: ISmsRepository = mockk(relaxed = true)
        ServiceLocator.setSmsRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideSmsRepository(application))

        ServiceLocator.setSmsRepository(null)
        val defaultRepo = ServiceLocator.provideSmsRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setMergeAccountsUseCase_overridesInstance() {
        val mockUseCase: MergeAccountsUseCase = mockk(relaxed = true)
        ServiceLocator.setMergeAccountsUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideMergeAccountsUseCase(application))

        val mockUseCase2: MergeAccountsUseCase = mockk(relaxed = true)
        ServiceLocator.setMergeAccountsUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideMergeAccountsUseCase(application))

        ServiceLocator.setMergeAccountsUseCase(null)
        val defaultUseCase = ServiceLocator.provideMergeAccountsUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun setManageReimbursementUseCase_overridesInstance() {
        val mockUseCase: ManageReimbursementUseCase = mockk(relaxed = true)
        ServiceLocator.setManageReimbursementUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideManageReimbursementUseCase(application))

        val mockUseCase2: ManageReimbursementUseCase = mockk(relaxed = true)
        ServiceLocator.setManageReimbursementUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideManageReimbursementUseCase(application))

        ServiceLocator.setManageReimbursementUseCase(null)
        val defaultUseCase = ServiceLocator.provideManageReimbursementUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun setMergeTransactionsUseCase_overridesInstance() {
        val mockUseCase: MergeTransactionsUseCase = mockk(relaxed = true)
        ServiceLocator.setMergeTransactionsUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideMergeTransactionsUseCase(application))

        val mockUseCase2: MergeTransactionsUseCase = mockk(relaxed = true)
        ServiceLocator.setMergeTransactionsUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideMergeTransactionsUseCase(application))

        ServiceLocator.setMergeTransactionsUseCase(null)
        val defaultUseCase = ServiceLocator.provideMergeTransactionsUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun setSmsParseTemplateRepository_overridesInstance() {
        val mockRepo: io.pm.finlight.ISmsParseTemplateRepository = mockk(relaxed = true)
        ServiceLocator.setSmsParseTemplateRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideSmsParseTemplateRepository(application))

        val mockRepo2: io.pm.finlight.ISmsParseTemplateRepository = mockk(relaxed = true)
        ServiceLocator.setSmsParseTemplateRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideSmsParseTemplateRepository(application))

        ServiceLocator.setSmsParseTemplateRepository(null)
        val defaultRepo = ServiceLocator.provideSmsParseTemplateRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setAutoSaveSmsTransactionUseCase_overridesInstance() {
        val mockUseCase: AutoSaveSmsTransactionUseCase = mockk(relaxed = true)
        ServiceLocator.setAutoSaveSmsTransactionUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideAutoSaveSmsTransactionUseCase(application))

        val mockUseCase2: AutoSaveSmsTransactionUseCase = mockk(relaxed = true)
        ServiceLocator.setAutoSaveSmsTransactionUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideAutoSaveSmsTransactionUseCase(application))

        ServiceLocator.setAutoSaveSmsTransactionUseCase(null)
        val defaultUseCase = ServiceLocator.provideAutoSaveSmsTransactionUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun setGetMonthlyConsistencyDataUseCase_overridesInstance() {
        val mockUseCase: GetMonthlyConsistencyDataUseCase = mockk(relaxed = true)
        ServiceLocator.setGetMonthlyConsistencyDataUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application))

        val mockUseCase2: GetMonthlyConsistencyDataUseCase = mockk(relaxed = true)
        ServiceLocator.setGetMonthlyConsistencyDataUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application))

        ServiceLocator.setGetMonthlyConsistencyDataUseCase(null)
        val defaultUseCase = ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun provideScheduleRecurringTransactionWorkerUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideScheduleRecurringTransactionWorkerUseCase(application)
        val useCase2 = ServiceLocator.provideScheduleRecurringTransactionWorkerUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
        assertTrue(useCase1 is DefaultScheduleRecurringTransactionWorkerUseCase)
    }

    @Test
    fun setScheduleRecurringTransactionWorkerUseCase_overridesInstance() {
        val mockUseCase: ScheduleRecurringTransactionWorkerUseCase = mockk(relaxed = true)
        ServiceLocator.setScheduleRecurringTransactionWorkerUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideScheduleRecurringTransactionWorkerUseCase(application))

        val mockUseCase2: ScheduleRecurringTransactionWorkerUseCase = mockk(relaxed = true)
        ServiceLocator.setScheduleRecurringTransactionWorkerUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideScheduleRecurringTransactionWorkerUseCase(application))

        ServiceLocator.setScheduleRecurringTransactionWorkerUseCase(null)
        val defaultUseCase = ServiceLocator.provideScheduleRecurringTransactionWorkerUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun provideProcessDueRecurringTransactionsUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideProcessDueRecurringTransactionsUseCase(application)
        val useCase2 = ServiceLocator.provideProcessDueRecurringTransactionsUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
        assertTrue(useCase1 is DefaultProcessDueRecurringTransactionsUseCase)
    }

    @Test
    fun setProcessDueRecurringTransactionsUseCase_overridesInstance() {
        val mockUseCase: ProcessDueRecurringTransactionsUseCase = mockk(relaxed = true)
        ServiceLocator.setProcessDueRecurringTransactionsUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideProcessDueRecurringTransactionsUseCase(application))

        val mockUseCase2: ProcessDueRecurringTransactionsUseCase = mockk(relaxed = true)
        ServiceLocator.setProcessDueRecurringTransactionsUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideProcessDueRecurringTransactionsUseCase(application))

        ServiceLocator.setProcessDueRecurringTransactionsUseCase(null)
        val defaultUseCase = ServiceLocator.provideProcessDueRecurringTransactionsUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun provideAnalyzeRecurringPatternsUseCase_returnsSingletonInstance() {
        val useCase1 = ServiceLocator.provideAnalyzeRecurringPatternsUseCase(application)
        val useCase2 = ServiceLocator.provideAnalyzeRecurringPatternsUseCase(application)

        assertNotNull(useCase1)
        assertSame(useCase1, useCase2)
        assertTrue(useCase1 is DefaultAnalyzeRecurringPatternsUseCase)
    }

    @Test
    fun setAnalyzeRecurringPatternsUseCase_overridesInstance() {
        val mockUseCase: AnalyzeRecurringPatternsUseCase = mockk(relaxed = true)
        ServiceLocator.setAnalyzeRecurringPatternsUseCase(mockUseCase)
        assertSame(mockUseCase, ServiceLocator.provideAnalyzeRecurringPatternsUseCase(application))

        val mockUseCase2: AnalyzeRecurringPatternsUseCase = mockk(relaxed = true)
        ServiceLocator.setAnalyzeRecurringPatternsUseCase(mockUseCase2)
        assertSame(mockUseCase2, ServiceLocator.provideAnalyzeRecurringPatternsUseCase(application))

        ServiceLocator.setAnalyzeRecurringPatternsUseCase(null)
        val defaultUseCase = ServiceLocator.provideAnalyzeRecurringPatternsUseCase(application)
        assertNotNull(defaultUseCase)
        assertNotSame(mockUseCase2, defaultUseCase)
    }

    @Test
    fun provideWorkScheduler_returnsSingletonInstance() {
        val scheduler1 = ServiceLocator.provideWorkScheduler(application)
        val scheduler2 = ServiceLocator.provideWorkScheduler(application)

        assertNotNull(scheduler1)
        assertSame(scheduler1, scheduler2)
        assertTrue(scheduler1 is DefaultWorkScheduler)
    }

    @Test
    fun setWorkScheduler_overridesInstance() {
        val mockScheduler: IWorkScheduler = mockk(relaxed = true)
        ServiceLocator.setWorkScheduler(mockScheduler)
        assertSame(mockScheduler, ServiceLocator.provideWorkScheduler(application))

        val mockScheduler2: IWorkScheduler = mockk(relaxed = true)
        ServiceLocator.setWorkScheduler(mockScheduler2)
        assertSame(mockScheduler2, ServiceLocator.provideWorkScheduler(application))

        ServiceLocator.setWorkScheduler(null)
        val defaultScheduler = ServiceLocator.provideWorkScheduler(application)
        assertNotNull(defaultScheduler)
        assertNotSame(mockScheduler2, defaultScheduler)
    }

    @Test
    fun setMerchantMappingRepository_overridesInstance() {
        val mockRepo: IMerchantMappingRepository = mockk(relaxed = true)
        ServiceLocator.setMerchantMappingRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideMerchantMappingRepository(application))

        val mockRepo2: IMerchantMappingRepository = mockk(relaxed = true)
        ServiceLocator.setMerchantMappingRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideMerchantMappingRepository(application))

        ServiceLocator.setMerchantMappingRepository(null)
        val defaultRepo = ServiceLocator.provideMerchantMappingRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setBudgetRepository_overridesInstance() {
        val mockRepo: IBudgetRepository = mockk(relaxed = true)
        ServiceLocator.setBudgetRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideBudgetRepository(application))

        val mockRepo2: IBudgetRepository = mockk(relaxed = true)
        ServiceLocator.setBudgetRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideBudgetRepository(application))

        ServiceLocator.setBudgetRepository(null)
        val defaultRepo = ServiceLocator.provideBudgetRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setRecurringTransactionRepository_overridesInstance() {
        val mockRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setRecurringTransactionRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideRecurringTransactionRepository(application))

        val mockRepo2: IRecurringTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setRecurringTransactionRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideRecurringTransactionRepository(application))

        ServiceLocator.setRecurringTransactionRepository(null)
        val defaultRepo = ServiceLocator.provideRecurringTransactionRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setSplitTransactionRepository_overridesInstance() {
        val mockRepo: ISplitTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setSplitTransactionRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideSplitTransactionRepository(application))

        val mockRepo2: ISplitTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setSplitTransactionRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideSplitTransactionRepository(application))

        ServiceLocator.setSplitTransactionRepository(null)
        val defaultRepo = ServiceLocator.provideSplitTransactionRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setMerchantRenameRuleRepository_overridesInstance() {
        val mockRepo: IMerchantRenameRuleRepository = mockk(relaxed = true)
        ServiceLocator.setMerchantRenameRuleRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideMerchantRenameRuleRepository(application))

        val mockRepo2: IMerchantRenameRuleRepository = mockk(relaxed = true)
        ServiceLocator.setMerchantRenameRuleRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideMerchantRenameRuleRepository(application))

        ServiceLocator.setMerchantRenameRuleRepository(null)
        val defaultRepo = ServiceLocator.provideMerchantRenameRuleRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setMerchantCategoryMappingRepository_overridesInstance() {
        val mockRepo: IMerchantCategoryMappingRepository = mockk(relaxed = true)
        ServiceLocator.setMerchantCategoryMappingRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideMerchantCategoryMappingRepository(application))

        val mockRepo2: IMerchantCategoryMappingRepository = mockk(relaxed = true)
        ServiceLocator.setMerchantCategoryMappingRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideMerchantCategoryMappingRepository(application))

        ServiceLocator.setMerchantCategoryMappingRepository(null)
        val defaultRepo = ServiceLocator.provideMerchantCategoryMappingRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setCustomSmsRuleRepository_overridesInstance() {
        val mockRepo: ICustomSmsRuleRepository = mockk(relaxed = true)
        ServiceLocator.setCustomSmsRuleRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideCustomSmsRuleRepository(application))

        val mockRepo2: ICustomSmsRuleRepository = mockk(relaxed = true)
        ServiceLocator.setCustomSmsRuleRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideCustomSmsRuleRepository(application))

        ServiceLocator.setCustomSmsRuleRepository(null)
        val defaultRepo = ServiceLocator.provideCustomSmsRuleRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    @Test
    fun setIgnoreRuleRepository_overridesInstance() {
        val mockRepo: IIgnoreRuleRepository = mockk(relaxed = true)
        ServiceLocator.setIgnoreRuleRepository(mockRepo)
        assertSame(mockRepo, ServiceLocator.provideIgnoreRuleRepository(application))

        val mockRepo2: IIgnoreRuleRepository = mockk(relaxed = true)
        ServiceLocator.setIgnoreRuleRepository(mockRepo2)
        assertSame(mockRepo2, ServiceLocator.provideIgnoreRuleRepository(application))

        ServiceLocator.setIgnoreRuleRepository(null)
        val defaultRepo = ServiceLocator.provideIgnoreRuleRepository(application)
        assertNotNull(defaultRepo)
        assertNotSame(mockRepo2, defaultRepo)
    }

    // --- Reset Behavior Tests ---

    @Test
    fun reset_clearsAllDomainRepositoriesAndUseCases() {
        val mockTxn: ITransactionRepository = mockk(relaxed = true)
        val mockAccount: IAccountRepository = mockk(relaxed = true)
        val mockCategory: ICategoryRepository = mockk(relaxed = true)
        val mockTag: ITagRepository = mockk(relaxed = true)
        val mockSms: ISmsRepository = mockk(relaxed = true)
        val mockMergeAccounts: MergeAccountsUseCase = mockk(relaxed = true)
        val mockManageReimbursement: ManageReimbursementUseCase = mockk(relaxed = true)
        val mockMergeTransactions: MergeTransactionsUseCase = mockk(relaxed = true)
        val mockAutoSave: AutoSaveSmsTransactionUseCase = mockk(relaxed = true)
        val mockGetMonthlyConsistency: GetMonthlyConsistencyDataUseCase = mockk(relaxed = true)
        val mockScheduleWorker: ScheduleRecurringTransactionWorkerUseCase = mockk(relaxed = true)
        val mockMerchantMapping: IMerchantMappingRepository = mockk(relaxed = true)
        val mockBudget: IBudgetRepository = mockk(relaxed = true)
        val mockRecurring: IRecurringTransactionRepository = mockk(relaxed = true)
        val mockSplit: ISplitTransactionRepository = mockk(relaxed = true)
        val mockRename: IMerchantRenameRuleRepository = mockk(relaxed = true)
        val mockCategoryMapping: IMerchantCategoryMappingRepository = mockk(relaxed = true)
        val mockCustomSmsRule: ICustomSmsRuleRepository = mockk(relaxed = true)
        val mockIgnoreRule: IIgnoreRuleRepository = mockk(relaxed = true)
        val mockWorkScheduler: IWorkScheduler = mockk(relaxed = true)
        val mockProcessDueUseCase: ProcessDueRecurringTransactionsUseCase = mockk(relaxed = true)
        val mockAnalyzePatternsUseCase: AnalyzeRecurringPatternsUseCase = mockk(relaxed = true)

        ServiceLocator.setTransactionRepository(mockTxn)
        ServiceLocator.setAccountRepository(mockAccount)
        ServiceLocator.setCategoryRepository(mockCategory)
        ServiceLocator.setTagRepository(mockTag)
        ServiceLocator.setSmsRepository(mockSms)
        ServiceLocator.setMergeAccountsUseCase(mockMergeAccounts)
        ServiceLocator.setManageReimbursementUseCase(mockManageReimbursement)
        ServiceLocator.setMergeTransactionsUseCase(mockMergeTransactions)
        ServiceLocator.setAutoSaveSmsTransactionUseCase(mockAutoSave)
        ServiceLocator.setGetMonthlyConsistencyDataUseCase(mockGetMonthlyConsistency)
        ServiceLocator.setScheduleRecurringTransactionWorkerUseCase(mockScheduleWorker)
        ServiceLocator.setProcessDueRecurringTransactionsUseCase(mockProcessDueUseCase)
        ServiceLocator.setAnalyzeRecurringPatternsUseCase(mockAnalyzePatternsUseCase)
        ServiceLocator.setMerchantMappingRepository(mockMerchantMapping)
        ServiceLocator.setBudgetRepository(mockBudget)
        ServiceLocator.setRecurringTransactionRepository(mockRecurring)
        ServiceLocator.setSplitTransactionRepository(mockSplit)
        ServiceLocator.setMerchantRenameRuleRepository(mockRename)
        ServiceLocator.setMerchantCategoryMappingRepository(mockCategoryMapping)
        ServiceLocator.setCustomSmsRuleRepository(mockCustomSmsRule)
        ServiceLocator.setIgnoreRuleRepository(mockIgnoreRule)
        ServiceLocator.setWorkScheduler(mockWorkScheduler)

        assertSame(mockTxn, ServiceLocator.provideTransactionRepository(application))
        assertSame(mockAccount, ServiceLocator.provideAccountRepository(application))
        assertSame(mockCategory, ServiceLocator.provideCategoryRepository(application))
        assertSame(mockTag, ServiceLocator.provideTagRepository(application))
        assertSame(mockSms, ServiceLocator.provideSmsRepository(application))
        assertSame(mockMergeAccounts, ServiceLocator.provideMergeAccountsUseCase(application))
        assertSame(mockManageReimbursement, ServiceLocator.provideManageReimbursementUseCase(application))
        assertSame(mockMergeTransactions, ServiceLocator.provideMergeTransactionsUseCase(application))
        assertSame(mockAutoSave, ServiceLocator.provideAutoSaveSmsTransactionUseCase(application))
        assertSame(mockGetMonthlyConsistency, ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application))
        assertSame(mockScheduleWorker, ServiceLocator.provideScheduleRecurringTransactionWorkerUseCase(application))
        assertSame(mockProcessDueUseCase, ServiceLocator.provideProcessDueRecurringTransactionsUseCase(application))
        assertSame(mockAnalyzePatternsUseCase, ServiceLocator.provideAnalyzeRecurringPatternsUseCase(application))
        assertSame(mockMerchantMapping, ServiceLocator.provideMerchantMappingRepository(application))
        assertSame(mockBudget, ServiceLocator.provideBudgetRepository(application))
        assertSame(mockRecurring, ServiceLocator.provideRecurringTransactionRepository(application))
        assertSame(mockSplit, ServiceLocator.provideSplitTransactionRepository(application))
        assertSame(mockRename, ServiceLocator.provideMerchantRenameRuleRepository(application))
        assertSame(mockCategoryMapping, ServiceLocator.provideMerchantCategoryMappingRepository(application))
        assertSame(mockCustomSmsRule, ServiceLocator.provideCustomSmsRuleRepository(application))
        assertSame(mockIgnoreRule, ServiceLocator.provideIgnoreRuleRepository(application))
        assertSame(mockWorkScheduler, ServiceLocator.provideWorkScheduler(application))

        ServiceLocator.reset()

        val newTxn = ServiceLocator.provideTransactionRepository(application)
        val newAccount = ServiceLocator.provideAccountRepository(application)
        val newCategory = ServiceLocator.provideCategoryRepository(application)
        val newTag = ServiceLocator.provideTagRepository(application)
        val newSms = ServiceLocator.provideSmsRepository(application)
        val newMergeAccounts = ServiceLocator.provideMergeAccountsUseCase(application)
        val newManageReimbursement = ServiceLocator.provideManageReimbursementUseCase(application)
        val newMergeTransactions = ServiceLocator.provideMergeTransactionsUseCase(application)
        val newAutoSave = ServiceLocator.provideAutoSaveSmsTransactionUseCase(application)
        val newGetMonthlyConsistency = ServiceLocator.provideGetMonthlyConsistencyDataUseCase(application)
        val newScheduleWorker = ServiceLocator.provideScheduleRecurringTransactionWorkerUseCase(application)
        val newProcessDueUseCase = ServiceLocator.provideProcessDueRecurringTransactionsUseCase(application)
        val newAnalyzePatternsUseCase = ServiceLocator.provideAnalyzeRecurringPatternsUseCase(application)
        val newMerchantMapping = ServiceLocator.provideMerchantMappingRepository(application)
        val newBudget = ServiceLocator.provideBudgetRepository(application)
        val newRecurring = ServiceLocator.provideRecurringTransactionRepository(application)
        val newSplit = ServiceLocator.provideSplitTransactionRepository(application)
        val newRename = ServiceLocator.provideMerchantRenameRuleRepository(application)
        val newCategoryMapping = ServiceLocator.provideMerchantCategoryMappingRepository(application)
        val newCustomSmsRule = ServiceLocator.provideCustomSmsRuleRepository(application)
        val newIgnoreRule = ServiceLocator.provideIgnoreRuleRepository(application)
        val newWorkScheduler = ServiceLocator.provideWorkScheduler(application)

        assertNotNull(newTxn)
        assertNotSame(mockTxn, newTxn)
        assertNotNull(newAccount)
        assertNotSame(mockAccount, newAccount)
        assertNotNull(newCategory)
        assertNotSame(mockCategory, newCategory)
        assertNotNull(newTag)
        assertNotSame(mockTag, newTag)
        assertNotNull(newSms)
        assertNotSame(mockSms, newSms)
        assertNotNull(newMergeAccounts)
        assertNotSame(mockMergeAccounts, newMergeAccounts)
        assertNotNull(newManageReimbursement)
        assertNotSame(mockManageReimbursement, newManageReimbursement)
        assertNotNull(newMergeTransactions)
        assertNotSame(mockMergeTransactions, newMergeTransactions)
        assertNotNull(newAutoSave)
        assertNotSame(mockAutoSave, newAutoSave)
        assertNotNull(newGetMonthlyConsistency)
        assertNotSame(mockGetMonthlyConsistency, newGetMonthlyConsistency)
        assertNotNull(newScheduleWorker)
        assertNotSame(mockScheduleWorker, newScheduleWorker)
        assertNotNull(newProcessDueUseCase)
        assertNotSame(mockProcessDueUseCase, newProcessDueUseCase)
        assertNotNull(newAnalyzePatternsUseCase)
        assertNotSame(mockAnalyzePatternsUseCase, newAnalyzePatternsUseCase)
        assertNotNull(newMerchantMapping)
        assertNotSame(mockMerchantMapping, newMerchantMapping)
        assertNotNull(newBudget)
        assertNotSame(mockBudget, newBudget)
        assertNotNull(newRecurring)
        assertNotSame(mockRecurring, newRecurring)
        assertNotNull(newSplit)
        assertNotSame(mockSplit, newSplit)
        assertNotNull(newRename)
        assertNotSame(mockRename, newRename)
        assertNotNull(newCategoryMapping)
        assertNotSame(mockCategoryMapping, newCategoryMapping)
        assertNotNull(newCustomSmsRule)
        assertNotSame(mockCustomSmsRule, newCustomSmsRule)
        assertNotNull(newIgnoreRule)
        assertNotSame(mockIgnoreRule, newIgnoreRule)
        assertNotNull(newWorkScheduler)
        assertNotSame(mockWorkScheduler, newWorkScheduler)
    }

    // --- Concurrency / Thread-Safety Tests ---

    @Test
    fun concurrentAccess_returnsSameSingletonInstances() =
        runBlocking {
            val budgetInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideBudgetRepository(application)
                    }
                }.awaitAll()

            val recurringInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideRecurringTransactionRepository(application)
                    }
                }.awaitAll()

            val splitInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideSplitTransactionRepository(application)
                    }
                }.awaitAll()

            val renameInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideMerchantRenameRuleRepository(application)
                    }
                }.awaitAll()

            val categoryMappingInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideMerchantCategoryMappingRepository(application)
                    }
                }.awaitAll()

            val customSmsRuleInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideCustomSmsRuleRepository(application)
                    }
                }.awaitAll()

            val ignoreRuleInstances =
                (1..10).map {
                    async(Dispatchers.Default) {
                        ServiceLocator.provideIgnoreRuleRepository(application)
                    }
                }.awaitAll()

            budgetInstances.forEach { assertSame(budgetInstances.first(), it) }
            recurringInstances.forEach { assertSame(recurringInstances.first(), it) }
            splitInstances.forEach { assertSame(splitInstances.first(), it) }
            renameInstances.forEach { assertSame(renameInstances.first(), it) }
            categoryMappingInstances.forEach { assertSame(categoryMappingInstances.first(), it) }
            customSmsRuleInstances.forEach { assertSame(customSmsRuleInstances.first(), it) }
            ignoreRuleInstances.forEach { assertSame(ignoreRuleInstances.first(), it) }
        }

    // --- ViewModel & Factory Resolution Tests ---

    @Test
    fun provideTransactionViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideTransactionViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun provideTransactionViewModel_withResolvedDb_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideTransactionViewModel(application, db)
        assertNotNull(viewModel)
    }

    @Test
    fun provideTransactionViewModel_withSavedStateHandle_returnsConfiguredInstanceWithSavedStateHandle() {
        val customHandle = SavedStateHandle(mapOf("test_key" to "test_value"))
        val viewModel = ServiceLocator.provideTransactionViewModel(application, savedStateHandle = customHandle)
        assertNotNull(viewModel)
        assertSame(customHandle, viewModel.savedStateHandle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun provideTransactionViewModel_withNonApplicationContext_throwsIllegalArgumentException() {
        val nonAppContext =
            mockk<Context>(relaxed = true) {
                every { applicationContext } returns mockk<Context>(relaxed = true)
            }
        ServiceLocator.provideTransactionViewModel(nonAppContext)
    }

    @Test
    fun provideDashboardViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideDashboardViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun provideViewModelFactory_returnsFactoryInstance() {
        val factory = ServiceLocator.provideViewModelFactory(application)
        assertNotNull(factory)
    }

    @Test
    fun provideViewModelFactory_withNullContext_returnsFactoryInstance() {
        val factory = ServiceLocator.provideViewModelFactory()
        assertNotNull(factory)
    }

    @Test
    fun setTransactionViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: TransactionViewModel = mockk(relaxed = true)
        ServiceLocator.setTransactionViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideTransactionViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideTransactionViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun setDashboardViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: DashboardViewModel = mockk(relaxed = true)
        ServiceLocator.setDashboardViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideDashboardViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideDashboardViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideRuleCreationViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideRuleCreationViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setRuleCreationViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: RuleCreationViewModel = mockk(relaxed = true)
        ServiceLocator.setRuleCreationViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideRuleCreationViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideRuleCreationViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideTripRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideTripRepository(application)
        val repo2 = ServiceLocator.provideTripRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun setTripRepository_overridesProvidedInstance_andResetClearsIt() {
        val mockRepo: ITripRepository = mockk(relaxed = true)
        ServiceLocator.setTripRepository(mockRepo)

        assertSame(mockRepo, ServiceLocator.provideTripRepository(application))

        ServiceLocator.reset()
        val newRepo = ServiceLocator.provideTripRepository(application)
        assertNotNull(newRepo)
        assertNotSame(mockRepo, newRepo)
    }

    @Test
    fun provideTripRepository_withResolvedDb_usesProvidedDb() {
        ServiceLocator.reset()
        val repo = ServiceLocator.provideTripRepository(application, db)
        assertNotNull(repo)
    }

    @Test
    fun provideGoalRepository_returnsSingletonInstance() {
        val repo1 = ServiceLocator.provideGoalRepository(application)
        val repo2 = ServiceLocator.provideGoalRepository(application)

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun setGoalRepository_overridesProvidedInstance_andResetClearsIt() {
        val mockRepo: IGoalRepository = mockk(relaxed = true)
        ServiceLocator.setGoalRepository(mockRepo)

        assertSame(mockRepo, ServiceLocator.provideGoalRepository(application))

        ServiceLocator.reset()
        val newRepo = ServiceLocator.provideGoalRepository(application)
        assertNotNull(newRepo)
        assertNotSame(mockRepo, newRepo)
    }

    @Test
    fun provideGoalRepository_withResolvedDb_usesProvidedDb() {
        ServiceLocator.reset()
        val repo = ServiceLocator.provideGoalRepository(application, db)
        assertNotNull(repo)
    }

    @Test
    fun provideAccountViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideAccountViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setAccountViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: AccountViewModel = mockk(relaxed = true)
        ServiceLocator.setAccountViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideAccountViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideAccountViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideBudgetViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideBudgetViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setBudgetViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: BudgetViewModel = mockk(relaxed = true)
        ServiceLocator.setBudgetViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideBudgetViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideBudgetViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideCategoryViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideCategoryViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setCategoryViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: CategoryViewModel = mockk(relaxed = true)
        ServiceLocator.setCategoryViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideCategoryViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideCategoryViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideCurrencyViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideCurrencyViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setCurrencyViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: CurrencyViewModel = mockk(relaxed = true)
        ServiceLocator.setCurrencyViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideCurrencyViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideCurrencyViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideGoalViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideGoalViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setGoalViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: GoalViewModel = mockk(relaxed = true)
        ServiceLocator.setGoalViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideGoalViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideGoalViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideHistoricTripsViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideHistoricTripsViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setHistoricTripsViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: HistoricTripsViewModel = mockk(relaxed = true)
        ServiceLocator.setHistoricTripsViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideHistoricTripsViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideHistoricTripsViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideIncomeViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideIncomeViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setIncomeViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: IncomeViewModel = mockk(relaxed = true)
        ServiceLocator.setIncomeViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideIncomeViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideIncomeViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideOnboardingViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideOnboardingViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setOnboardingViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: OnboardingViewModel = mockk(relaxed = true)
        ServiceLocator.setOnboardingViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideOnboardingViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideOnboardingViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideProfileViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideProfileViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setProfileViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: ProfileViewModel = mockk(relaxed = true)
        ServiceLocator.setProfileViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideProfileViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideProfileViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideTagViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideTagViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setTagViewModel_overridesProvidedInstance_andResetClearsIt() {
        val mockViewModel: TagViewModel = mockk(relaxed = true)
        ServiceLocator.setTagViewModel(mockViewModel)

        assertSame(mockViewModel, ServiceLocator.provideTagViewModel(application))

        ServiceLocator.reset()
        val newViewModel = ServiceLocator.provideTagViewModel(application)
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    private inline fun <reified T : androidx.lifecycle.ViewModel> testViewModelOverrideAndReset(
        setter: (T?) -> Unit,
        provider: () -> T,
    ) {
        val mockViewModel: T = mockk(relaxed = true)
        setter(mockViewModel)
        assertSame(mockViewModel, provider())

        ServiceLocator.reset()
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        val mockNerExtractor: SmsEntityExtractor = mockk(relaxed = true)
        ServiceLocator.setSmsClassifier(mockClassifier)
        ServiceLocator.setNerExtractorProvider { mockNerExtractor }
        ServiceLocator.setDispatcherProvider(TestDispatcherProvider())
        val newViewModel = provider()
        assertNotNull(newViewModel)
        assertNotSame(mockViewModel, newViewModel)
    }

    @Test
    fun provideReportsViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideReportsViewModel(application)
        assertNotNull(viewModel)
    }

    @Test
    fun setReportsViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setReportsViewModel(it) },
            provider = { ServiceLocator.provideReportsViewModel(application) },
        )
    }

    @Test
    fun provideAnalysisViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideAnalysisViewModel(application, db)
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideAnalysisViewModel(application))
    }

    @Test
    fun setAnalysisViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setAnalysisViewModel(it) },
            provider = { ServiceLocator.provideAnalysisViewModel(application) },
        )
    }

    @Test
    fun provideAnnualSimulatorViewModel_returnsConfiguredInstance() {
        val viewModel =
            ServiceLocator.provideAnnualSimulatorViewModel(
                application,
                simulationYear = 2030,
                timeProvider = io.pm.finlight.utils.SystemTimeProvider(),
            )
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideAnnualSimulatorViewModel(application))
    }

    @Test
    fun setAnnualSimulatorViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setAnnualSimulatorViewModel(it) },
            provider = { ServiceLocator.provideAnnualSimulatorViewModel(application) },
        )
    }

    @Test
    fun provideWhatIfViewModel_returnsConfiguredInstance() {
        val viewModel =
            ServiceLocator.provideWhatIfViewModel(
                application,
                timeProvider = io.pm.finlight.utils.SystemTimeProvider(),
            )
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideWhatIfViewModel(application))
    }

    @Test
    fun setWhatIfViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setWhatIfViewModel(it) },
            provider = { ServiceLocator.provideWhatIfViewModel(application) },
        )
    }

    @Test
    fun provideManageMerchantRulesViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideManageMerchantRulesViewModel(application, db)
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideManageMerchantRulesViewModel(application))
    }

    @Test
    fun setManageMerchantRulesViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setManageMerchantRulesViewModel(it) },
            provider = { ServiceLocator.provideManageMerchantRulesViewModel(application) },
        )
    }

    @Test
    fun provideSettingsViewModel_returnsConfiguredInstance() {
        val autoSaveUseCase: AutoSaveSmsTransactionUseCase = mockk(relaxed = true)
        val nerExtractor: io.pm.finlight.ml.SmsEntityExtractor = mockk(relaxed = true)
        val viewModel =
            ServiceLocator.provideSettingsViewModel(
                context = application,
                resolvedDb = db,
                autoSaveSmsTransactionUseCase = autoSaveUseCase,
                nerExtractorProvider = { nerExtractor },
            )
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideSettingsViewModel(application))
    }

    @Test
    fun setSettingsViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setSettingsViewModel(it) },
            provider = { ServiceLocator.provideSettingsViewModel(application) },
        )
    }

    @Test
    fun provideRecurringTransactionViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideRecurringTransactionViewModel(application, db)
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideRecurringTransactionViewModel(application))
    }

    @Test
    fun setRecurringTransactionViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setRecurringTransactionViewModel(it) },
            provider = { ServiceLocator.provideRecurringTransactionViewModel(application) },
        )
    }

    @Test
    fun provideSmsDebugViewModel_returnsConfiguredInstance() {
        val autoSaveUseCase: AutoSaveSmsTransactionUseCase = mockk(relaxed = true)
        val nerExtractor: io.pm.finlight.ml.SmsEntityExtractor = mockk(relaxed = true)
        val viewModel =
            ServiceLocator.provideSmsDebugViewModel(
                context = application,
                resolvedDb = db,
                autoSaveSmsTransactionUseCase = autoSaveUseCase,
                nerExtractorProvider = { nerExtractor },
            )
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideSmsDebugViewModel(application))
    }

    @Test
    fun setSmsDebugViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setSmsDebugViewModel(it) },
            provider = { ServiceLocator.provideSmsDebugViewModel(application) },
        )
    }

    @Test
    fun provideManageIgnoreRulesViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideManageIgnoreRulesViewModel(application, db)
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideManageIgnoreRulesViewModel(application))
    }

    @Test
    fun setManageIgnoreRulesViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setManageIgnoreRulesViewModel(it) },
            provider = { ServiceLocator.provideManageIgnoreRulesViewModel(application) },
        )
    }

    @Test
    fun provideManageParseRulesViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.provideManageParseRulesViewModel(application, db)
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.provideManageParseRulesViewModel(application))
    }

    @Test
    fun setManageParseRulesViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setManageParseRulesViewModel(it) },
            provider = { ServiceLocator.provideManageParseRulesViewModel(application) },
        )
    }

    @Test
    fun providePendingTransactionsViewModel_returnsConfiguredInstance() {
        val viewModel = ServiceLocator.providePendingTransactionsViewModel(application, db)
        assertNotNull(viewModel)
        assertNotNull(ServiceLocator.providePendingTransactionsViewModel(application))
    }

    @Test
    fun setPendingTransactionsViewModel_overridesProvidedInstance_andResetClearsIt() {
        testViewModelOverrideAndReset(
            setter = { ServiceLocator.setPendingTransactionsViewModel(it) },
            provider = { ServiceLocator.providePendingTransactionsViewModel(application) },
        )
    }

    @Test
    fun provideSmsClassifier_whenClassifierIsNull_createsAndCachesInstanceFromMlModelFactory() {
        ServiceLocator.reset()
        mockkObject(MlModelFactory)
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        every { MlModelFactory.getClassifier(application.applicationContext) } returns mockClassifier

        val classifier1 = ServiceLocator.provideSmsClassifier(application)
        val classifier2 = ServiceLocator.provideSmsClassifier(application)

        assertSame(mockClassifier, classifier1)
        assertSame(classifier1, classifier2)
        verify(exactly = 1) { MlModelFactory.getClassifier(application.applicationContext) }
    }

    @Test
    fun setSmsClassifier_overridesProvidedInstance_andResetClearsIt() {
        val customClassifier: SmsClassifier = mockk(relaxed = true)
        ServiceLocator.setSmsClassifier(customClassifier)

        assertSame(customClassifier, ServiceLocator.provideSmsClassifier(application))

        ServiceLocator.reset()
        mockkObject(MlModelFactory)
        val factoryClassifier: SmsClassifier = mockk(relaxed = true)
        every { MlModelFactory.getClassifier(any()) } returns factoryClassifier

        val newClassifier = ServiceLocator.provideSmsClassifier(application)
        assertSame(factoryClassifier, newClassifier)
        assertNotSame(customClassifier, newClassifier)
    }

    @Test
    fun provideSettingsAndSmsDebugViewModel_resolvesFallbackNerExtractor_whenNoProviderConfigured() {
        ServiceLocator.reset()
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        ServiceLocator.setSmsClassifier(mockClassifier)
        ServiceLocator.setNerExtractorProvider(null)

        mockkObject(MlModelFactory)
        val fallbackExtractor: NerExtractor = mockk(relaxed = true)
        every { MlModelFactory.getNerExtractor(application) } returns fallbackExtractor

        val settingsVm = ServiceLocator.provideSettingsViewModel(application, db)
        val smsDebugVm = ServiceLocator.provideSmsDebugViewModel(application, db)

        assertSame(fallbackExtractor, settingsVm.nerExtractorProvider.invoke())
        assertSame(fallbackExtractor, smsDebugVm.nerExtractorProvider.invoke())
    }

    @Test
    fun provideViewModels_throwsIllegalArgumentException_whenContextLacksApplication() {
        val nonAppContext = mockk<Context>()
        every { nonAppContext.applicationContext } returns null

        val viewModelProviders: List<() -> Unit> =
            listOf(
                { ServiceLocator.provideAccountViewModel(nonAppContext) },
                { ServiceLocator.provideBudgetViewModel(nonAppContext) },
                { ServiceLocator.provideCategoryViewModel(nonAppContext) },
                { ServiceLocator.provideCurrencyViewModel(nonAppContext) },
                { ServiceLocator.provideGoalViewModel(nonAppContext) },
                { ServiceLocator.provideHistoricTripsViewModel(nonAppContext) },
                { ServiceLocator.provideIncomeViewModel(nonAppContext) },
                { ServiceLocator.provideOnboardingViewModel(nonAppContext) },
                { ServiceLocator.provideProfileViewModel(nonAppContext) },
                { ServiceLocator.provideTagViewModel(nonAppContext) },
                { ServiceLocator.provideReportsViewModel(nonAppContext) },
                { ServiceLocator.provideAnalysisViewModel(nonAppContext) },
                { ServiceLocator.provideAnnualSimulatorViewModel(nonAppContext) },
                { ServiceLocator.provideWhatIfViewModel(nonAppContext) },
                { ServiceLocator.provideManageMerchantRulesViewModel(nonAppContext) },
                { ServiceLocator.provideSettingsViewModel(nonAppContext) },
                { ServiceLocator.provideRecurringTransactionViewModel(nonAppContext) },
                { ServiceLocator.provideSmsDebugViewModel(nonAppContext) },
                { ServiceLocator.provideRuleCreationViewModel(nonAppContext) },
                { ServiceLocator.provideManageIgnoreRulesViewModel(nonAppContext) },
                { ServiceLocator.provideManageParseRulesViewModel(nonAppContext) },
                { ServiceLocator.providePendingTransactionsViewModel(nonAppContext) },
            )

        for (provider in viewModelProviders) {
            assertThrows(IllegalArgumentException::class.java) {
                provider()
            }
        }
    }

    @Test
    fun provideViewModels_resolvesInstance_whenContextIsNotApplicationButProvidesApplicationContext() {
        val nonAppContext = mockk<Context>()
        every { nonAppContext.applicationContext } returns application
        setupCommonMockRepositories()
        val sampleTxn = PotentialTransaction(1L, "Bank", 10.0, "expense", "Shop", "SMS")

        val viewModels =
            listOf(
                ServiceLocator.provideAccountViewModel(nonAppContext),
                ServiceLocator.provideBudgetViewModel(nonAppContext),
                ServiceLocator.provideCategoryViewModel(nonAppContext),
                ServiceLocator.provideCurrencyViewModel(nonAppContext),
                ServiceLocator.provideGoalViewModel(nonAppContext),
                ServiceLocator.provideHistoricTripsViewModel(nonAppContext),
                ServiceLocator.provideIncomeViewModel(nonAppContext),
                ServiceLocator.provideOnboardingViewModel(nonAppContext),
                ServiceLocator.provideProfileViewModel(nonAppContext),
                ServiceLocator.provideTagViewModel(nonAppContext),
                ServiceLocator.provideReportsViewModel(nonAppContext),
                ServiceLocator.provideAnalysisViewModel(nonAppContext),
                ServiceLocator.provideAnnualSimulatorViewModel(nonAppContext),
                ServiceLocator.provideWhatIfViewModel(nonAppContext),
                ServiceLocator.provideManageMerchantRulesViewModel(nonAppContext),
                ServiceLocator.provideSettingsViewModel(nonAppContext),
                ServiceLocator.provideRecurringTransactionViewModel(nonAppContext),
                ServiceLocator.provideSmsDebugViewModel(nonAppContext),
                ServiceLocator.provideRuleCreationViewModel(nonAppContext),
                ServiceLocator.provideManageIgnoreRulesViewModel(nonAppContext),
                ServiceLocator.provideManageParseRulesViewModel(nonAppContext),
                ServiceLocator.providePendingTransactionsViewModel(nonAppContext),
                ServiceLocator.provideSplitTransactionViewModel(nonAppContext, 1),
                ServiceLocator.provideTripDetailViewModel(nonAppContext, 1, 1),
                ServiceLocator.provideLinkTransactionViewModel(nonAppContext, sampleTxn),
                ServiceLocator.provideLinkRecurringTransactionViewModel(nonAppContext, sampleTxn),
                ServiceLocator.provideDrilldownViewModel(nonAppContext, DrilldownType.CATEGORY, "F", 1, 2026),
                ServiceLocator.provideTimePeriodReportViewModel(nonAppContext, TimePeriod.MONTHLY, null, false),
                ServiceLocator.provideAnalysisDetailViewModel(nonAppContext, AnalysisDimension.CATEGORY, "1", 0L, 100L),
                ServiceLocator.provideSearchViewModel(nonAppContext),
                ServiceLocator.provideBatchAnalysisViewModel(nonAppContext),
            )

        for (viewModel in viewModels) {
            assertNotNull(viewModel)
        }
    }

    @Test
    fun provideViewModels_throwsIllegalArgumentException_whenApplicationContextIsNotApplication() {
        val nonAppContext = mockk<Context>()
        val nestedNonAppContext = mockk<Context>()
        every { nonAppContext.applicationContext } returns nestedNonAppContext
        every { nestedNonAppContext.applicationContext } returns null
        val sampleTxn = PotentialTransaction(1L, "Bank", 10.0, "expense", "Shop", "SMS")

        val viewModelProviders: List<() -> Unit> =
            listOf(
                { ServiceLocator.provideAccountViewModel(nonAppContext) },
                { ServiceLocator.provideBudgetViewModel(nonAppContext) },
                { ServiceLocator.provideCategoryViewModel(nonAppContext) },
                { ServiceLocator.provideCurrencyViewModel(nonAppContext) },
                { ServiceLocator.provideGoalViewModel(nonAppContext) },
                { ServiceLocator.provideHistoricTripsViewModel(nonAppContext) },
                { ServiceLocator.provideIncomeViewModel(nonAppContext) },
                { ServiceLocator.provideOnboardingViewModel(nonAppContext) },
                { ServiceLocator.provideProfileViewModel(nonAppContext) },
                { ServiceLocator.provideTagViewModel(nonAppContext) },
                { ServiceLocator.provideReportsViewModel(nonAppContext) },
                { ServiceLocator.provideAnalysisViewModel(nonAppContext) },
                { ServiceLocator.provideAnnualSimulatorViewModel(nonAppContext) },
                { ServiceLocator.provideWhatIfViewModel(nonAppContext) },
                { ServiceLocator.provideManageMerchantRulesViewModel(nonAppContext) },
                { ServiceLocator.provideSettingsViewModel(nonAppContext) },
                { ServiceLocator.provideRecurringTransactionViewModel(nonAppContext) },
                { ServiceLocator.provideSmsDebugViewModel(nonAppContext) },
                { ServiceLocator.provideRuleCreationViewModel(nonAppContext) },
                { ServiceLocator.provideManageIgnoreRulesViewModel(nonAppContext) },
                { ServiceLocator.provideManageParseRulesViewModel(nonAppContext) },
                { ServiceLocator.providePendingTransactionsViewModel(nonAppContext) },
                { ServiceLocator.provideSplitTransactionViewModel(nonAppContext, 1) },
                { ServiceLocator.provideTripDetailViewModel(nonAppContext, 1, 1) },
                { ServiceLocator.provideLinkTransactionViewModel(nonAppContext, sampleTxn) },
                { ServiceLocator.provideLinkRecurringTransactionViewModel(nonAppContext, sampleTxn) },
                { ServiceLocator.provideDrilldownViewModel(nonAppContext, DrilldownType.CATEGORY, "F", 1, 2026) },
                { ServiceLocator.provideTimePeriodReportViewModel(nonAppContext, TimePeriod.MONTHLY, null, false) },
                { ServiceLocator.provideAnalysisDetailViewModel(nonAppContext, AnalysisDimension.CATEGORY, "1", 0L, 100L) },
                { ServiceLocator.provideSearchViewModel(nonAppContext) },
            )

        for (provider in viewModelProviders) {
            assertThrows(IllegalArgumentException::class.java) {
                provider()
            }
        }
    }

    @Test
    fun provideParameterizedViewModels_resolvesInstancesAndForwardsArguments() {
        setupCommonMockRepositories()

        val sampleTxn = PotentialTransaction(101L, "Bank", 75.0, "expense", "Grocery", "SMS Body")

        val splitVM = ServiceLocator.provideSplitTransactionViewModel(application, 42)
        assertNotNull(splitVM)
        val splitTxnId =
            SplitTransactionViewModel::class.java.getDeclaredField("transactionId").apply {
                isAccessible = true
            }.get(splitVM)
        org.junit.Assert.assertEquals(42, splitTxnId)

        val tripVM = ServiceLocator.provideTripDetailViewModel(application, 5, 10)
        assertNotNull(tripVM)

        val linkVM = ServiceLocator.provideLinkTransactionViewModel(application, sampleTxn)
        assertNotNull(linkVM)
        org.junit.Assert.assertSame(sampleTxn, linkVM.potentialTransaction)

        val linkRecVM = ServiceLocator.provideLinkRecurringTransactionViewModel(application, sampleTxn)
        assertNotNull(linkRecVM)
        org.junit.Assert.assertSame(sampleTxn, linkRecVM.potentialTransaction)

        val drilldownVM =
            ServiceLocator.provideDrilldownViewModel(application, DrilldownType.MERCHANT, "Supermarket", 3, 2026)
        assertNotNull(drilldownVM)
        org.junit.Assert.assertEquals("Supermarket", drilldownVM.entityName)

        val reportVM =
            ServiceLocator.provideTimePeriodReportViewModel(application, TimePeriod.YEARLY, 1000L, true)
        assertNotNull(reportVM)

        val analysisVM =
            ServiceLocator.provideAnalysisDetailViewModel(
                application,
                AnalysisDimension.TAG,
                "9",
                1000L,
                2000L,
            )
        assertNotNull(analysisVM)

        val searchVM = ServiceLocator.provideSearchViewModel(application, 4, 1234L, "Coffee")
        assertNotNull(searchVM)
        org.junit.Assert.assertEquals("Coffee", searchVM.uiState.value.keyword)

        val batchVM = ServiceLocator.provideBatchAnalysisViewModel(application)
        assertNotNull(batchVM)
    }

    @Test
    fun provideParameterizedViewModels_usesOverridesWhenSet() {
        val mockSplit: SplitTransactionViewModel = mockk(relaxed = true)
        val mockTrip: TripDetailViewModel = mockk(relaxed = true)
        val mockLink: LinkTransactionViewModel = mockk(relaxed = true)
        val mockLinkRec: LinkRecurringTransactionViewModel = mockk(relaxed = true)
        val mockDrilldown: DrilldownViewModel = mockk(relaxed = true)
        val mockReport: TimePeriodReportViewModel = mockk(relaxed = true)
        val mockAnalysis: AnalysisDetailViewModel = mockk(relaxed = true)
        val mockSearch: SearchViewModel = mockk(relaxed = true)
        val mockBatch: BatchAnalysisViewModel = mockk(relaxed = true)

        ServiceLocator.setSplitTransactionViewModel(mockSplit)
        ServiceLocator.setTripDetailViewModel(mockTrip)
        ServiceLocator.setLinkTransactionViewModel(mockLink)
        ServiceLocator.setLinkRecurringTransactionViewModel(mockLinkRec)
        ServiceLocator.setDrilldownViewModel(mockDrilldown)
        ServiceLocator.setTimePeriodReportViewModel(mockReport)
        ServiceLocator.setAnalysisDetailViewModel(mockAnalysis)
        ServiceLocator.setSearchViewModel(mockSearch)
        ServiceLocator.setBatchAnalysisViewModel(mockBatch)

        val sampleTxn = PotentialTransaction(1L, "B", 1.0, "expense", "M", "S")
        org.junit.Assert.assertSame(mockSplit, ServiceLocator.provideSplitTransactionViewModel(application, 1))
        org.junit.Assert.assertSame(mockTrip, ServiceLocator.provideTripDetailViewModel(application, 1, 1))
        org.junit.Assert.assertSame(mockLink, ServiceLocator.provideLinkTransactionViewModel(application, sampleTxn))
        org.junit.Assert.assertSame(mockLinkRec, ServiceLocator.provideLinkRecurringTransactionViewModel(application, sampleTxn))
        org.junit.Assert.assertSame(mockDrilldown, ServiceLocator.provideDrilldownViewModel(application, DrilldownType.CATEGORY, "F", 1, 2026))
        org.junit.Assert.assertSame(mockReport, ServiceLocator.provideTimePeriodReportViewModel(application, TimePeriod.MONTHLY, null, false))
        org.junit.Assert.assertSame(mockAnalysis, ServiceLocator.provideAnalysisDetailViewModel(application, AnalysisDimension.CATEGORY, "1", 0L, 1L))
        org.junit.Assert.assertSame(mockSearch, ServiceLocator.provideSearchViewModel(application))
        org.junit.Assert.assertSame(mockBatch, ServiceLocator.provideBatchAnalysisViewModel(application))

        ServiceLocator.reset()
        val freshClassifier: SmsClassifier = mockk(relaxed = true)
        val freshNer: SmsEntityExtractor = mockk(relaxed = true)
        ServiceLocator.setSmsClassifier(freshClassifier)
        ServiceLocator.setNerExtractorProvider { freshNer }
        setupCommonMockRepositories()
        ServiceLocator.setDispatcherProvider(TestDispatcherProvider())

        org.junit.Assert.assertNotSame(mockSplit, ServiceLocator.provideSplitTransactionViewModel(application, 1))
        org.junit.Assert.assertNotSame(mockTrip, ServiceLocator.provideTripDetailViewModel(application, 1, 1))
        org.junit.Assert.assertNotSame(mockLink, ServiceLocator.provideLinkTransactionViewModel(application, sampleTxn))
        org.junit.Assert.assertNotSame(mockLinkRec, ServiceLocator.provideLinkRecurringTransactionViewModel(application, sampleTxn))
        org.junit.Assert.assertNotSame(mockDrilldown, ServiceLocator.provideDrilldownViewModel(application, DrilldownType.CATEGORY, "F", 1, 2026))
        org.junit.Assert.assertNotSame(mockReport, ServiceLocator.provideTimePeriodReportViewModel(application, TimePeriod.MONTHLY, null, false))
        org.junit.Assert.assertNotSame(mockAnalysis, ServiceLocator.provideAnalysisDetailViewModel(application, AnalysisDimension.CATEGORY, "1", 0L, 1L))
        org.junit.Assert.assertNotSame(mockSearch, ServiceLocator.provideSearchViewModel(application))
        org.junit.Assert.assertNotSame(mockBatch, ServiceLocator.provideBatchAnalysisViewModel(application))
    }

    @Test
    fun provideParameterizedViewModels_usesExplicitParameterOverrides() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockCatRepo: ICategoryRepository = mockk(relaxed = true)
        val mockSplitRepo: ISplitTransactionRepository = mockk(relaxed = true)
        val mockTripRepo: ITripRepository = mockk(relaxed = true)
        val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        val mockUseCase: GetMonthlyConsistencyDataUseCase = mockk(relaxed = true)
        val mockDao: io.pm.finlight.data.db.dao.TransactionAnalyticsDao = mockk(relaxed = true)
        val mockAccountRepo: io.pm.finlight.IAccountRepository = mockk(relaxed = true)
        val mockTagRepo: ITagRepository = mockk(relaxed = true)
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        val mockNer: SmsEntityExtractor = mockk(relaxed = true)
        val sampleTxn = PotentialTransaction(1L, "B", 1.0, "expense", "M", "S")

        val splitVM =
            ServiceLocator.provideSplitTransactionViewModel(
                context = application,
                transactionId = 88,
                transactionRepository = mockTxnRepo,
                categoryRepository = mockCatRepo,
                splitTransactionRepository = mockSplitRepo,
            )
        assertNotNull(splitVM)

        val tripVM =
            ServiceLocator.provideTripDetailViewModel(
                context = application,
                tripId = 2,
                tagId = 3,
                tripRepository = mockTripRepo,
                transactionRepository = mockTxnRepo,
            )
        assertNotNull(tripVM)

        val linkVM =
            ServiceLocator.provideLinkTransactionViewModel(
                context = application,
                potentialTransaction = sampleTxn,
                transactionRepository = mockTxnRepo,
            )
        assertNotNull(linkVM)

        val linkRecVM =
            ServiceLocator.provideLinkRecurringTransactionViewModel(
                context = application,
                potentialTransaction = sampleTxn,
                transactionRepository = mockTxnRepo,
                recurringTransactionRepository = mockRecurringRepo,
            )
        assertNotNull(linkRecVM)

        val drilldownVM =
            ServiceLocator.provideDrilldownViewModel(
                context = application,
                drilldownType = DrilldownType.CATEGORY,
                entityName = "Groceries",
                month = 2,
                year = 2026,
                transactionRepository = mockTxnRepo,
            )
        assertNotNull(drilldownVM)

        val reportVM =
            ServiceLocator.provideTimePeriodReportViewModel(
                context = application,
                timePeriod = TimePeriod.MONTHLY,
                initialDateMillis = 500L,
                showPreviousMonth = false,
                settingsRepository = mockSettingsRepo,
                transactionRepository = mockTxnRepo,
                getMonthlyConsistencyDataUseCase = mockUseCase,
            )
        assertNotNull(reportVM)

        val analysisVM =
            ServiceLocator.provideAnalysisDetailViewModel(
                context = application,
                dimension = AnalysisDimension.CATEGORY,
                dimensionId = "1",
                startDate = 0L,
                endDate = 500L,
                transactionAnalyticsDao = mockDao,
            )
        assertNotNull(analysisVM)

        val searchVM =
            ServiceLocator.provideSearchViewModel(
                context = application,
                initialCategoryId = 2,
                initialDateMillis = 300L,
                initialQuery = "Query",
                transactionRepository = mockTxnRepo,
                accountRepository = mockAccountRepo,
                categoryRepository = mockCatRepo,
                tagRepository = mockTagRepo,
            )
        assertNotNull(searchVM)

        val batchVM =
            ServiceLocator.provideBatchAnalysisViewModel(
                context = application,
                nerExtractorProvider = { mockNer },
                classifier = mockClassifier,
            )
        assertNotNull(batchVM)
    }
}
