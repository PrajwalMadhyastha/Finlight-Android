// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/SearchViewModelFactoryTest.kt
// REASON: NEW FILE (Domain 5 / Issue #339) - Comprehensive unit tests for SearchViewModelFactory
// verifying ServiceLocator fallback, constructor injection overrides, and unknown ViewModel handling.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SearchViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: SearchViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = SearchViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun `create with SearchViewModel class resolves dependencies from ServiceLocator`() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockTagRepo: ITagRepository = mockk(relaxed = true)

        every { mockTxnRepo.searchTransactions(any(), any()) } returns flowOf(emptyList())
        every { mockAccountRepo.allAccounts } returns flowOf(emptyList())
        every { mockCategoryRepo.allCategories } returns flowOf(emptyList())
        every { mockTagRepo.allTags } returns flowOf(emptyList())

        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)
        ServiceLocator.setCategoryRepository(mockCategoryRepo)
        ServiceLocator.setTagRepository(mockTagRepo)

        val vm = factory.create(SearchViewModel::class.java)
        assertNotNull(vm)
    }

    @Test
    fun `create with constructor overrides uses passed repositories`() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)
        val mockCategoryRepo: ICategoryRepository = mockk(relaxed = true)
        val mockTagRepo: ITagRepository = mockk(relaxed = true)
        val mockDispatcherProvider: DispatcherProvider = TestDispatcherProvider()

        every { mockAccountRepo.allAccounts } returns flowOf(emptyList())
        every { mockCategoryRepo.allCategories } returns flowOf(emptyList())
        every { mockTagRepo.allTags } returns flowOf(emptyList())

        val customFactory =
            SearchViewModelFactory(
                application = application,
                initialCategoryId = 5,
                initialDateMillis = 1000L,
                initialQuery = "TestQuery",
                transactionRepository = mockTxnRepo,
                accountRepository = mockAccountRepo,
                categoryRepository = mockCategoryRepo,
                tagRepository = mockTagRepo,
                dispatcherProvider = mockDispatcherProvider,
            )

        val vm = customFactory.create(SearchViewModel::class.java)
        assertNotNull(vm)
        assertEquals("TestQuery", vm.uiState.value.keyword)
        assertTrue(vm.uiState.value.isDrilldown)
    }

    @Test
    fun `create with unknown ViewModel class throws IllegalArgumentException`() {
        class UnknownViewModel : ViewModel()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                factory.create(UnknownViewModel::class.java)
            }
        assertTrue(exception.message?.contains("Unknown ViewModel class") == true)
    }
}
