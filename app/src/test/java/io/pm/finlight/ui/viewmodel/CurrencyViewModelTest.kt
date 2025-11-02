package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.db.entity.Trip
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CurrencyViewModelTest : BaseViewModelTest() {

    @Mock private lateinit var application: Application
    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var tripRepository: TripRepository
    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var tagRepository: TagRepository

    private lateinit var viewModel: CurrencyViewModel

    @Before
    override fun setup() {
        super.setup()

        // Setup default mocks for initialization
        `when`(settingsRepository.getHomeCurrency()).thenReturn(flowOf("INR"))
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
        `when`(tripRepository.getAllTripsWithStats()).thenReturn(flowOf(emptyList()))

        viewModel = CurrencyViewModel(
            ApplicationProvider.getApplicationContext(),
            settingsRepository,
            tripRepository,
            transactionRepository,
            tagRepository
        )
    }

    @Test
    fun `homeCurrency flow emits value from repository`() = runTest {
        // Arrange
        `when`(settingsRepository.getHomeCurrency()).thenReturn(flowOf("USD"))
        initializeViewModel()

        // Assert
        viewModel.homeCurrency.test {
            assertEquals("USD", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveHomeCurrency calls repository`() = runTest {
        // Act
        viewModel.saveHomeCurrency("EUR")
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveHomeCurrency("EUR")
    }

    @Test
    fun `completeTrip calls repository to save null settings`() = runTest {
        // Act
        viewModel.completeTrip()
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveTravelModeSettings(null)
    }

    @Test
    fun `deleteTrip calls transaction and trip repositories`() = runTest {
        // Arrange
        val tripId = 1
        val tagId = 2

        // Act
        viewModel.deleteTrip(tripId, tagId)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).removeAllTransactionsForTag(tagId)
        verify(tripRepository).deleteTripById(tripId)
    }

    @Test
    fun `saveActiveTravelPlan for new trip correctly interacts with repositories`() = runTest {
        // Arrange
        val newSettings = TravelModeSettings(
            isEnabled = true,
            tripName = "Goa Trip",
            tripType = TripType.DOMESTIC,
            startDate = 1000L,
            endDate = 2000L,
            currencyCode = null,
            conversionRate = null
        )
        val newTag = Tag(id = 1, name = "Goa Trip")

        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null)) // No old settings
        `when`(tagRepository.findOrCreateTag(anyObject())).thenReturn(newTag)

        initializeViewModel() // Re-initialize to pick up new mock

        // Act
        viewModel.saveActiveTravelPlan(newSettings)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveTravelModeSettings(newSettings)
        verify(tagRepository).findOrCreateTag("Goa Trip")
        verify(transactionRepository).addTagForDateRange(newTag.id, newSettings.startDate, newSettings.endDate)
        verify(tripRepository).insert(anyObject())
    }

    @Test
    fun `updateHistoricTrip correctly interacts with repositories`() = runTest {
        // Arrange
        val tripId = 1
        val updatedSettings = TravelModeSettings(
            isEnabled = true,
            tripName = "New York Trip",
            tripType = TripType.INTERNATIONAL,
            startDate = 3000L,
            endDate = 4000L,
            currencyCode = "USD",
            conversionRate = 83.0f
        )
        val originalTag = Tag(id = 1, name = "Old Trip Name")
        val newTag = Tag(id = 2, name = "New York Trip")
        val tripToEdit = TripWithStats(tripId, "Old Trip Name", 1000L, 2000L, 100.0, 1, 1, TripType.DOMESTIC, null, null)

        `when`(tripRepository.getTripWithStatsById(tripId)).thenReturn(flowOf(tripToEdit))
        `when`(tagRepository.findTagById(anyInt())).thenReturn(originalTag)
        `when`(tagRepository.findOrCreateTag(anyObject())).thenReturn(newTag)
        `when`(tripRepository.isTagUsedByTrip(anyInt())).thenReturn(false)
        `when`(transactionRepository.getTransactionsByTagId(anyInt())).thenReturn(flowOf(emptyList()))

        initializeViewModel()

        // Act
        // 1. Load the data into the ViewModel's state.
        viewModel.loadTripForEditing(tripId)
        advanceUntilIdle() // Ensure the state is updated from the flow.

        // 2. Call the function under test.
        viewModel.updateHistoricTrip(updatedSettings)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).removeTagForDateRange(originalTag.id, tripToEdit.startDate, tripToEdit.endDate)
        verify(transactionRepository).addTagForDateRange(newTag.id, updatedSettings.startDate, updatedSettings.endDate)
        verify(tripRepository).insert(anyObject())
    }

    @Test
    fun `cancelTrip correctly interacts with repositories`() = runTest {
        // Arrange
        val settingsToCancel = TravelModeSettings(
            isEnabled = true,
            tripName = "Cancelled Trip",
            tripType = TripType.DOMESTIC,
            startDate = 1000L,
            endDate = 2000L,
            currencyCode = null,
            conversionRate = null
        )
        val tripTag = Tag(id = 3, name = "Cancelled Trip")
        val tripRecord = Trip(id = 5, name = "Cancelled Trip", startDate = 1000L, endDate = 2000L, tagId = 3, tripType = TripType.DOMESTIC, null, null)

        `when`(tagRepository.findOrCreateTag(anyObject())).thenReturn(tripTag)
        `when`(tripRepository.getTripByTagId(anyInt())).thenReturn(tripRecord)

        initializeViewModel()

        // Act
        viewModel.cancelTrip(settingsToCancel)
        advanceUntilIdle()

        // Assert
        verify(transactionRepository).removeTagForDateRange(tripTag.id, settingsToCancel.startDate, settingsToCancel.endDate)
        verify(tripRepository).deleteTripById(tripRecord.id)
        verify(settingsRepository).saveTravelModeSettings(null)
    }

    @Test
    fun `loadTripForEditing updates tripToEdit state`() = runTest {
        // Arrange
        val tripId = 1
        val mockTrip = TripWithStats(tripId, "Test Trip", 1L, 2L, 100.0, 2, 1, TripType.DOMESTIC, null, null)
        `when`(tripRepository.getTripWithStatsById(tripId)).thenReturn(flowOf(mockTrip))
        initializeViewModel()

        // Act
        viewModel.loadTripForEditing(tripId)

        // Assert
        viewModel.tripToEdit.test {
            assertEquals(mockTrip, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(tripRepository).getTripWithStatsById(tripId)
    }

    @Test
    fun `clearTripToEdit resets tripToEdit state`() = runTest {
        // Arrange
        val tripId = 1
        val mockTrip = TripWithStats(tripId, "Test Trip", 1L, 2L, 100.0, 2, 1, TripType.DOMESTIC, null, null)
        `when`(tripRepository.getTripWithStatsById(tripId)).thenReturn(flowOf(mockTrip))
        initializeViewModel()
        viewModel.loadTripForEditing(tripId) // Pre-load a trip

        // Assert initial state
        viewModel.tripToEdit.test {
            assertEquals(mockTrip, awaitItem())

            // Act
            viewModel.clearTripToEdit()

            // Assert final state
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveActiveTravelPlan when updating existing plan correctly removes old tags`() = runTest {
        // Arrange
        val oldSettings = TravelModeSettings(isEnabled = true, tripName = "Old Trip", tripType = TripType.DOMESTIC, startDate = 100L, endDate = 200L, currencyCode = null, conversionRate = null)
        val newSettings = TravelModeSettings(isEnabled = true, tripName = "New Trip", tripType = TripType.DOMESTIC, startDate = 1000L, endDate = 2000L, currencyCode = null, conversionRate = null)
        val oldTag = Tag(id = 1, name = "Old Trip")
        val newTag = Tag(id = 2, name = "New Trip")

        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(oldSettings)) // Start with an existing plan
        `when`(tagRepository.findOrCreateTag("Old Trip")).thenReturn(oldTag)
        `when`(tagRepository.findOrCreateTag("New Trip")).thenReturn(newTag)
        // Assume old tag is not used elsewhere so it can be deleted
        `when`(tripRepository.isTagUsedByTrip(oldTag.id)).thenReturn(false)
        `when`(transactionRepository.getTransactionsByTagId(oldTag.id)).thenReturn(flowOf(emptyList()))

        initializeViewModel()

        // --- FIX: Eagerly collect the flow to trigger emission from `stateIn` ---
        val collectionJob = launch { viewModel.travelModeSettings.collect() }
        advanceUntilIdle()

        // Act
        viewModel.saveActiveTravelPlan(newSettings)
        advanceUntilIdle()

        // Assert
        // Verify old tag cleanup
        verify(transactionRepository).removeTagForDateRange(oldTag.id, oldSettings.startDate, oldSettings.endDate)
        verify(tagRepository).delete(oldTag)
        // Verify new tag setup
        verify(transactionRepository).addTagForDateRange(newTag.id, newSettings.startDate, newSettings.endDate)
        verify(tripRepository).insert(anyObject())
        verify(settingsRepository).saveTravelModeSettings(newSettings)

        collectionJob.cancel() // Clean up the collector
    }

    @Test
    fun `historicTrips flow emits all trips when no travel mode is active`() = runTest {
        // Arrange
        val historicTrip1 = TripWithStats(1, "Historic Trip 1", 100L, 200L, 100.0, 1, 1, TripType.DOMESTIC, null, null)
        val historicTrip2 = TripWithStats(2, "Historic Trip 2", 300L, 400L, 200.0, 2, 2, TripType.DOMESTIC, null, null)
        val allTrips = listOf(historicTrip1, historicTrip2)

        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null)) // No active trip
        `when`(tripRepository.getAllTripsWithStats()).thenReturn(flowOf(allTrips))
        initializeViewModel()

        // Assert
        viewModel.historicTrips.test {
            val filteredList = awaitItem()
            assertEquals("When no trip is active, all trips should be returned", 2, filteredList.size)
            assertEquals(allTrips, filteredList)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `historicTrips flow filters out the active trip`() = runTest {
        // Arrange
        val activeSettings = TravelModeSettings(true, "Active Trip", TripType.DOMESTIC, 1000L, 2000L, null, null)
        val activeTrip = TripWithStats(1, "Active Trip", 1000L, 2000L, 100.0, 1, 1, TripType.DOMESTIC, null, null)
        val historicTrip = TripWithStats(2, "Historic Trip", 100L, 200L, 200.0, 2, 2, TripType.DOMESTIC, null, null)
        val allTrips = listOf(activeTrip, historicTrip)

        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(activeSettings))
        `when`(tripRepository.getAllTripsWithStats()).thenReturn(flowOf(allTrips))
        initializeViewModel()

        // Assert
        viewModel.historicTrips.test {
            val filteredList = awaitItem()
            assertEquals("Active trip should be filtered out", 1, filteredList.size)
            assertEquals("Historic Trip", filteredList.first().tripName)
            cancelAndIgnoreRemainingEvents()
        }
    }


    private fun initializeViewModel() {
        viewModel = CurrencyViewModel(
            ApplicationProvider.getApplicationContext(),
            settingsRepository,
            tripRepository,
            transactionRepository,
            tagRepository
        )
    }
}
