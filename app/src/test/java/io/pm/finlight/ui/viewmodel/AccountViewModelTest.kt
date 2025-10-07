package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
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
        `when`(accountRepository.allAccounts).thenReturn(flowOf(emptyList()))


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
        viewModel.uiEvent.test {
            assertEquals("Accounts merged successfully.", awaitItem())
        }
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

    @Test
    fun `addAccount with existing name sends error event`() = runTest {
        // Arrange
        val accountName = "Existing Account"
        val existingAccount = Account(1, accountName, "Bank")
        `when`(accountRepository.allAccounts).thenReturn(flowOf(listOf(existingAccount)))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.addAccount(accountName, "Card")
            advanceUntilIdle()

            assertEquals("An account named '$accountName' already exists.", awaitItem())
            verify(accountRepository, never()).insert(anyObject())
        }
    }

    @Test
    fun `addAccount failure sends error event`() = runTest {
        // Arrange
        val errorMessage = "DB Error"
        `when`(accountRepository.allAccounts).thenReturn(flowOf(emptyList()))
        `when`(accountRepository.insert(anyObject())).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.addAccount("New Account", "Bank")
            advanceUntilIdle()

            assertEquals("Error creating account: $errorMessage", awaitItem())
        }
    }

    @Test
    fun `deleteAccount success sends success event`() = runTest {
        // Arrange
        val accountToDelete = Account(1, "Test Account", "Bank")

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.deleteAccount(accountToDelete)
            advanceUntilIdle()

            verify(accountRepository).delete(accountToDelete)
            assertEquals("Account '${accountToDelete.name}' deleted.", awaitItem())
        }
    }

    @Test
    fun `deleteAccount failure sends error event`() = runTest {
        // Arrange
        val accountToDelete = Account(1, "Test Account", "Bank")
        val errorMessage = "DB Error"
        `when`(accountRepository.delete(anyObject())).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.deleteAccount(accountToDelete)
            advanceUntilIdle()

            assertEquals("Error deleting account: $errorMessage", awaitItem())
        }
    }

    @Test
    fun `mergeSelectedAccounts with invalid selection sends error event`() = runTest {
        // Arrange
        viewModel.enterSelectionMode(1) // Select only one account

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.mergeSelectedAccounts(1)
            advanceUntilIdle()

            assertEquals("Error: Invalid selection for merge.", awaitItem())
            verify(accountRepository, never()).mergeAccounts(anyInt(), anyObject())
        }
    }

    @Test
    fun `mergeSelectedAccounts failure sends error event`() = runTest {
        // Arrange
        val destinationId = 1
        val sourceIds = listOf(2)
        val errorMessage = "DB Error"
        `when`(accountRepository.mergeAccounts(destinationId, sourceIds)).thenThrow(RuntimeException(errorMessage))

        viewModel.enterSelectionMode(null)
        viewModel.toggleAccountSelection(destinationId)
        viewModel.toggleAccountSelection(sourceIds[0])
        advanceUntilIdle()

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.mergeSelectedAccounts(destinationId)
            advanceUntilIdle()

            assertEquals("Error merging accounts: $errorMessage", awaitItem())
            // ViewModel should still clear selection mode even on failure
            assertEquals(false, viewModel.isSelectionModeActive.value)
            assertTrue(viewModel.selectedAccountIds.value.isEmpty())
        }
    }
}