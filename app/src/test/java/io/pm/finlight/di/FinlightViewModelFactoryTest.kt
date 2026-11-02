 package io.pm.finlight.di

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.DashboardViewModel
import io.pm.finlight.IAccountRepository
import io.pm.finlight.IBudgetRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.IGoalRepository
import io.pm.finlight.IIgnoreRuleRepository
import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.SmsDebugViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.AnalysisDetailViewModel
import io.pm.finlight.ui.viewmodel.AnalysisDimension
import io.pm.finlight.ui.viewmodel.AnalysisViewModel
import io.pm.finlight.ui.viewmodel.AnnualSimulatorViewModel
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
import io.pm.finlight.ui.viewmodel.ManageIgnoreRulesViewModel
import io.pm.finlight.ui.viewmodel.ManageMerchantRulesViewModel
import io.pm.finlight.ui.viewmodel.ManageParseRulesViewModel
import io.pm.finlight.ui.viewmodel.OnboardingViewModel
import io.pm.finlight.ui.viewmodel.PendingTransactionsViewModel
import io.pm.finlight.ui.viewmodel.ProfileViewModel
import io.pm.finlight.ui.viewmodel.ReportsViewModel
import io.pm.finlight.ui.viewmodel.RuleCreationViewModel
import io.pm.finlight.ui.viewmodel.SearchViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.ui.viewmodel.SplitTransactionViewModel
import io.pm.finlight.ui.viewmodel.TagViewModel
import io.pm.finlight.ui.viewmodel.TimePeriodReportViewModel
import io.pm.finlight.ui.viewmodel.TripDetailViewModel
import io.pm.finlight.ui.viewmodel.WhatIfViewModel
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class FinlightViewModelFactoryTest {
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
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withTransactionViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)

        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(TransactionViewModel::class.java)

        assertNotNull(viewModel)
        assertSame(mockTxnRepo, viewModel.transactionRepository)
        assertSame(mockAccountRepo, viewModel.accountRepository)
        assertSame(mockCategoryRepo, viewModel.categoryRepository)
    }

    @Test
    fun create_withDashboardViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockBudgetRepo: IBudgetRepository = mockk(relaxed = true)
        val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)

        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setBudgetRepository(mockBudgetRepo)
        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)

        val factory = FinlightViewModelFactory(application)
        val viewModel = factory.create(DashboardViewModel::class.java)

        assertNotNull(viewModel)
        val txnField =
            DashboardViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)

        val accField =
            DashboardViewModel::class.java.getDeclaredField("accountRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockAccountRepo, accField)
    }

    @Test
    fun create_withRuleCreationViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockRuleRepo: ICustomSmsRuleRepository = mockk(relaxed = true)
        ServiceLocator.setCustomSmsRuleRepository(mockRuleRepo)

        val factory = FinlightViewModelFactory(application)
        val viewModel = factory.create(RuleCreationViewModel::class.java)

        assertNotNull(viewModel)
        val repoField =
            RuleCreationViewModel::class.java.getDeclaredField("customSmsRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRuleRepo, repoField)
    }

    @Test
    fun create_withAccountViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        val mockMergeAccountsUseCase: MergeAccountsUseCase = mockk(relaxed = true)

        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setMergeAccountsUseCase(mockMergeAccountsUseCase)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(AccountViewModel::class.java)

        assertNotNull(viewModel)
        val repoField =
            AccountViewModel::class.java.getDeclaredField("repository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockAccountRepo, repoField)
    }

    @Test
    fun create_withBudgetViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockBudgetRepo: IBudgetRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        every { mockTxnRepo.getFirstTransactionDate() } returns flowOf(null)

        ServiceLocator.setBudgetRepository(mockBudgetRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(BudgetViewModel::class.java)

        assertNotNull(viewModel)
        val repoField =
            BudgetViewModel::class.java.getDeclaredField("budgetRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockBudgetRepo, repoField)
    }

    @Test
    fun create_withCategoryViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockCatRepo: ICategoryRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        ServiceLocator.setCategoryRepository(mockCatRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(CategoryViewModel::class.java)

        assertNotNull(viewModel)
        val catField =
            CategoryViewModel::class.java.getDeclaredField("categoryRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCatRepo, catField)
    }

    @Test
    fun create_withCurrencyViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        val mockTripRepo: ITripRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockTagRepo: ITagRepository = mockk(relaxed = true)

        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setTripRepository(mockTripRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setTagRepository(mockTagRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(CurrencyViewModel::class.java)

        assertNotNull(viewModel)
        val tripField =
            CurrencyViewModel::class.java.getDeclaredField("tripRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTripRepo, tripField)
    }

    @Test
    fun create_withGoalViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockGoalRepo: IGoalRepository = mockk(relaxed = true)
        ServiceLocator.setGoalRepository(mockGoalRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(GoalViewModel::class.java)

        assertNotNull(viewModel)
        val repoField =
            GoalViewModel::class.java.getDeclaredField("goalRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockGoalRepo, repoField)
    }

    @Test
    fun create_withHistoricTripsViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockTripRepo: ITripRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        ServiceLocator.setTripRepository(mockTripRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(HistoricTripsViewModel::class.java)

        assertNotNull(viewModel)
        val tripField =
            HistoricTripsViewModel::class.java.getDeclaredField("tripRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTripRepo, tripField)
    }

    @Test
    fun create_withIncomeViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)

        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setSettingsRepository(mockSettingsRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(IncomeViewModel::class.java)

        assertNotNull(viewModel)
        val accField =
            IncomeViewModel::class.java.getDeclaredField("accountRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockAccountRepo, accField)
    }

    @Test
    fun create_withOnboardingViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)

        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setSettingsRepository(mockSettingsRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(OnboardingViewModel::class.java)

        assertNotNull(viewModel)
        val catField =
            OnboardingViewModel::class.java.getDeclaredField("categoryRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCategoryRepo, catField)
    }

    @Test
    fun create_withProfileViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        ServiceLocator.setSettingsRepository(mockSettingsRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(ProfileViewModel::class.java)

        assertNotNull(viewModel)
        val settingsField =
            ProfileViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsField)
    }

    @Test
    fun create_withTagViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockTagRepo: ITagRepository = mockk(relaxed = true)
        val mockTripRepo: ITripRepository = mockk(relaxed = true)

        ServiceLocator.setTagRepository(mockTagRepo)
        ServiceLocator.setTripRepository(mockTripRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(TagViewModel::class.java)

        assertNotNull(viewModel)
        val tagField =
            TagViewModel::class.java.getDeclaredField("tagRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTagRepo, tagField)
    }

    @Test
    fun create_withReportsViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockCatRepo: ICategoryRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockUseCase: GetMonthlyConsistencyDataUseCase = mockk(relaxed = true)

        ServiceLocator.setCategoryRepository(mockCatRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setGetMonthlyConsistencyDataUseCase(mockUseCase)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(ReportsViewModel::class.java)

        assertNotNull(viewModel)
        val catField =
            ReportsViewModel::class.java.getDeclaredField("categoryRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCatRepo, catField)
    }

    @Test
    fun create_withAnalysisViewModelClass_resolvesDependenciesFromServiceLocator() {
        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(AnalysisViewModel::class.java)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withAnnualSimulatorViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(AnnualSimulatorViewModel::class.java)

        assertNotNull(viewModel)
        val settingsField =
            AnnualSimulatorViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsField)
    }

    @Test
    fun create_withWhatIfViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(WhatIfViewModel::class.java)

        assertNotNull(viewModel)
        val settingsField =
            WhatIfViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsField)
    }

    @Test
    fun create_withManageMerchantRulesViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockRenameRepo: IMerchantRenameRuleRepository = mockk(relaxed = true)
        val mockCatMappingRepo: IMerchantCategoryMappingRepository = mockk(relaxed = true)
        every { mockRenameRepo.getAllRules() } returns flowOf(emptyList())
        every { mockCatMappingRepo.getAllMappings() } returns flowOf(emptyList())

        ServiceLocator.setMerchantRenameRuleRepository(mockRenameRepo)
        ServiceLocator.setMerchantCategoryMappingRepository(mockCatMappingRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(ManageMerchantRulesViewModel::class.java)

        assertNotNull(viewModel)
        val renameField =
            ManageMerchantRulesViewModel::class.java.getDeclaredField("merchantRenameRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRenameRepo, renameField)
    }

    @Test
    fun create_withSettingsViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        every { mockSettingsRepo.getSelectedTheme() } returns flowOf(io.pm.finlight.ui.theme.AppTheme.SYSTEM_DEFAULT)
        every { mockSettingsRepo.getHasSeenOnboarding() } returns flowOf(false)
        every { mockSettingsRepo.getPrivacyModeEnabled() } returns flowOf(false)
        ServiceLocator.setSettingsRepository(mockSettingsRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(SettingsViewModel::class.java)

        assertNotNull(viewModel)
        val settingsField =
            SettingsViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsField)
    }

    @Test
    fun create_withRecurringTransactionViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        every { mockRepo.getAll() } returns flowOf(emptyList())
        ServiceLocator.setRecurringTransactionRepository(mockRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(RecurringTransactionViewModel::class.java)

        assertNotNull(viewModel)
        val repoField =
            RecurringTransactionViewModel::class.java.getDeclaredField("repository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRepo, repoField)
    }

    @Test
    fun create_withSmsDebugViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockRepo: ISmsRepository = mockk(relaxed = true)
        ServiceLocator.setSmsRepository(mockRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(SmsDebugViewModel::class.java)

        assertNotNull(viewModel)
        val repoField =
            SmsDebugViewModel::class.java.getDeclaredField("smsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRepo, repoField)
    }

    @Test
    fun create_withManageIgnoreRulesViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockRepo: IIgnoreRuleRepository = mockk(relaxed = true)
        every { mockRepo.getAllRules() } returns flowOf(emptyList())
        ServiceLocator.setIgnoreRuleRepository(mockRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(ManageIgnoreRulesViewModel::class.java)

        assertNotNull(viewModel)
        val repoField =
            ManageIgnoreRulesViewModel::class.java.getDeclaredField("ignoreRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRepo, repoField)
    }

    @Test
    fun create_withManageParseRulesViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockRepo: ICustomSmsRuleRepository = mockk(relaxed = true)
        every { mockRepo.getAllRules() } returns flowOf(emptyList())
        ServiceLocator.setCustomSmsRuleRepository(mockRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(ManageParseRulesViewModel::class.java)

        assertNotNull(viewModel)
        val repoField =
            ManageParseRulesViewModel::class.java.getDeclaredField("customSmsRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRepo, repoField)
    }

    @Test
    fun create_withPendingTransactionsViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        every { mockTxnRepo.getPendingTransactionsFlow() } returns flowOf(emptyList())
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)

        val factory = FinlightViewModelFactory.create(application)
        val viewModel = factory.create(PendingTransactionsViewModel::class.java)

        assertNotNull(viewModel)
        val txnRepoField =
            PendingTransactionsViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnRepoField)
    }

    @Test
    fun create_withCreationExtras_resolvesApplicationFromExtras() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        every { mockTxnRepo.getFirstTransactionDate() } returns flowOf(null)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val factory = FinlightViewModelFactory.create()
        val extras =
            MutableCreationExtras().apply {
                set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
            }

        assertNotNull(factory.create(TransactionViewModel::class.java, extras))
        assertNotNull(factory.create(DashboardViewModel::class.java, extras))
        assertNotNull(factory.create(RuleCreationViewModel::class.java, extras))
        assertNotNull(factory.create(AccountViewModel::class.java, extras))
        assertNotNull(factory.create(BudgetViewModel::class.java, extras))
        assertNotNull(factory.create(CategoryViewModel::class.java, extras))
        assertNotNull(factory.create(CurrencyViewModel::class.java, extras))
        assertNotNull(factory.create(GoalViewModel::class.java, extras))
        assertNotNull(factory.create(HistoricTripsViewModel::class.java, extras))
        assertNotNull(factory.create(IncomeViewModel::class.java, extras))
        assertNotNull(factory.create(OnboardingViewModel::class.java, extras))
        assertNotNull(factory.create(ProfileViewModel::class.java, extras))
        assertNotNull(factory.create(TagViewModel::class.java, extras))
        assertNotNull(factory.create(ReportsViewModel::class.java, extras))
        assertNotNull(factory.create(AnalysisViewModel::class.java, extras))
        assertNotNull(factory.create(AnnualSimulatorViewModel::class.java, extras))
        assertNotNull(factory.create(WhatIfViewModel::class.java, extras))
        assertNotNull(factory.create(ManageMerchantRulesViewModel::class.java, extras))
        assertNotNull(factory.create(SettingsViewModel::class.java, extras))
        assertNotNull(factory.create(RecurringTransactionViewModel::class.java, extras))
        assertNotNull(factory.create(SmsDebugViewModel::class.java, extras))
        assertNotNull(factory.create(ManageIgnoreRulesViewModel::class.java, extras))
        assertNotNull(factory.create(ManageParseRulesViewModel::class.java, extras))
        assertNotNull(factory.create(PendingTransactionsViewModel::class.java, extras))
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        val factory = FinlightViewModelFactory.create(application)
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }

    @Test
    fun create_withoutApplicationContext_throwsIllegalArgumentException() {
        val factory = FinlightViewModelFactory.create(null)
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(TransactionViewModel::class.java, CreationExtras.Empty)
        }
    }

    @Test
    fun create_withWrapperContext_resolvesApplicationFromApplicationContext() {
        val mockContext =
            mockk<Context>(relaxed = true) {
                every { applicationContext } returns application
            }
        val factory = FinlightViewModelFactory.create(mockContext)
        val viewModel = factory.create(TransactionViewModel::class.java)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withNonApplicationContext_throwsIllegalArgumentException() {
        val mockContext =
            mockk<Context>(relaxed = true) {
                every { applicationContext } returns mockk<Context>(relaxed = true)
            }
        val factory = FinlightViewModelFactory.create(mockContext)
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(TransactionViewModel::class.java)
        }
    }

    @Test
    fun create_withOverriddenViewModelsInServiceLocator_returnsMockedInstances() {
        val mockTxnViewModel: TransactionViewModel = mockk(relaxed = true)
        val mockDashboardViewModel: DashboardViewModel = mockk(relaxed = true)
        val mockRuleCreationViewModel: RuleCreationViewModel = mockk(relaxed = true)
        val mockAccountViewModel: AccountViewModel = mockk(relaxed = true)
        val mockBudgetViewModel: BudgetViewModel = mockk(relaxed = true)
        val mockCategoryViewModel: CategoryViewModel = mockk(relaxed = true)
        val mockCurrencyViewModel: CurrencyViewModel = mockk(relaxed = true)
        val mockGoalViewModel: GoalViewModel = mockk(relaxed = true)
        val mockHistoricTripsViewModel: HistoricTripsViewModel = mockk(relaxed = true)
        val mockIncomeViewModel: IncomeViewModel = mockk(relaxed = true)
        val mockOnboardingViewModel: OnboardingViewModel = mockk(relaxed = true)
        val mockProfileViewModel: ProfileViewModel = mockk(relaxed = true)
        val mockTagViewModel: TagViewModel = mockk(relaxed = true)
        val mockReportsViewModel: ReportsViewModel = mockk(relaxed = true)
        val mockAnalysisViewModel: AnalysisViewModel = mockk(relaxed = true)
        val mockAnnualSimulatorViewModel: AnnualSimulatorViewModel = mockk(relaxed = true)
        val mockWhatIfViewModel: WhatIfViewModel = mockk(relaxed = true)
        val mockManageMerchantRulesViewModel: ManageMerchantRulesViewModel = mockk(relaxed = true)
        val mockSettingsViewModel: SettingsViewModel = mockk(relaxed = true)
        val mockRecurringTransactionViewModel: RecurringTransactionViewModel = mockk(relaxed = true)
        val mockSmsDebugViewModel: SmsDebugViewModel = mockk(relaxed = true)
        val mockManageIgnoreRulesViewModel: ManageIgnoreRulesViewModel = mockk(relaxed = true)
        val mockManageParseRulesViewModel: ManageParseRulesViewModel = mockk(relaxed = true)
        val mockPendingTransactionsViewModel: PendingTransactionsViewModel = mockk(relaxed = true)

        ServiceLocator.setTransactionViewModel(mockTxnViewModel)
        ServiceLocator.setDashboardViewModel(mockDashboardViewModel)
        ServiceLocator.setRuleCreationViewModel(mockRuleCreationViewModel)
        ServiceLocator.setAccountViewModel(mockAccountViewModel)
        ServiceLocator.setBudgetViewModel(mockBudgetViewModel)
        ServiceLocator.setCategoryViewModel(mockCategoryViewModel)
        ServiceLocator.setCurrencyViewModel(mockCurrencyViewModel)
        ServiceLocator.setGoalViewModel(mockGoalViewModel)
        ServiceLocator.setHistoricTripsViewModel(mockHistoricTripsViewModel)
        ServiceLocator.setIncomeViewModel(mockIncomeViewModel)
        ServiceLocator.setOnboardingViewModel(mockOnboardingViewModel)
        ServiceLocator.setProfileViewModel(mockProfileViewModel)
        ServiceLocator.setTagViewModel(mockTagViewModel)
        ServiceLocator.setReportsViewModel(mockReportsViewModel)
        ServiceLocator.setAnalysisViewModel(mockAnalysisViewModel)
        ServiceLocator.setAnnualSimulatorViewModel(mockAnnualSimulatorViewModel)
        ServiceLocator.setWhatIfViewModel(mockWhatIfViewModel)
        ServiceLocator.setManageMerchantRulesViewModel(mockManageMerchantRulesViewModel)
        ServiceLocator.setSettingsViewModel(mockSettingsViewModel)
        ServiceLocator.setRecurringTransactionViewModel(mockRecurringTransactionViewModel)
        ServiceLocator.setSmsDebugViewModel(mockSmsDebugViewModel)
        ServiceLocator.setManageIgnoreRulesViewModel(mockManageIgnoreRulesViewModel)
        ServiceLocator.setManageParseRulesViewModel(mockManageParseRulesViewModel)
        ServiceLocator.setPendingTransactionsViewModel(mockPendingTransactionsViewModel)

        val factory = FinlightViewModelFactory.create(application)

        assertSame(mockTxnViewModel, factory.create(TransactionViewModel::class.java))
        assertSame(mockDashboardViewModel, factory.create(DashboardViewModel::class.java))
        assertSame(mockRuleCreationViewModel, factory.create(RuleCreationViewModel::class.java))
        assertSame(mockAccountViewModel, factory.create(AccountViewModel::class.java))
        assertSame(mockBudgetViewModel, factory.create(BudgetViewModel::class.java))
        assertSame(mockCategoryViewModel, factory.create(CategoryViewModel::class.java))
        assertSame(mockCurrencyViewModel, factory.create(CurrencyViewModel::class.java))
        assertSame(mockGoalViewModel, factory.create(GoalViewModel::class.java))
        assertSame(mockHistoricTripsViewModel, factory.create(HistoricTripsViewModel::class.java))
        assertSame(mockIncomeViewModel, factory.create(IncomeViewModel::class.java))
        assertSame(mockOnboardingViewModel, factory.create(OnboardingViewModel::class.java))
        assertSame(mockProfileViewModel, factory.create(ProfileViewModel::class.java))
        assertSame(mockTagViewModel, factory.create(TagViewModel::class.java))
        assertSame(mockReportsViewModel, factory.create(ReportsViewModel::class.java))
        assertSame(mockAnalysisViewModel, factory.create(AnalysisViewModel::class.java))
        assertSame(mockAnnualSimulatorViewModel, factory.create(AnnualSimulatorViewModel::class.java))
        assertSame(mockWhatIfViewModel, factory.create(WhatIfViewModel::class.java))
        assertSame(mockManageMerchantRulesViewModel, factory.create(ManageMerchantRulesViewModel::class.java))
        assertSame(mockSettingsViewModel, factory.create(SettingsViewModel::class.java))
        assertSame(mockRecurringTransactionViewModel, factory.create(RecurringTransactionViewModel::class.java))
        assertSame(mockSmsDebugViewModel, factory.create(SmsDebugViewModel::class.java))
        assertSame(mockManageIgnoreRulesViewModel, factory.create(ManageIgnoreRulesViewModel::class.java))
        assertSame(mockManageParseRulesViewModel, factory.create(ManageParseRulesViewModel::class.java))
        assertSame(mockPendingTransactionsViewModel, factory.create(PendingTransactionsViewModel::class.java))
    }

    @Test
    fun create_withCreationExtras_parameterizedViewModels_resolvesSuccessfully() {
        setupMockRepositories()

        val sampleTxn = PotentialTransaction(1L, "Bank", 10.0, "expense", "Shop", "SMS")
        val factory = FinlightViewModelFactory.create(application)

        val splitExtras =
            MutableCreationExtras().apply {
                set(FinlightViewModelFactory.SPLIT_TRANSACTION_ID_KEY, 12)
            }
        assertNotNull(factory.create(SplitTransactionViewModel::class.java, splitExtras))

        val tripExtras =
            MutableCreationExtras().apply {
                set(FinlightViewModelFactory.TRIP_ID_KEY, 1)
                set(FinlightViewModelFactory.TAG_ID_KEY, 2)
            }
        assertNotNull(factory.create(TripDetailViewModel::class.java, tripExtras))

        val linkExtras =
            MutableCreationExtras().apply {
                set(FinlightViewModelFactory.POTENTIAL_TRANSACTION_KEY, sampleTxn)
            }
        assertNotNull(factory.create(LinkTransactionViewModel::class.java, linkExtras))
        assertNotNull(factory.create(LinkRecurringTransactionViewModel::class.java, linkExtras))

        val drilldownExtras =
            MutableCreationExtras().apply {
                set(FinlightViewModelFactory.DRILLDOWN_TYPE_KEY, DrilldownType.CATEGORY)
                set(FinlightViewModelFactory.ENTITY_NAME_KEY, "Food")
                set(FinlightViewModelFactory.MONTH_KEY, 5)
                set(FinlightViewModelFactory.YEAR_KEY, 2026)
            }
        assertNotNull(factory.create(DrilldownViewModel::class.java, drilldownExtras))

        val reportExtras =
            MutableCreationExtras().apply {
                set(FinlightViewModelFactory.TIME_PERIOD_KEY, TimePeriod.MONTHLY)
            }
        assertNotNull(factory.create(TimePeriodReportViewModel::class.java, reportExtras))

        val analysisExtras =
            MutableCreationExtras().apply {
                set(FinlightViewModelFactory.ANALYSIS_DIMENSION_KEY, AnalysisDimension.CATEGORY)
                set(FinlightViewModelFactory.DIMENSION_ID_KEY, "1")
                set(FinlightViewModelFactory.START_DATE_KEY, 100L)
                set(FinlightViewModelFactory.END_DATE_KEY, 200L)
            }
        assertNotNull(factory.create(AnalysisDetailViewModel::class.java, analysisExtras))

        assertNotNull(factory.create(SearchViewModel::class.java, CreationExtras.Empty))
        assertNotNull(factory.create(BatchAnalysisViewModel::class.java, CreationExtras.Empty))
    }

    @Test
    fun create_withCreationExtras_missingRequiredKeys_throwsException() {
        val factory = FinlightViewModelFactory.create(application)

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(SplitTransactionViewModel::class.java, CreationExtras.Empty)
        }
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(TripDetailViewModel::class.java, CreationExtras.Empty)
        }
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(LinkTransactionViewModel::class.java, CreationExtras.Empty)
        }
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(LinkRecurringTransactionViewModel::class.java, CreationExtras.Empty)
        }
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(DrilldownViewModel::class.java, CreationExtras.Empty)
        }
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(TimePeriodReportViewModel::class.java, CreationExtras.Empty)
        }
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(AnalysisDetailViewModel::class.java, CreationExtras.Empty)
        }
    }

    @Test
    fun createAssistedFactories_buildsExpectedViewModels() {
        setupMockRepositories()

        val sampleTxn = PotentialTransaction(1L, "Bank", 10.0, "expense", "Shop", "SMS")

        val splitFactory = FinlightViewModelFactory.createSplitTransactionFactory(application, 12)
        assertNotNull(splitFactory.create(SplitTransactionViewModel::class.java))

        val tripFactory = FinlightViewModelFactory.createTripDetailFactory(application, 1, 2)
        assertNotNull(tripFactory.create(TripDetailViewModel::class.java))

        val linkFactory = FinlightViewModelFactory.createLinkTransactionFactory(application, sampleTxn)
        assertNotNull(linkFactory.create(LinkTransactionViewModel::class.java))

        val linkRecFactory = FinlightViewModelFactory.createLinkRecurringTransactionFactory(application, sampleTxn)
        assertNotNull(linkRecFactory.create(LinkRecurringTransactionViewModel::class.java))

        val drilldownFactory =
            FinlightViewModelFactory.createDrilldownFactory(application, DrilldownType.MERCHANT, "Cafe", 1, 2026)
        assertNotNull(drilldownFactory.create(DrilldownViewModel::class.java))

        val reportFactory =
            FinlightViewModelFactory.createTimePeriodReportFactory(application, TimePeriod.YEARLY, 1000L, false)
        assertNotNull(reportFactory.create(TimePeriodReportViewModel::class.java))

        val analysisFactory =
            FinlightViewModelFactory.createAnalysisDetailFactory(
                application,
                AnalysisDimension.MERCHANT,
                "Cafe",
                0L,
                100L,
            )
        assertNotNull(analysisFactory.create(AnalysisDetailViewModel::class.java))

        val searchFactory = FinlightViewModelFactory.createSearchFactory(application, 1, 500L, "test")
        assertNotNull(searchFactory.create(SearchViewModel::class.java))

        val batchFactory = FinlightViewModelFactory.createBatchAnalysisFactory(application)
        assertNotNull(batchFactory.create(BatchAnalysisViewModel::class.java))

        class UnknownViewModel : ViewModel()
        assertThrows(IllegalArgumentException::class.java) {
            splitFactory.create(UnknownViewModel::class.java)
        }
    }

    @Test
    fun create_withOverriddenParameterizedViewModels_returnsMockedInstances() {
        val mockSplitVM: SplitTransactionViewModel = mockk(relaxed = true)
        val mockTripVM: TripDetailViewModel = mockk(relaxed = true)
        val mockLinkVM: LinkTransactionViewModel = mockk(relaxed = true)
        val mockLinkRecVM: LinkRecurringTransactionViewModel = mockk(relaxed = true)
        val mockDrilldownVM: DrilldownViewModel = mockk(relaxed = true)
        val mockReportVM: TimePeriodReportViewModel = mockk(relaxed = true)
        val mockAnalysisVM: AnalysisDetailViewModel = mockk(relaxed = true)
        val mockSearchVM: SearchViewModel = mockk(relaxed = true)
        val mockBatchVM: BatchAnalysisViewModel = mockk(relaxed = true)

        ServiceLocator.setSplitTransactionViewModel(mockSplitVM)
        ServiceLocator.setTripDetailViewModel(mockTripVM)
        ServiceLocator.setLinkTransactionViewModel(mockLinkVM)
        ServiceLocator.setLinkRecurringTransactionViewModel(mockLinkRecVM)
        ServiceLocator.setDrilldownViewModel(mockDrilldownVM)
        ServiceLocator.setTimePeriodReportViewModel(mockReportVM)
        ServiceLocator.setAnalysisDetailViewModel(mockAnalysisVM)
        ServiceLocator.setSearchViewModel(mockSearchVM)
        ServiceLocator.setBatchAnalysisViewModel(mockBatchVM)

        val sampleTxn = PotentialTransaction(1L, "Bank", 10.0, "expense", "Shop", "SMS")
        assertSame(mockSplitVM, FinlightViewModelFactory.createSplitTransactionFactory(application, 1).create(SplitTransactionViewModel::class.java))
        assertSame(mockTripVM, FinlightViewModelFactory.createTripDetailFactory(application, 1, 2).create(TripDetailViewModel::class.java))
        assertSame(mockLinkVM, FinlightViewModelFactory.createLinkTransactionFactory(application, sampleTxn).create(LinkTransactionViewModel::class.java))
        assertSame(mockLinkRecVM, FinlightViewModelFactory.createLinkRecurringTransactionFactory(application, sampleTxn).create(LinkRecurringTransactionViewModel::class.java))
        assertSame(mockDrilldownVM, FinlightViewModelFactory.createDrilldownFactory(application, DrilldownType.CATEGORY, "F", 1, 2026).create(DrilldownViewModel::class.java))
        assertSame(mockReportVM, FinlightViewModelFactory.createTimePeriodReportFactory(application, TimePeriod.MONTHLY, null, false).create(TimePeriodReportViewModel::class.java))
        assertSame(mockAnalysisVM, FinlightViewModelFactory.createAnalysisDetailFactory(application, AnalysisDimension.CATEGORY, "1", 0L, 1L).create(AnalysisDetailViewModel::class.java))
        assertSame(mockSearchVM, FinlightViewModelFactory.createSearchFactory(application).create(SearchViewModel::class.java))
        assertSame(mockBatchVM, FinlightViewModelFactory.createBatchAnalysisFactory(application).create(BatchAnalysisViewModel::class.java))
    }

    private fun setupMockRepositories() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockTagRepo: ITagRepository = mockk(relaxed = true)
        every { mockAccountRepo.allAccounts } returns flowOf(emptyList())
        every { mockCategoryRepo.allCategories } returns flowOf(emptyList())
        every { mockTagRepo.allTags } returns flowOf(emptyList())
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setTagRepository(mockTagRepo)
    }
}
