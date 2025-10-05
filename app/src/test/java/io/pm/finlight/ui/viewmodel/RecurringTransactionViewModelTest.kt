// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/RecurringTransactionViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.RecurringTransactionRepository
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringTransactionViewModelTest : BaseViewModelTest() {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Mock
    private lateinit var recurringTransactionRepository: RecurringTransactionRepository

    @Captor
    private lateinit var recurringTransactionCaptor: ArgumentCaptor<RecurringTransaction>

    private lateinit var viewModel: RecurringTransactionViewModel

    @Before
    override fun setup() {
        super.setup()
    }

    private fun initializeViewModel(initialRules: List<RecurringTransaction> = emptyList()) {
        `when`(recurringTransactionRepository.getAll()).thenReturn(flowOf(initialRules))
        viewModel = RecurringTransactionViewModel(application, recurringTransactionRepository)
    }

    @Test
    fun `allRecurringTransactions flow emits rules from repository`() = runTest {
        // Arrange
        val rules = listOf(
            RecurringTransaction(1, "Netflix", 149.0, "expense", "Monthly", 0L, 1, 1, null)
        )
        initializeViewModel(rules)

        // Assert
        viewModel.allRecurringTransactions.test {
            assertEquals(rules, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRuleById calls repository getById`() = runTest {
        // Arrange
        val ruleId = 1
        val rule = RecurringTransaction(ruleId, "Netflix", 149.0, "expense", "Monthly", 0L, 1, 1, null)
        `when`(recurringTransactionRepository.getById(ruleId)).thenReturn(flowOf(rule))
        initializeViewModel()

        // Act
        viewModel.getRuleById(ruleId)

        // Assert
        verify(recurringTransactionRepository).getById(ruleId)
    }

    @Test
    @Ignore
    fun `saveRule with null id calls repository insert`() = runTest {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.saveRule(null, "Spotify", 129.0, "expense", "Monthly", 0L, 1, 1, null)

        // Assert
        verify(recurringTransactionRepository).insert(recurringTransactionCaptor.capture())
        val capturedRule = recurringTransactionCaptor.value
        assertEquals("Spotify", capturedRule.description)
        assertEquals(129.0, capturedRule.amount, 0.0)
        assertEquals(0, capturedRule.id) // ID is 0 for new entities
    }

    @Test
    @Ignore
    fun `saveRule with existing id calls repository update`() = runTest {
        // Arrange
        initializeViewModel()
        val ruleId = 10

        // Act
        viewModel.saveRule(ruleId, "Updated Spotify", 199.0, "expense", "Monthly", 0L, 1, 1, null)

        // Assert
        verify(recurringTransactionRepository).update(recurringTransactionCaptor.capture())
        val capturedRule = recurringTransactionCaptor.value
        assertEquals("Updated Spotify", capturedRule.description)
        assertEquals(199.0, capturedRule.amount, 0.0)
        assertEquals(ruleId, capturedRule.id)
    }

    @Test
    fun `deleteRule calls repository delete`() = runTest {
        // Arrange
        initializeViewModel()
        val ruleToDelete = RecurringTransaction(1, "Old Subscription", 99.0, "expense", "Monthly", 0L, 1, 1, null)

        // Act
        viewModel.deleteRule(ruleToDelete)

        // Assert
        verify(recurringTransactionRepository).delete(ruleToDelete)
    }
}