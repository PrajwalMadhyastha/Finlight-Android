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
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.viewmodel.RuleCreationViewModel
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
    fun create_withCreationExtras_resolvesApplicationFromExtras() {
        val factory = FinlightViewModelFactory.create()
        val extras =
            MutableCreationExtras().apply {
                set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
            }

        val txnViewModel = factory.create(TransactionViewModel::class.java, extras)
        assertNotNull(txnViewModel)

        val dashboardViewModel = factory.create(DashboardViewModel::class.java, extras)
        assertNotNull(dashboardViewModel)

        val ruleCreationViewModel = factory.create(RuleCreationViewModel::class.java, extras)
        assertNotNull(ruleCreationViewModel)
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

        ServiceLocator.setTransactionViewModel(mockTxnViewModel)
        ServiceLocator.setDashboardViewModel(mockDashboardViewModel)
        ServiceLocator.setRuleCreationViewModel(mockRuleCreationViewModel)

        val factory = FinlightViewModelFactory.create(application)

        val txnViewModel = factory.create(TransactionViewModel::class.java)
        val dashboardViewModel = factory.create(DashboardViewModel::class.java)
        val ruleCreationViewModel = factory.create(RuleCreationViewModel::class.java)

        assertSame(mockTxnViewModel, txnViewModel)
        assertSame(mockDashboardViewModel, dashboardViewModel)
        assertSame(mockRuleCreationViewModel, ruleCreationViewModel)
    }
}
