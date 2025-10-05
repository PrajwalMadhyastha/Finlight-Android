// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/CurrencyViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// FIX (Testing) - Replaced all instances of Mockito's `any()` with the custom,
// null-safe `anyObject()` helper. This resolves the `NullPointerException` and
// `Misplaced or misused argument matcher` errors caused by Kotlin's non-nullable
// types. All previously ignored tests are now enabled and passing.
// FIX (Testing) - Replaced `anyObject()` with `anyInt()` for mocking methods
// that accept primitive `Int` types. This resolves a NullPointerException caused
// by the JVM attempting to unbox a null Integer.
// FIX (Testing) - Corrected the `updateHistoricTrip` test to properly set
// ViewModel state via public methods instead of attempting to stub a StateFlow's
// value directly. This resolves a `WrongTypeOfReturnValue` error from Mockito.
// =================================================================================
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

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
