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
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `getRuleById calls DAO`() =
        runTest {
            // Arrange
            val ruleId = 5
            val expectedRule =
                RecurringTransaction(
                    id = ruleId,
                    description = "Netflix",
                    amount = 149.0,
                    transactionType = TransactionType.EXPENSE,
                    recurrenceInterval = "Monthly",
                    startDate = 0L,
                    accountId = 1,
                    categoryId = 1,
                )
            `when`(recurringTransactionDao.getRuleById(ruleId)).thenReturn(expectedRule)

            // Act
            val result = repository.getRuleById(ruleId)

            // Assert
            assertEquals(expectedRule, result)
            verify(recurringTransactionDao).getRuleById(ruleId)
        }

    @Test
    fun `getRuleById returns null when DAO returns null`() =
        runTest {
            // Arrange
            val ruleId = 99
            `when`(recurringTransactionDao.getRuleById(ruleId)).thenReturn(null)

            // Act
            val result = repository.getRuleById(ruleId)

            // Assert
            assertNull(result)
            verify(recurringTransactionDao).getRuleById(ruleId)
        }

    @Test
    fun `updateLastRunDate calls DAO`() =
        runTest {
            // Arrange
            val ruleId = 5
            val lastRunDate = 1700000000000L

            // Act
            repository.updateLastRunDate(ruleId, lastRunDate)

            // Assert
            verify(recurringTransactionDao).updateLastRunDate(ruleId, lastRunDate)
        }

    @Test
    fun `updateLastRunAndSkipCount calls DAO`() =
        runTest {
            // Arrange
            val ruleId = 5
            val lastRunDate = 1700000000000L
            val skipCount = 2

            // Act
            repository.updateLastRunAndSkipCount(ruleId, lastRunDate, skipCount)

            // Assert
            verify(recurringTransactionDao).updateLastRunAndSkipCount(ruleId, lastRunDate, skipCount)
        }
}
