// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/DrilldownViewModelTest.kt
// REASON: NEW FILE - Unit tests for DrilldownViewModel. This covers the
// logic for fetching transactions and monthly trends based on the provided
// drilldown dimension (Category or Merchant).
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.MonthlyTrend
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DrilldownViewModelTest {
    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDispatcherProvider = io.pm.finlight.utils.TestDispatcherProvider(testDispatcher)

    @Mock
    private lateinit var transactionRepository: ITransactionRepository

    private lateinit var viewModel: DrilldownViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val mockTransactionDetails =
        listOf(
            TransactionDetails(
                Transaction(id = 1, description = "Test", categoryId = 1, amount = 100.0, date = 0, accountId = 1, notes = null),
                emptyList(),
                "Account",
                "Category",
                "icon",
                "color",
                null,
            ),
        )

    @Test
    fun `when dimension is CATEGORY, it calls correct repository methods`() =
        runTest {
            // Arrange
            val categoryName = "Food"
            val mockMonthlyTrends =
                listOf(
                    MonthlyTrend("2025-01", 50.0, 100.0),
                    MonthlyTrend("2025-02", 75.0, 150.0),
                )
            `when`(
                transactionRepository.getTransactionsForCategoryName(anyString(), anyLong(), anyLong()),
            ).thenReturn(flowOf(mockTransactionDetails))
            `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(mockMonthlyTrends))

            // Act
            viewModel = DrilldownViewModel(transactionRepository, DrilldownType.CATEGORY, categoryName, 1, 2025, testDispatcherProvider)

            // Assert
            viewModel.transactionsForMonth.test {
                assertEquals(mockTransactionDetails, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.monthlyTrendChartData.test {
                // Test that the chart data is processed correctly
                val item = awaitItem()
                val chartData = if (item == null) awaitItem() else item
                assertEquals(6, chartData?.second?.size) // 6 months of labels
                assertEquals(12, chartData?.first?.entryCount) // 6 months x 2 datasets (income + expense)
                cancelAndIgnoreRemainingEvents()
            }

            val startCaptor = ArgumentCaptor.forClass(Long::class.java)
            val endCaptor = ArgumentCaptor.forClass(Long::class.java)
            verify(transactionRepository).getTransactionsForCategoryName(anyString(), startCaptor.capture(), endCaptor.capture())
            verify(transactionRepository).getMonthlyTrends(anyLong())

            val startCal = Calendar.getInstance().apply { timeInMillis = startCaptor.value }
            assertEquals(2025, startCal.get(Calendar.YEAR))
            assertEquals(Calendar.JANUARY, startCal.get(Calendar.MONTH))
            assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, startCal.get(Calendar.MINUTE))
            assertEquals(0, startCal.get(Calendar.SECOND))
            assertEquals(0, startCal.get(Calendar.MILLISECOND))

            val endCal = Calendar.getInstance().apply { timeInMillis = endCaptor.value }
            assertEquals(2025, endCal.get(Calendar.YEAR))
            assertEquals(Calendar.JANUARY, endCal.get(Calendar.MONTH))
            assertEquals(31, endCal.get(Calendar.DAY_OF_MONTH))
            assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, endCal.get(Calendar.MINUTE))
            assertEquals(59, endCal.get(Calendar.SECOND))
            assertEquals(999, endCal.get(Calendar.MILLISECOND))
        }

    @Test
    fun `when dimension is MERCHANT, it calls correct repository methods`() =
        runTest {
            // Arrange
            val merchantName = "Amazon"
            val mockMonthlyTrends =
                listOf(
                    MonthlyTrend("2025-01", 50.0, 100.0),
                    MonthlyTrend("2025-02", 75.0, 150.0),
                )
            `when`(
                transactionRepository.getTransactionsForMerchantName(anyString(), anyLong(), anyLong()),
            ).thenReturn(flowOf(mockTransactionDetails))
            `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(mockMonthlyTrends))

            // Act
            viewModel = DrilldownViewModel(transactionRepository, DrilldownType.MERCHANT, merchantName, 1, 2025, testDispatcherProvider)

            // Assert
            viewModel.transactionsForMonth.test {
                assertEquals(mockTransactionDetails, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.monthlyTrendChartData.test {
                val item = awaitItem()
                val chartData = if (item == null) awaitItem() else item
                assertEquals(6, chartData?.second?.size)
                assertEquals(12, chartData?.first?.entryCount) // 6 months x 2 datasets
                cancelAndIgnoreRemainingEvents()
            }

            verify(transactionRepository).getTransactionsForMerchantName(anyString(), anyLong(), anyLong())
            verify(transactionRepository).getMonthlyTrends(anyLong())
        }

    @Test
    fun `when monthly trends are empty, monthlyTrendChartData emits null`() =
        runTest {
            // Arrange
            `when`(
                transactionRepository.getTransactionsForCategoryName(anyString(), anyLong(), anyLong()),
            ).thenReturn(flowOf(mockTransactionDetails))
            `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(flowOf(emptyList()))

            // Act
            viewModel = DrilldownViewModel(transactionRepository, DrilldownType.CATEGORY, "Food", 1, 2025, testDispatcherProvider)

            // Assert
            viewModel.monthlyTrendChartData.test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `monthlyTrendChartData reactively updates when repository flow emits new trends`() =
        runTest {
            val trendsFlow = MutableSharedFlow<List<MonthlyTrend>>()
            `when`(
                transactionRepository.getTransactionsForCategoryName(anyString(), anyLong(), anyLong()),
            ).thenReturn(flowOf(mockTransactionDetails))
            `when`(transactionRepository.getMonthlyTrends(anyLong())).thenReturn(trendsFlow)

            viewModel = DrilldownViewModel(transactionRepository, DrilldownType.CATEGORY, "Food", 1, 2025, testDispatcherProvider)

            viewModel.monthlyTrendChartData.test {
                // Initial emission before any trends emitted
                assertNull(awaitItem())

                // First emission: 1 month of trends
                trendsFlow.emit(listOf(MonthlyTrend("2025-01", 50.0, 100.0)))
                val firstData = awaitItem()
                assertNotNull(firstData)
                assertEquals(6, firstData!!.second.size)

                // Second emission: Updated trends
                trendsFlow.emit(
                    listOf(
                        MonthlyTrend("2025-01", 50.0, 100.0),
                        MonthlyTrend("2025-02", 75.0, 150.0),
                    ),
                )
                val secondData = awaitItem()
                assertNotNull(secondData)
                assertEquals(6, secondData!!.second.size)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
