package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CategoryRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var categoryDao: CategoryDao

    // The repository is now initialized within each test to ensure mocks are configured first.

    @Test
    fun `allCategories flow proxies call to DAO`() = runTest {
        // Arrange
        val mockCategories = listOf(Category(id = 1, name = "Food", iconKey = "restaurant", colorKey = "red"))
        `when`(categoryDao.getAllCategories()).thenReturn(flowOf(mockCategories))
        val repository = CategoryRepository(categoryDao)

        // Act & Assert
        repository.allCategories.test {
            assertEquals(mockCategories, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(categoryDao).getAllCategories()
    }

    @Test
    fun `getCategoryById calls DAO`() = runTest {
        // Arrange
        val repository = CategoryRepository(categoryDao)
        val categoryId = 1
        val mockCategory = Category(id = categoryId, name = "Food", iconKey = "restaurant", colorKey = "red")
        `when`(categoryDao.getCategoryById(categoryId)).thenReturn(mockCategory)

        // Act
        val result = repository.getCategoryById(categoryId)

        // Assert
        assertEquals(mockCategory, result)
        verify(categoryDao).getCategoryById(categoryId)
    }

    @Test
    fun `insert calls DAO`() = runTest {
        // Arrange
        val repository = CategoryRepository(categoryDao)
        val newCategory = Category(name = "Travel", iconKey = "flight", colorKey = "blue")

        // Act
        repository.insert(newCategory)

        // Assert
        verify(categoryDao).insert(newCategory)
    }

    @Test
    fun `update calls DAO`() = runTest {
        // Arrange
        val repository = CategoryRepository(categoryDao)
        val categoryToUpdate = Category(id = 1, name = "Groceries", iconKey = "cart", colorKey = "green")

        // Act
        repository.update(categoryToUpdate)

        // Assert
        verify(categoryDao).update(categoryToUpdate)
    }

    @Test
    fun `delete calls DAO`() = runTest {
        // Arrange
        val repository = CategoryRepository(categoryDao)
        val categoryToDelete = Category(id = 1, name = "Entertainment", iconKey = "movie", colorKey = "purple")

        // Act
        repository.delete(categoryToDelete)

        // Assert
        verify(categoryDao).delete(categoryToDelete)
    }
}

