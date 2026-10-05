package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.RecurringTransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringTransactionRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var recurringTransactionDao: RecurringTransactionDao

    private lateinit var repository: RecurringTransactionRepository

    @Before
    override fun setup() {
        super.setup()
        repository = RecurringTransactionRepository(recurringTransactionDao)
    }

    @Test
    fun `getAll calls DAO`() {
        // Act
        repository.getAll()
        // Assert
        verify(recurringTransactionDao).getAllRulesFlow()
    }

    @Test
    fun `getById calls DAO`() {
        // Arrange
        val id = 1
        // Act
        repository.getById(id)
        // Assert
        verify(recurringTransactionDao).getById(id)
    }

    @Test
    fun `insert calls DAO`() =
        runTest {
            // Arrange
            val rule =
                RecurringTransaction(
                    description = "Netflix",
                    amount = 149.0,
                    transactionType = TransactionType.EXPENSE,
                    recurrenceInterval = "Monthly",
                    startDate = 0L,
                    accountId = 1,
                    categoryId = 1,
                )
            // Act
            repository.insert(rule)
            // Assert
            verify(recurringTransactionDao).insert(rule)
        }

    @Test
    fun `update calls DAO`() =
        runTest {
            // Arrange
            val rule =
                RecurringTransaction(
                    id = 1,
                    description = "Netflix",
                    amount = 149.0,
                    transactionType = TransactionType.EXPENSE,
                    recurrenceInterval = "Monthly",
                    startDate = 0L,
                    accountId = 1,
                    categoryId = 1,
                )
            // Act
            repository.update(rule)
            // Assert
            verify(recurringTransactionDao).update(rule)
        }

    @Test
    fun `delete calls DAO`() =
        runTest {
            // Arrange
            val rule =
                RecurringTransaction(
                    id = 1,
                    description = "Netflix",
                    amount = 149.0,
                    transactionType = TransactionType.EXPENSE,
                    recurrenceInterval = "Monthly",
                    startDate = 0L,
                    accountId = 1,
                    categoryId = 1,
                )
            // Act
            repository.delete(rule)
            // Assert
            verify(recurringTransactionDao).delete(rule)
        }

    @Test
    fun `updateLastRunAndSkipCount calls DAO methods in order`() =
        runTest {
            // Arrange
            val ruleId = 5
            val lastRunDate = 1700000000000L
            val skipCount = 2

            // Act
            repository.updateLastRunAndSkipCount(ruleId, lastRunDate, skipCount)

            // Assert
            val inOrder = inOrder(recurringTransactionDao)
            inOrder.verify(recurringTransactionDao).updateLastRunDate(ruleId, lastRunDate)
            inOrder.verify(recurringTransactionDao).updateSkipCount(ruleId, skipCount)
        }
}
