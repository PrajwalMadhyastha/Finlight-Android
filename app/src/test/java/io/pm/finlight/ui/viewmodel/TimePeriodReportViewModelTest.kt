// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/TimePeriodReportViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.data.model.TimePeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.math.roundToLong

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TimePeriodReportViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: TimePeriodReportViewModel

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `totalIncome is correctly calculated and converted to Long`() = runTest {
        // ARRANGE
        val transactions = listOf(
            TransactionDetails(Transaction(id = 1, amount = 1000.50, transactionType = "income", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
            TransactionDetails(Transaction(id = 2, amount = 2000.25, transactionType = "income", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null),
            TransactionDetails(Transaction(id = 3, amount = 500.0, transactionType = "expense", description = "", categoryId = 1, date = 0L, accountId = 1, notes = null), emptyList(), null, null, null, null, null)
        )
        `when`(transactionDao.getTransactionDetailsForRange(anyLong(), anyLong(), any(), any(), any())).thenReturn(flowOf(transactions))
        `when`(transactionDao.getFinancialSummaryForRange(anyLong(), anyLong())).thenReturn(null)
        `when`(transactionDao.getTopSpendingCategoriesForRange(anyLong(), anyLong())).thenReturn(emptyList())


        // ACT
        viewModel = TimePeriodReportViewModel(transactionDao, settingsRepository, TimePeriod.MONTHLY, null, false)
        advanceUntilIdle()

        // ASSERT
        val expectedTotalIncome = (1000.50 + 2000.25).roundToLong() // 3001
        val actualTotalIncome = viewModel.totalIncome.first()

        assertEquals(expectedTotalIncome, actualTotalIncome)
    }

}