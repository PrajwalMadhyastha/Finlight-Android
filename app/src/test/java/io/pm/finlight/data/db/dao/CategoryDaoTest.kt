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
}