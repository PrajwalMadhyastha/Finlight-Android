// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/HistoricTripsViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionRepository
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.repository.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class HistoricTripsViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var tripRepository: TripRepository

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    private lateinit var viewModel: HistoricTripsViewModel

    @Before
    override fun setup() {
        super.setup()
    }

    private fun initializeViewModel(initialTrips: List<TripWithStats> = emptyList()) {
        `when`(tripRepository.getAllTripsWithStats()).thenReturn(flowOf(initialTrips))
        viewModel = HistoricTripsViewModel(tripRepository, transactionRepository)
    }

    @Test
    fun `historicTrips flow emits trips from repository`() = runTest {
        // Arrange
        val mockTrips = listOf(
            TripWithStats(
                tripId = 1, tripName = "Goa Trip", startDate = 1000L, endDate = 2000L,
                totalSpend = 500.0, transactionCount = 5, tagId = 1,
                tripType = io.pm.finlight.TripType.DOMESTIC, currencyCode = null, conversionRate = null
            )
        )
        initializeViewModel(mockTrips)

        // Assert
        viewModel.historicTrips.test {
            assertEquals(mockTrips, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteTrip calls repositories to remove tag associations and delete trip`() = runTest {
        // Arrange
        initializeViewModel()
        val tripId = 1
        val tagId = 10

        // Act
        viewModel.deleteTrip(tripId, tagId)

        // Assert
        verify(transactionRepository).removeAllTransactionsForTag(tagId)
        verify(tripRepository).deleteTripById(tripId)
    }
}