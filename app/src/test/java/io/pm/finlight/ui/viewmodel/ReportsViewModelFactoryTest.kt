package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
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
class ReportsViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: ReportsViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = ReportsViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withReportsViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockCatRepo: ICategoryRepository = mockk(relaxed = true)
        val mockUseCase: GetMonthlyConsistencyDataUseCase = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setCategoryRepository(mockCatRepo)
        ServiceLocator.setGetMonthlyConsistencyDataUseCase(mockUseCase)

        val viewModel = factory.create(ReportsViewModel::class.java)

        assertNotNull(viewModel)
        val txnField =
            ReportsViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)

        val catField =
            ReportsViewModel::class.java.getDeclaredField("categoryRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCatRepo, catField)

        val useCase =
            ReportsViewModel::class.java.getDeclaredField("getMonthlyConsistencyDataUseCase").apply {
                isAccessible = true
            }.get(viewModel) as GetMonthlyConsistencyDataUseCase
        assertSame(mockUseCase, useCase)
    }

    @Test
    fun create_withExtras_returnsReportsViewModel() {
        val viewModel = factory.create(ReportsViewModel::class.java, CreationExtras.Empty)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockViewModel: ReportsViewModel = mockk(relaxed = true)
        ServiceLocator.setReportsViewModel(mockViewModel)

        val resolved = factory.create(ReportsViewModel::class.java)
        assertSame(mockViewModel, resolved)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
