package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.BudgetViewModel
import io.pm.finlight.IBudgetRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.di.ServiceLocator
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
class BudgetViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: BudgetViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = BudgetViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withBudgetViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockBudgetRepo: IBudgetRepository = mockk(relaxed = true)
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        every { mockTxnRepo.getFirstTransactionDate() } returns flowOf(null)

        ServiceLocator.setBudgetRepository(mockBudgetRepo)
        ServiceLocator.setSettingsRepository(mockSettingsRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val viewModel = factory.create(BudgetViewModel::class.java)

        assertNotNull(viewModel)

        val budgetRepoField =
            BudgetViewModel::class.java.getDeclaredField("budgetRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockBudgetRepo, budgetRepoField)

        val settingsRepoField =
            BudgetViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsRepoField)

        val categoryRepoField =
            BudgetViewModel::class.java.getDeclaredField("categoryRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCategoryRepo, categoryRepoField)

        verify(atLeast = 1) { mockTxnRepo.getFirstTransactionDate() }
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
