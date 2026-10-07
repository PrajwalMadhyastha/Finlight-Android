package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.ManageMerchantRulesViewModel
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
class ManageMerchantRulesViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: ManageMerchantRulesViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = ManageMerchantRulesViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withManageMerchantRulesViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockRenameRepo: IMerchantRenameRuleRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockCategoryMappingRepo: IMerchantCategoryMappingRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)

        every { mockRenameRepo.getAllRules() } returns flowOf(emptyList())
        every { mockCategoryMappingRepo.getAllMappings() } returns flowOf(emptyList())
        every { mockCategoryRepo.allCategories } returns flowOf(emptyList())
        every { mockTxnRepo.getTransactionCountsByOriginalDescription() } returns flowOf(emptyMap())

        ServiceLocator.setMerchantRenameRuleRepository(mockRenameRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setMerchantCategoryMappingRepository(mockCategoryMappingRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)

        val viewModel = factory.create(ManageMerchantRulesViewModel::class.java)

        assertNotNull(viewModel)

        val renameRepoField =
            ManageMerchantRulesViewModel::class.java.getDeclaredField("merchantRenameRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRenameRepo, renameRepoField)

        val txnRepoField =
            ManageMerchantRulesViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnRepoField)

        val categoryMappingRepoField =
            ManageMerchantRulesViewModel::class.java.getDeclaredField("categoryMappingRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCategoryMappingRepo, categoryMappingRepoField)

        val categoryRepoField =
            ManageMerchantRulesViewModel::class.java.getDeclaredField("categoryRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockCategoryRepo, categoryRepoField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
