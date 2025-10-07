package io.pm.finlight.data.db.dao

import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Tag
import io.pm.finlight.TagDao
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
class TagDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var tagDao: TagDao

    @Before
    fun setup() {
        tagDao = dbRule.db.tagDao()
    }

    @Test
    fun `inserting duplicate tag name with different case is ignored`() = runTest {
        // Arrange
        tagDao.insert(Tag(name = "Work"))

        // Act
        // This insert should be ignored because of the unique, case-insensitive index.
        try {
            tagDao.insert(Tag(name = "work"))
        } catch (e: SQLiteConstraintException) {
            // This is the expected outcome for a unique constraint violation.
            // The test passes if this exception is caught.
        }

        // Assert
        tagDao.getAllTags().test {
            val tags = awaitItem()
            // There should still be only one tag in the database.
            assertEquals(1, tags.size)
            assertEquals("Work", tags.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `findByName is case insensitive`() = runTest {
        // Arrange
        tagDao.insert(Tag(name = "Travel"))

        // Act
        val foundTag = tagDao.findByName("travel")

        // Assert
        assertNotNull(foundTag)
        assertEquals("Travel", foundTag?.name)
    }

    @Test
    fun `getAllTagsList returns all tags`() = runTest {
        // Arrange
        tagDao.insertAll(listOf(Tag(name = "Tag1"), Tag(name = "Tag2")))

        // Act
        val tags = tagDao.getAllTagsList()

        // Assert
        assertEquals(2, tags.size)
    }

    @Test
    fun `getTagById returns correct tag`() = runTest {
        // Arrange
        val id = tagDao.insert(Tag(name = "Test")).toInt()

        // Act
        val tag = tagDao.getTagById(id)

        // Assert
        assertNotNull(tag)
        assertEquals("Test", tag?.name)
    }

    @Test
    fun `update modifies a tag`() = runTest {
        // Arrange
        val id = tagDao.insert(Tag(name = "Old")).toInt()
        val updatedTag = Tag(id = id, name = "New")

        // Act
        tagDao.update(updatedTag)

        // Assert
        val fromDb = tagDao.getTagById(id)
        assertEquals("New", fromDb?.name)
    }

    @Test
    fun `delete removes a tag`() = runTest {
        // Arrange
        val id = tagDao.insert(Tag(name = "ToDelete")).toInt()
        val toDelete = Tag(id = id, name = "ToDelete")

        // Act
        tagDao.delete(toDelete)

        // Assert
        val fromDb = tagDao.getTagById(id)
        assertNull(fromDb)
    }

    @Test
    fun `deleteAll removes all tags`() = runTest {
        // Arrange
        tagDao.insertAll(listOf(Tag(name = "Tag1"), Tag(name = "Tag2")))

        // Act
        tagDao.deleteAll()

        // Assert
        val tags = tagDao.getAllTagsList()
        assertTrue(tags.isEmpty())
    }
}