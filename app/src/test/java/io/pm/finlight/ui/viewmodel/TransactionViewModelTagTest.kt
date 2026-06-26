package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.ui.components.ShareableField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import java.lang.RuntimeException
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelTagTest : TransactionViewModelBaseSetup() {

    // --- NEW: Tag Logic Tests ---

    @Test
        fun `updateTagsForTransaction calls repository successfully`() =
            runTest {
                // Arrange
                val transactionId = 1
                val tags = setOf(Tag(1, "test"))
                // Set the internal state that the VM function will read from
                tags.forEach { viewModel.onTagSelected(it) }
                advanceUntilIdle()

                // Act
                viewModel.updateTagsForTransaction(transactionId) // Call with only the ID
                advanceUntilIdle()

                // Assert
                verify(transactionRepository).updateTagsForTransaction(transactionId, tags)
                // Check no error event
                viewModel.uiEvent.test {
                    expectNoEvents()
                }
            }

    @Test
        fun `updateTagsForTransaction failure sends uiEvent`() =
            runTest {
                // Arrange
                val transactionId = 1
                val tags = setOf(Tag(1, "test"))
                val errorMessage = "Failed to update tags. Please try again."

                // Set the internal state
                tags.forEach { viewModel.onTagSelected(it) }
                advanceUntilIdle()

                // Mock the repository call to throw an exception
                whenever(transactionRepository.updateTagsForTransaction(eq(transactionId), eq(tags)))
                    .thenThrow(RuntimeException("DB update failed"))

                // Act & Assert
                viewModel.uiEvent.test {
                    viewModel.updateTagsForTransaction(transactionId) // Call with only the ID
                    advanceUntilIdle() // Re-add advanceUntilIdle to fix timeout
                    assertEquals(errorMessage, awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `onTagSelected adds and removes tags from selectedTags flow`() =
            runTest {
                // Arrange
                val tag1 = Tag(1, "Groceries")
                val tag2 = Tag(2, "Fun")

                viewModel.selectedTags.test {
                    // Initial state
                    assertTrue("Initial tags should be empty", awaitItem().isEmpty())

                    // Act 1: Add tag1
                    viewModel.onTagSelected(tag1)
                    assertEquals("Should contain tag1", setOf(tag1), awaitItem())

                    // Act 2: Add tag2
                    viewModel.onTagSelected(tag2)
                    assertEquals("Should contain tag1 and tag2", setOf(tag1, tag2), awaitItem())

                    // Act 3: Remove tag1
                    viewModel.onTagSelected(tag1)
                    assertEquals("Should only contain tag2", setOf(tag2), awaitItem())

                    // Act 4: Remove tag2
                    viewModel.onTagSelected(tag2)
                    assertTrue("Should be empty again", awaitItem().isEmpty())

                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `addTagOnTheGo inserts new tag and selects it`() =
            runTest {
                // Arrange
                val newTagName = "NewTag"
                val newTagRowId = 10L // This is the Long ID returned from the repository
                // This mocks the repository's insert function, which should take a Tag with no ID
                // and return the new row ID (Long).
                whenever(tagRepository.insert(Tag(name = newTagName))).thenReturn(newTagRowId)

                viewModel.selectedTags.test {
                    // Initial state
                    assertTrue("Initial tags should be empty", awaitItem().isEmpty())

                    // Act
                    viewModel.addTagOnTheGo(newTagName)
                    advanceUntilIdle() // Let the coroutine launch

                    // Assert
                    // 1. Verify repository insert was called correctly
                    verify(tagRepository).insert(Tag(name = newTagName))

                    // 2. Verify the selectedTags flow was updated with the new tag,
                    // which should now have the ID cast to an Int for the Tag data class.
                    val expectedTag = Tag(id = newTagRowId.toInt(), name = newTagName)
                    assertEquals("Should contain the new tag", setOf(expectedTag), awaitItem())

                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `addTagOnTheGo failure sends uiEvent`() =
            runTest {
                // Arrange
                val newTagName = "BadTag"
                val errorMessage = "Failed to add new tag. Please try again."
                whenever(tagRepository.insert(Tag(name = newTagName)))
                    .thenThrow(RuntimeException("DB insert failed"))

                // Act & Assert
                viewModel.uiEvent.test {
                    viewModel.addTagOnTheGo(newTagName)
                    advanceUntilIdle() // Re-add advanceUntilIdle to fix timeout

                    // Assert
                    assertEquals(errorMessage, awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }

                // Also check that selectedTags was not modified
                viewModel.selectedTags.test {
                    assertTrue("Tags should remain empty on failure", awaitItem().isEmpty())
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `addTagOnTheGo fails for existing tag name`() =
            runTest {
                // Arrange
                whenever(db.tagDao().findByName("Work")).thenReturn(Tag(1, "Work"))

                // Act
                viewModel.addTagOnTheGo("Work")
                advanceUntilIdle()

                // Assert
                viewModel.validationError.test {
                    assertEquals("A tag named 'Work' already exists.", awaitItem())
                }
            }

}
