package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.Budget
import io.pm.finlight.BudgetDao
import io.pm.finlight.BudgetRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BudgetRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var budgetDao: BudgetDao

    private lateinit var repository: BudgetRepository

    @Before
    override fun setup() {
        super.setup()
        repository = BudgetRepository(budgetDao)
    }

    @Test
    fun `getBudgetsForMonth calls DAO`() = runTest {
        // Arrange
        val month = 10
        val year = 2025
        `when`(budgetDao.getBudgetsForMonth(month, year)).thenReturn(flowOf(emptyList()))

        // Act
        repository.getBudgetsForMonth(month, year).test {
            assertEquals(emptyList<Budget>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        verify(budgetDao).getBudgetsForMonth(month, year)
    }

    @Test
    fun `getBudgetsForMonthWithSpending calls DAO`() = runTest {
        // Arrange
        val yearMonth = "2025-10"
        val month = 10
        val year = 2025
        `when`(budgetDao.getBudgetsWithSpendingForMonth(yearMonth, month, year)).thenReturn(flowOf(emptyList()))

        // Act
        repository.getBudgetsForMonthWithSpending(yearMonth, month, year).test {
            assertEquals(emptyList<Budget>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        verify(budgetDao).getBudgetsWithSpendingForMonth(yearMonth, month, year)
    }

    @Test
    fun `getActualSpendingForCategory calls DAO`() = runTest {
        // Arrange
        val categoryName = "Food"
        val month = 10
        val year = 2025
        `when`(budgetDao.getActualSpendingForCategory(categoryName, month, year)).thenReturn(flowOf(123.45))

        // Act
        repository.getActualSpendingForCategory(categoryName, month, year).test {
            assertEquals(123.45, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        verify(budgetDao).getActualSpendingForCategory(categoryName, month, year)
    }

    @Test
    fun `insert calls DAO`() = runTest {
        // Arrange
        val budget = Budget(categoryName = "Food", amount = 500.0, month = 10, year = 2025)

        // Act
        repository.insert(budget)

        // Assert
        verify(budgetDao).insert(budget)
    }

    @Test
    fun `update calls DAO`() = runTest {
        // Arrange
        val budget = Budget(id = 1, categoryName = "Food", amount = 600.0, month = 10, year = 2025)

        // Act
        repository.update(budget)

        // Assert
        verify(budgetDao).update(budget)
    }

    @Test
    fun `delete calls DAO`() = runTest {
        // Arrange
        val budget = Budget(id = 1, categoryName = "Food", amount = 600.0, month = 10, year = 2025)

        // Act
        repository.delete(budget)

        // Assert
        verify(budgetDao).delete(budget)
    }

    @Test
    fun `getBudgetById calls DAO`() = runTest {
        // Arrange
        val budgetId = 1
        `when`(budgetDao.getById(anyInt())).thenReturn(flowOf(null))

        // Act
        repository.getBudgetById(budgetId)

        // Assert
        verify(budgetDao).getById(budgetId)
    }
}
