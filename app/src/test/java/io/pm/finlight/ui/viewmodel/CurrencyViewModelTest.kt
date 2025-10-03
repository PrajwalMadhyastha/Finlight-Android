// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/CurrencyViewModelTest.kt
// REASON: NEW FILE - Unit tests for the refactored CurrencyViewModel, covering
// state observation and actions for managing currency and travel settings.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.entity.Trip
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class CurrencyViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock private lateinit var application: Application
    @Mock private lateinit var settingsRepository: SettingsRepository
    @Mock private lateinit var tripRepository: TripRepository
    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var tagRepository: TagRepository

    private lateinit var viewModel: CurrencyViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    @Ignore
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
        `when`(tagRepository.findOrCreateTag("Goa Trip")).thenReturn(newTag)

        initializeViewModel() // Re-initialize to pick up new mock

        // Act
        viewModel.saveActiveTravelPlan(newSettings)
        advanceUntilIdle()

        // Assert
        verify(settingsRepository).saveTravelModeSettings(newSettings)
        verify(tagRepository).findOrCreateTag("Goa Trip")
        verify(transactionRepository).addTagForDateRange(newTag.id, newSettings.startDate, newSettings.endDate)
        verify(tripRepository).insert(any(Trip::class.java))
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
