// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/AccountViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class has been updated to extend the new
// `BaseViewModelTest`. All boilerplate for JUnit rules, coroutine dispatchers,
// and Mockito initialization has been removed and is now inherited from the base
// class.
// FIX (Flaky Test) - The `suggestedMerges` test has been made deterministic.
// It now uses `advanceUntilIdle()` *before* collecting the flow. This ensures
// the coroutine in the ViewModel's `init` block completes before assertions
// are made, eliminating a race condition that caused intermittent failures.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class AccountViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var accountRepository: AccountRepository
    @Mock
    private lateinit var transactionRepository: TransactionRepository
    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: AccountViewModel

    @Before
    override fun setup() {
        super.setup()

        // Setup default mock behaviors needed for ViewModel initialization
        `when`(accountRepository.accountsWithBalance).thenReturn(flowOf(emptyList()))
        `when`(settingsRepository.getDismissedMergeSuggestions()).thenReturn(flowOf(emptySet()))

        viewModel = AccountViewModel(
            ApplicationProvider.getApplicationContext(),
            accountRepository,
            transactionRepository,
            settingsRepository
        )
    }

    @Test
    fun `accountsWithBalance should emit what the repository provides`() = runTest {
        // ARRANGE
        val mockAccounts = listOf(
            AccountWithBalance(Account(id = 1, name = "Savings", type = "Bank"), 1000.0),
            AccountWithBalance(Account(id = 2, name = "Credit Card", type = "Card"), -500.0)
        )
        `when`(accountRepository.accountsWithBalance).thenReturn(flowOf(mockAccounts))

        // ACT
        viewModel = AccountViewModel(
            ApplicationProvider.getApplicationContext(),
            accountRepository,
            transactionRepository,
            settingsRepository
        )
        val result = viewModel.accountsWithBalance.first()

        // ASSERT
        assertEquals(mockAccounts, result)
    }

    @Test
    fun `toggleAccountSelection should add and remove ids from selection`() = runTest {
        // ACT
        viewModel.enterSelectionMode(null) // Enter selection mode first
        viewModel.toggleAccountSelection(1)
        // ASSERT
        assertEquals(setOf(1), viewModel.selectedAccountIds.value)

        // ACT
        viewModel.toggleAccountSelection(2)
        // ASSERT
        assertEquals(setOf(1, 2), viewModel.selectedAccountIds.value)

        // ACT
        viewModel.toggleAccountSelection(1)
        // ASSERT
        assertEquals(setOf(2), viewModel.selectedAccountIds.value)
    }

    @Test
    fun `clearSelectionMode should reset selection state`() = runTest {
        // ARRANGE
        viewModel.enterSelectionMode(1)
        viewModel.toggleAccountSelection(2)

        // ACT
        viewModel.clearSelectionMode()

        // ASSERT
        assertEquals(false, viewModel.isSelectionModeActive.value)
        assertTrue(viewModel.selectedAccountIds.value.isEmpty())
    }

    @Test
    fun `mergeSelectedAccounts should call repository and clear selection`() = runTest {
        // ARRANGE
        val destinationId = 1
        val sourceIds = listOf(2, 3)
        viewModel.enterSelectionMode(null)
        viewModel.toggleAccountSelection(destinationId)
        viewModel.toggleAccountSelection(sourceIds[0])
        viewModel.toggleAccountSelection(sourceIds[1])

        // ACT
        viewModel.mergeSelectedAccounts(destinationId)
        advanceUntilIdle()

        // ASSERT
        verify(accountRepository).mergeAccounts(destinationId, sourceIds)
        assertEquals(false, viewModel.isSelectionModeActive.value)
        assertTrue(viewModel.selectedAccountIds.value.isEmpty())
    }

    @Test
    fun `suggestedMerges should identify similar account names`() = runTest {
        // ARRANGE
        val mockAccounts = listOf(
            AccountWithBalance(Account(id = 1, name = "ICICI Bank", type = "Bank"), 1000.0),
            AccountWithBalance(Account(id = 2, name = "ICICI - xx1234", type = "Card"), -500.0),
            AccountWithBalance(Account(id = 3, name = "HDFC Bank", type = "Bank"), 2000.0)
        )
        `when`(accountRepository.accountsWithBalance).thenReturn(flowOf(mockAccounts))

        // ACT - Re-init to trigger the logic in the init block
        viewModel = AccountViewModel(
            ApplicationProvider.getApplicationContext(),
            accountRepository,
            transactionRepository,
            settingsRepository
        )
        // Ensure all coroutines launched in init complete before we assert
        advanceUntilIdle()

        // ASSERT
        viewModel.suggestedMerges.test {
            // The flow has already settled on its final value. Await the one and only item.
            val suggestions = awaitItem()
            assertEquals(1, suggestions.size)
            val suggestion = suggestions.first()
            assertEquals(setOf(1, 2), setOf(suggestion.first.id, suggestion.second.id))

            cancelAndIgnoreRemainingEvents()
        }
    }
}