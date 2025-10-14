package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TagRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var tagDao: TagDao

    @Mock
    private lateinit var transactionDao: TransactionDao

    private lateinit var repository: TagRepository

    @Before
    override fun setup() {
        super.setup()
        repository = TagRepository(tagDao, transactionDao)
    }

    @Test
    fun `allTags calls DAO`() {
        repository.allTags
        verify(tagDao).getAllTags()
    }

    @Test
    fun `insert calls DAO`() = runTest {
        val tag = Tag(name = "Test")
        repository.insert(tag)
        verify(tagDao).insert(tag)
    }

    @Test
    fun `update calls DAO`() = runTest {
        val tag = Tag(id = 1, name = "Test")
        repository.update(tag)
        verify(tagDao).update(tag)
    }

    @Test
    fun `delete calls DAO`() = runTest {
        val tag = Tag(id = 1, name = "Test")
        repository.delete(tag)
        verify(tagDao).delete(tag)
    }

    @Test
    fun `isTagInUse returns true when count is greater than zero`() = runTest {
        `when`(transactionDao.countTransactionsForTag(1)).thenReturn(5)
        val result = repository.isTagInUse(1)
        assertTrue(result)
    }

    @Test
    fun `isTagInUse returns false when count is zero`() = runTest {
        `when`(transactionDao.countTransactionsForTag(1)).thenReturn(0)
        val result = repository.isTagInUse(1)
        assertFalse(result)
    }

    @Test
    fun `findOrCreateTag returns existing tag if found`() = runTest {
        val existingTag = Tag(id = 1, name = "Work")
        `when`(tagDao.findByName("Work")).thenReturn(existingTag)
        val result = repository.findOrCreateTag("Work")
        assertEquals(existingTag, result)
        verify(tagDao).findByName("Work")
    }

    @Test
    fun `findOrCreateTag creates new tag if not found`() = runTest {
        `when`(tagDao.findByName("New Tag")).thenReturn(null)
        `when`(tagDao.insert(anyObject())).thenReturn(10L)
        val result = repository.findOrCreateTag("New Tag")
        assertEquals("New Tag", result.name)
        assertEquals(10, result.id)
        verify(tagDao).insert(Tag(name = "New Tag"))
    }

    @Test
    fun `findTagById calls DAO`() = runTest {
        repository.findTagById(1)
        verify(tagDao).getTagById(1)
    }

    @Test
    fun `findByName calls DAO`() = runTest {
        repository.findByName("Test")
        verify(tagDao).findByName("Test")
    }
}
