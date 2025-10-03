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
import io.pm.finlight.DrilldownType
import io.pm.finlight.DrilldownViewModel
import io.pm.finlight.PeriodTotal
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDao
import io.pm.finlight.TransactionDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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

    @Mock
    private lateinit var transactionDao: TransactionDao

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

    private val mockTransactionDetails = listOf(
        TransactionDetails(
            Transaction(id = 1, description = "Test", categoryId = 1, amount = 100.0, date = 0, accountId = 1, notes = null),
            emptyList(), "Account", "Category", "icon", "color", null
        )
    )

    private val mockPeriodTotals = listOf(
        PeriodTotal("2025-01", 100.0),
        PeriodTotal("2025-02", 150.0)
    )

    @Test
    fun `when dimension is CATEGORY, it calls correct DAO methods`() = runTest {
        // Arrange
        val categoryName = "Food"
        `when`(transactionDao.getTransactionsForCategoryName(anyString(), anyLong(), anyLong())).thenReturn(flowOf(mockTransactionDetails))
        `when`(transactionDao.getMonthlySpendingForCategory(anyString(), anyLong(), anyLong())).thenReturn(flowOf(mockPeriodTotals))

        // Act
        viewModel = DrilldownViewModel(transactionDao, DrilldownType.CATEGORY, categoryName, 1, 2025)

        // Assert
        viewModel.transactionsForMonth.test {
            assertEquals(mockTransactionDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.monthlyTrendChartData.test {
            // Test that the chart data is processed correctly
            val chartData = awaitItem()
            assertEquals(6, chartData?.second?.size) // 6 months of labels
            assertEquals(6, chartData?.first?.entryCount)
            cancelAndIgnoreRemainingEvents()
        }

        verify(transactionDao).getTransactionsForCategoryName(anyString(), anyLong(), anyLong())
        verify(transactionDao).getMonthlySpendingForCategory(anyString(), anyLong(), anyLong())
    }

    @Test
    fun `when dimension is MERCHANT, it calls correct DAO methods`() = runTest {
        // Arrange
        val merchantName = "Amazon"
        `when`(transactionDao.getTransactionsForMerchantName(anyString(), anyLong(), anyLong())).thenReturn(flowOf(mockTransactionDetails))
        `when`(transactionDao.getMonthlySpendingForMerchant(anyString(), anyLong(), anyLong())).thenReturn(flowOf(mockPeriodTotals))

        // Act
        viewModel = DrilldownViewModel(transactionDao, DrilldownType.MERCHANT, merchantName, 1, 2025)

        // Assert
        viewModel.transactionsForMonth.test {
            assertEquals(mockTransactionDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.monthlyTrendChartData.test {
            val chartData = awaitItem()
            assertEquals(6, chartData?.second?.size)
            assertEquals(6, chartData?.first?.entryCount)
            cancelAndIgnoreRemainingEvents()
        }

        verify(transactionDao).getTransactionsForMerchantName(anyString(), anyLong(), anyLong())
        verify(transactionDao).getMonthlySpendingForMerchant(anyString(), anyLong(), anyLong())
    }
}
