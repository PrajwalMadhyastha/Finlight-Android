// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/CategoryViewModelTest.kt
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Category
import io.pm.finlight.CategoryDao
import io.pm.finlight.CategoryRepository
import io.pm.finlight.CategoryViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CategoryViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var categoryDao: CategoryDao

    @Captor
    private lateinit var categoryCaptor: ArgumentCaptor<Category>

    private lateinit var viewModel: CategoryViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        // ViewModel is instantiated in each test after mocks are configured for clarity.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `allCategories flow should emit categories from repository`() = runTest {
        // ARRANGE
        val categories = listOf(Category(1, "Food", "icon", "color"))
        `when`(categoryRepository.allCategories).thenReturn(flowOf(categories))
        viewModel = CategoryViewModel(categoryRepository, transactionRepository, categoryDao)

        // ACT & ASSERT
        viewModel.allCategories.test {
            assertEquals(categories, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateCategory calls repository update`() = runTest {
        // ARRANGE
        val categoryToUpdate = Category(1, "Updated Name", "icon", "color")
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        viewModel = CategoryViewModel(categoryRepository, transactionRepository, categoryDao)

        // ACT
        viewModel.updateCategory(categoryToUpdate)

        // ASSERT
        verify(categoryRepository).update(categoryToUpdate)
    }


    @Test
    fun `deleteCategory when not in use calls repository delete and sends success event`() = runTest {
        // ARRANGE
        val category = Category(1, "Deletable Category", "icon", "color")
        `when`(transactionRepository.countTransactionsForCategory(category.id)).thenReturn(0)
        `when`(categoryRepository.allCategories).thenReturn(flowOf(listOf(category)))
        viewModel = CategoryViewModel(categoryRepository, transactionRepository, categoryDao)

        // ACT
        viewModel.deleteCategory(category)

        // ASSERT
        verify(categoryRepository).delete(category)
        viewModel.uiEvent.test {
            assertEquals("Category '${category.name}' deleted.", awaitItem())
        }
    }

    @Test
    fun `deleteCategory when in use sends UI event and does not delete`() = runTest {
        // ARRANGE
        val category = Category(1, "In-Use Category", "icon", "color")
        val transactionCount = 3
        `when`(transactionRepository.countTransactionsForCategory(category.id)).thenReturn(transactionCount)
        `when`(categoryRepository.allCategories).thenReturn(flowOf(listOf(category)))
        viewModel = CategoryViewModel(categoryRepository, transactionRepository, categoryDao)

        // ACT
        viewModel.deleteCategory(category)

        // ASSERT
        verify(categoryRepository, never()).delete(category)
        viewModel.uiEvent.test {
            assertEquals("Cannot delete '${category.name}'. It's used by $transactionCount transaction(s).", awaitItem())
        }
    }
}
