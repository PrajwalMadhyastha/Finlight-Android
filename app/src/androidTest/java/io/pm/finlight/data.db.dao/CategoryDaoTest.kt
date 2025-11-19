package io.pm.finlight.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Category
import io.pm.finlight.CategoryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest : BaseDaoTest() {

    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() {
        categoryDao = db.categoryDao()
    }

    @Test
    fun insertAndReadCategory() = runBlocking {
        val category = Category(name = "Groceries", iconKey = "cart", colorKey = "green")
        val id = categoryDao.insert(category)

        val loaded = categoryDao.getCategoryById(id.toInt())
        assertNotNull(loaded)
        assertEquals("Groceries", loaded?.name)
    }

    @Test
    fun testCaseInsensitiveUniqueConstraint() = runBlocking {
        // 1. Insert "Food"
        val id1 = categoryDao.insert(Category(name = "Food", iconKey = "icon", colorKey = "red"))
        assertTrue(id1 > 0)

        // 2. Attempt to insert "FOOD"
        // Conflict strategy IGNORE -> returns -1
        val id2 = categoryDao.insert(Category(name = "FOOD", iconKey = "icon2", colorKey = "blue"))

        assertEquals(-1L, id2)

        // Verify only one exists
        val categories = categoryDao.getAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("Food", categories[0].name)
    }

    @Test
    fun findByName_isCaseInsensitive() = runBlocking {
        categoryDao.insert(Category(name = "Transport", iconKey = "car", colorKey = "blue"))

        val found = categoryDao.findByName("transport") // lowercase lookup
        assertNotNull(found)
        assertEquals("Transport", found?.name)
    }
}