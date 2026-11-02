package io.pm.finlight.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertThrows

class TestUnknownViewModel : ViewModel()

fun assertThrowsUnknownViewModel(factory: ViewModelProvider.Factory) {
    assertThrows(IllegalArgumentException::class.java) {
        factory.create(TestUnknownViewModel::class.java)
    }
}

fun setupCommonMockRepositories() {
    val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
    val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
    val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
    val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
    val mockTagRepo: ITagRepository = mockk(relaxed = true)
    every { mockAccountRepo.allAccounts } returns flowOf(emptyList())
    every { mockAccountRepo.accountsWithBalance } returns flowOf(emptyList())
    every { mockCategoryRepo.allCategories } returns flowOf(emptyList())
    every { mockTagRepo.allTags } returns flowOf(emptyList())
    ServiceLocator.setTransactionRepository(mockTxnRepo)
    ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)
    ServiceLocator.setAccountRepository(mockAccountRepo)
    ServiceLocator.setCategoryRepository(mockCategoryRepo)
    ServiceLocator.setTagRepository(mockTagRepo)
}
