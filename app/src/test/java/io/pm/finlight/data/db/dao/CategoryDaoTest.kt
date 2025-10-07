package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Category
import io.pm.finlight.CategoryDao
import io.pm.finlight.TestApplication
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CategoryDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() {
        categoryDao = dbRule.db.categoryDao()
    }

    @Test
    fun `findByName is case insensitive`() = runTest {
        // Arrange
        categoryDao.insert(Category(name = "Shopping", iconKey = "shop", colorKey = "blue"))

        // Act
        val foundCategory = categoryDao.findByName("shopping")

        // Assert
        assertNotNull(foundCategory)
        assertEquals("Shopping", foundCategory?.name)
    }

    @Test
    fun `getAllCategories returns categories ordered by name`() = runTest {
        // Arrange
        categoryDao.insertAll(listOf(
            Category(name = "Zoo", iconKey = "", colorKey = ""),
            Category(name = "Apple", iconKey = "", colorKey = ""),
            Category(name = "Microsoft", iconKey = "", colorKey = "")
        ))

        // Act & Assert
        categoryDao.getAllCategories().test {
            val categories = awaitItem()
            assertEquals(3, categories.size)
            assertEquals("Apple", categories[0].name)
            assertEquals("Microsoft", categories[1].name)
            assertEquals("Zoo", categories[2].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCategoryById returns correct category`() = runTest {
        // Arrange
        val id = categoryDao.insert(Category(name = "Test", iconKey = "icon", colorKey = "color")).toInt()
        // Act
        val fromDb = categoryDao.getCategoryById(id)
        // Assert
        assertNotNull(fromDb)
        assertEquals("Test", fromDb?.name)
    }

    @Test
    fun `update modifies category correctly`() = runTest {
        // Arrange
        val id = categoryDao.insert(Category(name = "Old Name", iconKey = "icon", colorKey = "color")).toInt()
        val updatedCategory = Category(id = id, name = "New Name", iconKey = "new_icon", colorKey = "new_color")
        // Act
        categoryDao.update(updatedCategory)
        // Assert
        val fromDb = categoryDao.getCategoryById(id)
        assertEquals("New Name", fromDb?.name)
        assertEquals("new_icon", fromDb?.iconKey)
    }

    @Test
    fun `updateAll modifies all categories`() = runTest {
        // Arrange
        val id1 = categoryDao.insert(Category(name = "Cat1", iconKey = "", colorKey = "")).toInt()
        val id2 = categoryDao.insert(Category(name = "Cat2", iconKey = "", colorKey = "")).toInt()
        val updatedList = listOf(
            Category(id = id1, name = "Updated Cat1", iconKey = "", colorKey = ""),
            Category(id = id2, name = "Updated Cat2", iconKey = "", colorKey = "")
        )
        // Act
        categoryDao.updateAll(updatedList)
        // Assert
        assertEquals("Updated Cat1", categoryDao.getCategoryById(id1)?.name)
        assertEquals("Updated Cat2", categoryDao.getCategoryById(id2)?.name)
    }

    @Test
    fun `delete removes category`() = runTest {
        // Arrange
        val id = categoryDao.insert(Category(name = "ToDelete", iconKey = "", colorKey = "")).toInt()
        val toDelete = Category(id = id, name = "ToDelete", iconKey = "", colorKey = "")
        // Act
        categoryDao.delete(toDelete)
        // Assert
        assertNull(categoryDao.getCategoryById(id))
    }

    @Test
    fun `deleteAll removes all categories`() = runTest {
        // Arrange
        categoryDao.insertAll(listOf(Category(name = "Cat1", iconKey = "", colorKey = ""), Category(name = "Cat2", iconKey = "", colorKey = "")))
        // Act
        categoryDao.deleteAll()
        // Assert
        categoryDao.getAllCategories().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}