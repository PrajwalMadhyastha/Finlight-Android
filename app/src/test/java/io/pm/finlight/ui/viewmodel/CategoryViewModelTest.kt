package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CategoryViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var categoryDao: CategoryDao

    private lateinit var viewModel: CategoryViewModel

    @Before
    override fun setup() {
        super.setup()
        `when`(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        viewModel = CategoryViewModel(categoryRepository, transactionRepository, categoryDao)
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
        advanceUntilIdle()


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
        advanceUntilIdle()


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
        advanceUntilIdle()


        // ASSERT
        verify(categoryRepository, never()).delete(category)
        viewModel.uiEvent.test {
            assertEquals("Cannot delete '${category.name}'. It's used by $transactionCount transaction(s).", awaitItem())
        }
    }

    @Test
    fun `addCategory with existing name sends error event`() = runTest {
        // Arrange
        val categoryName = "Food"
        `when`(categoryDao.findByName(categoryName)).thenReturn(Category(1, categoryName, "", ""))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.addCategory(categoryName, "icon", "color")
            advanceUntilIdle()

            assertEquals("A category named '$categoryName' already exists.", awaitItem())
            verify(categoryRepository, never()).insert(anyObject())
        }
    }

    @Test
    fun `addCategory failure sends error event`() = runTest {
        // Arrange
        val categoryName = "New Category"
        val errorMessage = "DB Error"
        `when`(categoryDao.findByName(categoryName)).thenReturn(null)
        `when`(categoryRepository.insert(anyObject())).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.addCategory(categoryName, "icon", "color")
            advanceUntilIdle()

            assertEquals("Error creating category: $errorMessage", awaitItem())
        }
    }

    @Test
    fun `updateCategory success sends success event`() = runTest {
        // Arrange
        val categoryToUpdate = Category(1, "Updated", "icon", "color")

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.updateCategory(categoryToUpdate)
            advanceUntilIdle()

            verify(categoryRepository).update(categoryToUpdate)
            assertEquals("Category '${categoryToUpdate.name}' updated.", awaitItem())
        }
    }

    @Test
    fun `updateCategory failure sends error event`() = runTest {
        // Arrange
        val categoryToUpdate = Category(1, "Updated", "icon", "color")
        val errorMessage = "DB Error"
        `when`(categoryRepository.update(anyObject())).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.updateCategory(categoryToUpdate)
            advanceUntilIdle()

            assertEquals("Error updating category: $errorMessage", awaitItem())
        }
    }

    @Test
    fun `deleteCategory failure sends error event`() = runTest {
        // Arrange
        val category = Category(1, "Test", "icon", "color")
        val errorMessage = "DB Error"
        `when`(transactionRepository.countTransactionsForCategory(category.id)).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.deleteCategory(category)
            advanceUntilIdle()

            assertEquals("Error deleting category: $errorMessage", awaitItem())
        }
    }
}
