package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.RecurringPattern
import io.pm.finlight.RecurringPatternDao
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
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringTransactionRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var recurringTransactionDao: RecurringTransactionDao

    @Mock
    private lateinit var recurringPatternDao: RecurringPatternDao

    private lateinit var repository: RecurringTransactionRepository

    @Before
    override fun setup() {
        super.setup()
        repository = RecurringTransactionRepository(recurringTransactionDao, recurringPatternDao)
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

    @Test
    fun `updateLastRunDate with invalid arguments throws IllegalArgumentException`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunDate(0, 1700000000000L)
            }
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunDate(-5, 1700000000000L)
            }
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunDate(5, 0L)
            }
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunDate(5, -100L)
            }
            verifyNoInteractions(recurringTransactionDao)
        }

    @Test
    fun `updateLastRunAndSkipCount with invalid arguments throws IllegalArgumentException`() =
        runTest {
            // invalid ruleId
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunAndSkipCount(0, 1700000000000L, 0)
            }
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunAndSkipCount(-1, 1700000000000L, 0)
            }
            // invalid lastRunDate
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunAndSkipCount(5, 0L, 0)
            }
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunAndSkipCount(5, -100L, 0)
            }
            // negative skipCount
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunAndSkipCount(5, 1700000000000L, -1)
            }
            assertFailsWith<IllegalArgumentException> {
                repository.updateLastRunAndSkipCount(5, 1700000000000L, -10)
            }
            verifyNoInteractions(recurringTransactionDao)
        }

    @Test
    fun `getUnacknowledgedPatterns calls recurringPatternDao`() {
        // Act
        repository.getUnacknowledgedPatterns()

        // Assert
        verify(recurringPatternDao).getUnacknowledgedPatterns()
    }

    @Test
    fun `dismissPatternSuggestion calls recurringPatternDao update with isDismissed true`() =
        runTest {
            // Arrange
            val pattern =
                RecurringPattern(
                    smsSignature = "NETFLIX",
                    description = "Netflix",
                    amount = 149.0,
                    transactionType = TransactionType.EXPENSE,
                    accountId = 1,
                    categoryId = 1,
                    occurrences = 2,
                    firstSeen = 1000L,
                    lastSeen = 2000L,
                    isDismissed = false,
                )

            // Act
            repository.dismissPatternSuggestion(pattern)

            // Assert
            verify(recurringPatternDao).update(pattern.copy(isDismissed = true))
        }
}
