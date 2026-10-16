package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ISplitTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.SplitTransactionViewModel
import io.pm.finlight.SplitTransactionViewModelFactory
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
class SplitTransactionViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: SplitTransactionViewModelFactory
    private val testTransactionId = 42

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = SplitTransactionViewModelFactory(application, testTransactionId)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withSplitTransactionViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockSplitRepo: ISplitTransactionRepository = mockk(relaxed = true)

        every { mockTxnRepo.getTransactionById(testTransactionId) } returns flowOf(null)

        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setSplitTransactionRepository(mockSplitRepo)

        val viewModel = factory.create(SplitTransactionViewModel::class.java)

        assertNotNull(viewModel)

        val txnRepoField =
            SplitTransactionViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnRepoField)

        assertSame(mockCategoryRepo, viewModel.categoryRepository)

        val splitRepoField =
            SplitTransactionViewModel::class.java.getDeclaredField("splitTransactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSplitRepo, splitRepoField)

        val txnIdField =
            SplitTransactionViewModel::class.java.getDeclaredField("transactionId").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(testTransactionId, txnIdField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
