// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/TagViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.Tag
import io.pm.finlight.TagRepository
import io.pm.finlight.TagViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TagViewModelTest : BaseViewModelTest() {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Mock
    private lateinit var tagRepository: TagRepository

    @Mock
    private lateinit var tripRepository: TripRepository

    private lateinit var viewModel: TagViewModel

    @Before
    override fun setup() {
        super.setup()
    }

    private fun initializeViewModel(initialTags: List<Tag> = emptyList()) {
        `when`(tagRepository.allTags).thenReturn(flowOf(initialTags))
        viewModel = TagViewModel(application, tagRepository, tripRepository)
    }

    @Test
    fun `allTags flow emits tags from repository`() = runTest {
        // Arrange
        val tags = listOf(Tag(1, "Work"), Tag(2, "Vacation"))
        initializeViewModel(tags)

        // Assert
        viewModel.allTags.test {
            assertEquals(tags, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addTag with new name calls repository insert`() = runTest {
        // Arrange
        val newTagName = "New Tag"
        initializeViewModel()
        `when`(tagRepository.findByName(newTagName)).thenReturn(null)

        // Act
        viewModel.addTag(newTagName)

        // Assert
        verify(tagRepository).insert(Tag(name = newTagName))
        viewModel.uiEvent.test {
            assertEquals("Tag '$newTagName' created.", awaitItem())
        }
    }

    @Test
    fun `addTag with existing name sends error event and does not insert`() = runTest {
        // Arrange
        val existingTagName = "Existing Tag"
        val existingTag = Tag(1, existingTagName)
        initializeViewModel(listOf(existingTag))
        `when`(tagRepository.findByName(existingTagName)).thenReturn(existingTag)

        // Act
        viewModel.addTag(existingTagName)

        // Assert
        verify(tagRepository, never()).insert(Tag(name = existingTagName))
        viewModel.uiEvent.test {
            assertEquals("A tag named '$existingTagName' already exists.", awaitItem())
        }
    }

    @Test
    fun `updateTag calls repository update`() = runTest {
        // Arrange
        initializeViewModel()
        val tagToUpdate = Tag(1, "Updated Name")

        // Act
        viewModel.updateTag(tagToUpdate)

        // Assert
        verify(tagRepository).update(tagToUpdate)
    }

    @Test
    fun `deleteTag when not in use calls repository delete`() = runTest {
        // Arrange
        val tagToDelete = Tag(1, "Unused Tag")
        initializeViewModel()
        `when`(tagRepository.isTagInUse(tagToDelete.id)).thenReturn(false)
        `when`(tripRepository.isTagUsedByTrip(tagToDelete.id)).thenReturn(false)

        // Act
        viewModel.deleteTag(tagToDelete)

        // Assert
        verify(tagRepository).delete(tagToDelete)
        viewModel.uiEvent.test {
            assertEquals("Tag '${tagToDelete.name}' deleted.", awaitItem())
        }
    }

    @Test
    fun `deleteTag when used by transaction sends error event`() = runTest {
        // Arrange
        val tagInUse = Tag(1, "Transaction Tag")
        initializeViewModel()
        `when`(tagRepository.isTagInUse(tagInUse.id)).thenReturn(true)
        `when`(tripRepository.isTagUsedByTrip(tagInUse.id)).thenReturn(false)

        // Act
        viewModel.deleteTag(tagInUse)

        // Assert
        verify(tagRepository, never()).delete(tagInUse)
        viewModel.uiEvent.test {
            assertEquals("Cannot delete '${tagInUse.name}'. It is attached to one or more transactions.", awaitItem())
        }
    }

    @Test
    fun `deleteTag when used by trip sends error event`() = runTest {
        // Arrange
        val tagInUse = Tag(1, "Trip Tag")
        initializeViewModel()
        `when`(tagRepository.isTagInUse(tagInUse.id)).thenReturn(false)
        `when`(tripRepository.isTagUsedByTrip(tagInUse.id)).thenReturn(true)

        // Act
        viewModel.deleteTag(tagInUse)

        // Assert
        verify(tagRepository, never()).delete(tagInUse)
        viewModel.uiEvent.test {
            assertEquals("Cannot delete '${tagInUse.name}'. It is linked to a trip in your travel history.", awaitItem())
        }
    }
}