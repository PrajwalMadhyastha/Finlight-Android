// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/AnalysisDetailViewModelTest.kt
// REASON: NEW FILE - Unit tests for AnalysisDetailViewModel. This covers the
// logic for fetching transactions based on the provided analysis dimension
// (Category, Tag, or Merchant).
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
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
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AnalysisDetailViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var transactionDao: TransactionDao

    private lateinit var viewModel: AnalysisDetailViewModel

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

    @Test
    fun `when dimension is CATEGORY, it calls getTransactionsForCategoryInRange`() = runTest {
        // Arrange
        val categoryId = "123"
        val startDate = 1000L
        val endDate = 2000L
        `when`(transactionDao.getTransactionsForCategoryInRange(categoryId.toInt(), startDate, endDate)).thenReturn(flowOf(mockTransactionDetails))

        // Act
        viewModel = AnalysisDetailViewModel(transactionDao, AnalysisDimension.CATEGORY, categoryId, startDate, endDate)

        // Assert
        viewModel.transactions.test {
            assertEquals(mockTransactionDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getTransactionsForCategoryInRange(categoryId.toInt(), startDate, endDate)
    }

    @Test
    fun `when dimension is TAG, it calls getTransactionsForTagInRange`() = runTest {
        // Arrange
        val tagId = "456"
        val startDate = 1000L
        val endDate = 2000L
        `when`(transactionDao.getTransactionsForTagInRange(tagId.toInt(), startDate, endDate)).thenReturn(flowOf(mockTransactionDetails))

        // Act
        viewModel = AnalysisDetailViewModel(transactionDao, AnalysisDimension.TAG, tagId, startDate, endDate)

        // Assert
        viewModel.transactions.test {
            assertEquals(mockTransactionDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getTransactionsForTagInRange(tagId.toInt(), startDate, endDate)
    }

    @Test
    fun `when dimension is MERCHANT, it calls getTransactionsForMerchantInRange`() = runTest {
        // Arrange
        val merchantName = "Amazon"
        val startDate = 1000L
        val endDate = 2000L
        `when`(transactionDao.getTransactionsForMerchantInRange(merchantName, startDate, endDate)).thenReturn(flowOf(mockTransactionDetails))

        // Act
        viewModel = AnalysisDetailViewModel(transactionDao, AnalysisDimension.MERCHANT, merchantName, startDate, endDate)

        // Assert
        viewModel.transactions.test {
            assertEquals(mockTransactionDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getTransactionsForMerchantInRange(merchantName, startDate, endDate)
    }
}
