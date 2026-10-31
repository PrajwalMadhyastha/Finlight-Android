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
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.ITripRepository
import io.pm.finlight.domain.usecase.MergeAccountsUseCase
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.BudgetViewModel
import io.pm.finlight.ui.viewmodel.CategoryViewModel
import io.pm.finlight.ui.viewmodel.CurrencyViewModel
import io.pm.finlight.ui.viewmodel.GoalViewModel
import io.pm.finlight.ui.viewmodel.HistoricTripsViewModel
import io.pm.finlight.ui.viewmodel.IncomeViewModel
import io.pm.finlight.ui.viewmodel.OnboardingViewModel
import io.pm.finlight.ui.viewmodel.ProfileViewModel
import io.pm.finlight.ui.viewmodel.RuleCreationViewModel
import io.pm.finlight.ui.viewmodel.TagViewModel
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
    }
}
