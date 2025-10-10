package io.pm.finlight.ui.viewmodel

import android.app.Application
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
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

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

    @Captor
    private lateinit var accountCaptor: ArgumentCaptor<Account>

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

    @Test
    fun `getAccountById calls repository`() = runTest {
        // Arrange
        val accountId = 1
        val account = Account(id = accountId, name = "Test", type = "Bank")
        `when`(accountRepository.getAccountById(accountId)).thenReturn(flowOf(account))

        // Act & Assert
        viewModel.getAccountById(accountId).test {
            assertEquals(account, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(accountRepository).getAccountById(accountId)
    }

    @Test
    fun `getAccountBalance correctly calculates from transactions`() = runTest {
        // Arrange
        val accountId = 1
        val transactions = listOf(
            TransactionDetails(Transaction(id=1, description = "", categoryId = 1, amount = 1000.0, date = 0, accountId = accountId, notes = null, transactionType = "income"), emptyList(), "","","","","",),
            TransactionDetails(Transaction(id=2, description = "", categoryId = 1, amount = 250.0, date = 0, accountId = accountId, notes = null, transactionType = "expense"), emptyList(), "","","","","",),
            TransactionDetails(Transaction(id=3, description = "", categoryId = 1, amount = 50.50, date = 0, accountId = accountId, notes = null, transactionType = "expense"), emptyList(), "","","","","",)
        )
        `when`(transactionRepository.getTransactionsForAccount(accountId)).thenReturn(flowOf(transactions.map { it.transaction }))

        // Act & Assert
        viewModel.getAccountBalance(accountId).test {
            val balance = awaitItem()
            // 1000.0 - 250.0 - 50.50 = 699.5, rounded to 700
            assertEquals(700L, balance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTransactionsForAccount calls repository`() = runTest {
        // Arrange
        val accountId = 1
        val transactionDetails = listOf(TransactionDetails(Transaction(id=1, description = "Test", categoryId = 1, amount = 100.0, date = 0, accountId = accountId, notes = null), emptyList(), "","","","","",))
        `when`(transactionRepository.getTransactionsForAccountDetails(accountId)).thenReturn(flowOf(transactionDetails))

        // Act & Assert
        viewModel.getTransactionsForAccount(accountId).test {
            assertEquals(transactionDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionRepository).getTransactionsForAccountDetails(accountId)
    }

    @Test
    fun `updateAccount calls repository`() = runTest {
        // Arrange
        val account = Account(id = 1, name = "Test", type = "Bank")

        // Act
        viewModel.updateAccount(account)
        advanceUntilIdle()

        // Assert
        verify(accountRepository).update(account)
    }

    @Test
    fun `renameAccount finds account and calls repository update`() = runTest {
        // Arrange
        val accountId = 1
        val originalAccount = Account(id = accountId, name = "Old Name", type = "Bank")
        val newName = "New Name"
        `when`(accountRepository.getAccountById(accountId)).thenReturn(flowOf(originalAccount))

        // Act
        viewModel.renameAccount(accountId, newName)
        advanceUntilIdle()

        // Assert
        verify(accountRepository).update(capture(accountCaptor))
        val updatedAccount = accountCaptor.value
        assertEquals(newName, updatedAccount.name)
        assertEquals(accountId, updatedAccount.id)
    }

    @Test
    fun `dismissMergeSuggestion calls settings repository`() {
        // Arrange
        val account1 = Account(id = 1, name = "Acc 1", type = "Bank")
        val account2 = Account(id = 2, name = "Acc 2", type = "Bank")
        val suggestion = Pair(account1, account2)
        val expectedKey = "${min(account1.id, account2.id)}|${max(account1.id, account2.id)}"

        // Act
        viewModel.dismissMergeSuggestion(suggestion)

        // Assert
        verify(settingsRepository).addDismissedMergeSuggestion(expectedKey)
    }

    @Test
    fun `enterSelectionModeWithSuggestions sets state correctly`() = runTest {
        // Arrange
        val accounts = listOf(
            Account(id = 5, name = "Acc 5", type = "Bank"),
            Account(id = 10, name = "Acc 10", type = "Bank")
        )

        // Act
        viewModel.enterSelectionModeWithSuggestions(accounts)

        // Assert
        assertTrue(viewModel.isSelectionModeActive.value)
        assertEquals(setOf(5, 10), viewModel.selectedAccountIds.value)
    }

    @Test
    fun `enterSelectionMode sets state correctly`() = runTest {
        // Act (with ID)
        viewModel.enterSelectionMode(7)

        // Assert (with ID)
        assertTrue(viewModel.isSelectionModeActive.value)
        assertEquals(setOf(7), viewModel.selectedAccountIds.value)

        // Act (with null)
        viewModel.enterSelectionMode(null)

        // Assert (with null)
        assertTrue(viewModel.isSelectionModeActive.value)
        assertTrue(viewModel.selectedAccountIds.value.isEmpty())
    }
}