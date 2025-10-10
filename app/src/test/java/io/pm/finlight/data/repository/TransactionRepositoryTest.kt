package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository
    @Mock
    private lateinit var tagRepository: TagRepository

    private lateinit var repository: TransactionRepository

    @Before
    override fun setup() {
        super.setup()
        // Initialize with default mock behaviors
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
    }

    @Test
    fun `insertTransactionWithTags without travel mode saves transaction and initial tags`() = runTest {
        // Arrange
        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(transactionDao).insert(transaction)
        verify(transactionDao).addTagsToTransaction(capture(crossRefCaptor))
        val capturedRefs = crossRefCaptor.value
        assertEquals(1, capturedRefs.size)
        assertEquals(1, capturedRefs.first().tagId)
        assertEquals(1, capturedRefs.first().transactionId)
    }

    @Test
    fun `insertTransactionWithTags with active travel mode adds trip tag`() = runTest {
        // Arrange
        val tripTag = Tag(id = 99, name = "US Trip")
        val travelSettings = TravelModeSettings(isEnabled = true, tripName = "US Trip", tripType = TripType.DOMESTIC, startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = null, conversionRate = null)
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
        `when`(tagRepository.findOrCreateTag("US Trip")).thenReturn(tripTag)
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>


        // Re-initialize repository to pick up the mocked settings
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(tagRepository).findOrCreateTag("US Trip")
        verify(transactionDao).addTagsToTransaction(capture(crossRefCaptor))
        val capturedRefs = crossRefCaptor.value
        assertEquals(2, capturedRefs.size) // Work tag + Trip tag
        assertTrue(capturedRefs.any { it.tagId == 1 })
        assertTrue(capturedRefs.any { it.tagId == 99 })
    }

    @Test
    fun `insertTransactionWithTags with inactive travel mode does not add trip tag`() = runTest {
        // Arrange
        val travelSettings = TravelModeSettings(isEnabled = false, tripName = "US Trip", tripType = TripType.DOMESTIC, startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = null, conversionRate = null)
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>


        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(tagRepository, never()).findOrCreateTag(anyString())
        verify(transactionDao).addTagsToTransaction(capture(crossRefCaptor))
        val capturedRefs = crossRefCaptor.value
        assertEquals(1, capturedRefs.size) // Only Work tag
        assertEquals(1, capturedRefs.first().tagId)
    }

    @Test
    fun `getTotalExpensesSince calls DAO`() = runTest {
        val startDate = 1000L
        repository.getTotalExpensesSince(startDate)
        verify(transactionDao).getTotalExpensesSince(startDate)
    }

    @Test
    fun `searchMerchants calls DAO`() {
        val query = "amzn"
        repository.searchMerchants(query)
        verify(transactionDao).searchMerchants(query)
    }

    @Test
    fun `deleteByIds calls DAO`() = runTest {
        val ids = listOf(1, 2)
        repository.deleteByIds(ids)
        verify(transactionDao).deleteByIds(ids)
    }

    @Test
    fun `addTagForDateRange calls DAO`() = runTest {
        repository.addTagForDateRange(1, 100L, 200L)
        verify(transactionDao).addTagForDateRange(1, 100L, 200L)
    }

    @Test
    fun `removeTagForDateRange calls DAO`() = runTest {
        repository.removeTagForDateRange(1, 100L, 200L)
        verify(transactionDao).removeTagForDateRange(1, 100L, 200L)
    }

    @Test
    fun `getTransactionsByTagId calls DAO`() {
        repository.getTransactionsByTagId(1)
        verify(transactionDao).getTransactionsByTagId(1)
    }

    @Test
    fun `removeAllTransactionsForTag calls DAO`() = runTest {
        repository.removeAllTransactionsForTag(1)
        verify(transactionDao).removeAllTransactionsForTag(1)
    }
}

