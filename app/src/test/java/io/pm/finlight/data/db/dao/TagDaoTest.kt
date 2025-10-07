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
}
