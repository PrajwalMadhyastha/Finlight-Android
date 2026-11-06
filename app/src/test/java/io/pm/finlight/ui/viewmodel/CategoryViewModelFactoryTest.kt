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
class CategoryViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: CategoryViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = CategoryViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withCategoryViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val viewModel = factory.create(CategoryViewModel::class.java)

        assertNotNull(viewModel)

        val catField =
            CategoryViewModel::class.java.getDeclaredField("categoryRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCategoryRepo, catField)

        val txnField =
            CategoryViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)
    }

    @Test
    fun create_withExtras_returnsCategoryViewModel() {
        val viewModel = factory.create(CategoryViewModel::class.java, CreationExtras.Empty)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockViewModel: CategoryViewModel = mockk(relaxed = true)
        ServiceLocator.setCategoryViewModel(mockViewModel)

        val resolved = factory.create(CategoryViewModel::class.java)
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
