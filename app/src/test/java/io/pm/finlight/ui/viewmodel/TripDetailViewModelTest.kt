// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/TripDetailViewModelTest.kt
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
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
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
class TripDetailViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var tripRepository: TripRepository

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    private lateinit var viewModel: TripDetailViewModel

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `tripDetails flow emits trip data from repository`() = runTest {
        // Arrange
        val tripId = 1
        val tagId = 10
        val mockTrip = TripWithStats(
            tripId = tripId, tripName = "Test Trip", startDate = 1L, endDate = 2L,
            totalSpend = 100.0, transactionCount = 2, tagId = tagId,
            tripType = io.pm.finlight.TripType.DOMESTIC, currencyCode = null, conversionRate = null
        )
        `when`(tripRepository.getTripWithStatsById(tripId)).thenReturn(flowOf(mockTrip))
        `when`(transactionRepository.getTransactionsByTagId(tagId)).thenReturn(flowOf(emptyList()))

        // Act
        viewModel = TripDetailViewModel(tripRepository, transactionRepository, tripId, tagId)

        // Assert
        viewModel.tripDetails.test {
            assertEquals(mockTrip, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(tripRepository).getTripWithStatsById(tripId)
    }

    @Test
    fun `transactions flow emits transaction list from repository`() = runTest {
        // Arrange
        val tripId = 1
        val tagId = 10
        val mockTransactions = listOf(
            TransactionDetails(
                Transaction(id = 1, description = "T1", amount = 50.0, date = 1L, accountId = 1, categoryId = 1, notes = null),
                emptyList(), "Acc1", "Cat1", "icon1", "color1", null
            )
        )
        `when`(tripRepository.getTripWithStatsById(tripId)).thenReturn(flowOf(null))
        `when`(transactionRepository.getTransactionsByTagId(tagId)).thenReturn(flowOf(mockTransactions))

        // Act
        viewModel = TripDetailViewModel(tripRepository, transactionRepository, tripId, tagId)

        // Assert
        viewModel.transactions.test {
            assertEquals(mockTransactions, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionRepository).getTransactionsByTagId(tagId)
    }
}