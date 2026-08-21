package io.pm.finlight.ui.viewmodel

import androidx.room.withTransaction
import app.cash.turbine.test
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.lang.RuntimeException
import kotlin.time.Duration.Companion.seconds
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelSaveAndEditTest : TransactionViewModelBaseSetup() {
    @Test
    fun `onSaveTapped with only amount saves directly without category nudge`() =
        runTest {
            // ARRANGE
            val accountId = 1
            var capturedId: Long? = null
            val expectedId = 1L
            whenever(transactionRepository.insertTransactionWithTagsAndImages(any(), any(), any())).thenReturn(expectedId)

            // ACT
            viewModel.onSaveTapped(
                // No description
                description = "",
                amountStr = "100.0",
                accountId = accountId,
                categoryId = null,
                notes = null,
                date = 0L,
                transactionType = "expense",
                imageUris = emptyList(),
            ) { capturedId = it }
            advanceUntilIdle()

            // ASSERT
            assertNull(viewModel.showCategoryNudge.value)
            val transactionCaptor = argumentCaptor<Transaction>()
            verify(transactionRepository).insertTransactionWithTagsAndImages(transactionCaptor.capture(), any(), any())
            assertEquals(100.0, transactionCaptor.firstValue.originalAmount)
            assertEquals(expectedId, capturedId)
        }

    @Test
    fun `onSaveTapped with description and amount shows category nudge`() =
        runTest {
            // ARRANGE
            val accountId = 1
            var callbackInvoked = false

            // ACT
            viewModel.onSaveTapped(
                // Description is present
                description = "Coffee",
                amountStr = "100.0",
                accountId = accountId,
                // No category selected yet
                categoryId = null,
                notes = null,
                date = 0L,
                transactionType = "expense",
                imageUris = emptyList(),
            ) { callbackInvoked = true }
            advanceUntilIdle()

            // ASSERT
            assertNotNull(viewModel.showCategoryNudge.value)
            assertEquals("Coffee", viewModel.showCategoryNudge.value?.description)
            verify(transactionRepository, never()).insertTransactionWithTagsAndImages(any(), any(), any())
            assertFalse(callbackInvoked)
        }

    @Test
    fun `saveWithSelectedCategory saves transaction with correct data from nudge`() =
        runTest {
            // ARRANGE
            val accountId = 1
            var capturedId: Long? = null
            val expectedId = 1L
            val newCategoryId = 5
            val transactionCaptor = argumentCaptor<Transaction>()

            whenever(transactionRepository.insertTransactionWithTagsAndImages(any(), any(), any())).thenReturn(expectedId)

            // First, trigger the nudge
            viewModel.onSaveTapped(
                description = "Lunch",
                amountStr = "250.0",
                accountId = accountId,
                categoryId = null,
                notes = "With friends",
                date = 12345L,
                transactionType = "expense",
                imageUris = emptyList(),
            ) {}
            advanceUntilIdle()
            assertNotNull(viewModel.showCategoryNudge.value)

            // ACT
            viewModel.saveWithSelectedCategory(newCategoryId) { capturedId = it }
            advanceUntilIdle()

            // ASSERT
            verify(transactionRepository).insertTransactionWithTagsAndImages(transactionCaptor.capture(), any(), any())
            val savedTransaction = transactionCaptor.firstValue
            assertEquals("Lunch", savedTransaction.description)
            assertEquals(250.0, savedTransaction.amount, 0.0)
            assertEquals(accountId, savedTransaction.accountId)
            assertEquals(newCategoryId, savedTransaction.categoryId)
            assertEquals("With friends", savedTransaction.notes)
            assertEquals(12345L, savedTransaction.date)
            assertEquals(250.0, savedTransaction.originalAmount)
            assertEquals(expectedId, capturedId)
            assertNull(viewModel.showCategoryNudge.value) // Nudge should be cleared
        }

    @Test
    fun `saveManualTransaction applies currency conversion when international travel mode is active`() =
        runTest {
            // ARRANGE
            val travelSettings =
                TravelModeSettings(
                    isEnabled = true,
                    tripType = TripType.INTERNATIONAL,
                    startDate = 0L,
                    endDate = Long.MAX_VALUE,
                    conversionRate = 85.0f,
                    currencyCode = "USD",
                    tripName = "US Trip",
                )
            // Set up the mock to return the active travel settings
            whenever(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
            val transactionCaptor = argumentCaptor<Transaction>()
            whenever(transactionRepository.insertTransactionWithTagsAndImages(any(), any(), any())).thenReturn(1L)

            // Re-initialize the ViewModel to pick up the new mock behavior
            initializeViewModel()
            // Ensure the StateFlow in the ViewModel has time to collect the value from the repository
            advanceUntilIdle()

            // ACT
            viewModel.onSaveTapped(
                description = "Starbucks",
                // 5 USD
                amountStr = "5.0",
                accountId = 1,
                // Category provided, so no nudge
                categoryId = 1,
                notes = null,
                // Within trip date range
                date = 1000L,
                transactionType = "expense",
                imageUris = emptyList(),
            ) {}
            advanceUntilIdle() // Run the coroutine launched by onSaveTapped

            // ASSERT
            verify(transactionRepository).insertTransactionWithTagsAndImages(transactionCaptor.capture(), any(), any())
            val savedTransaction = transactionCaptor.firstValue
            assertEquals(425.0, savedTransaction.amount, 0.0) // 5.0 * 85.0
            assertEquals(5.0, savedTransaction.originalAmount!!, 0.0)
            assertEquals("USD", savedTransaction.currencyCode)
            assertEquals(85.0, savedTransaction.conversionRate!!, 0.0)
        }

    @Test
    fun `onSaveTapped shows validation error for zero amount`() =
        runTest {
            // ACT
            viewModel.onSaveTapped("Test", "0.0", 1, 1, null, 0L, "expense", emptyList()) {}
            advanceUntilIdle()

            // ASSERT
            viewModel.validationError.test {
                assertEquals("Please enter a valid, positive amount.", awaitItem())
            }
            verify(transactionRepository, never()).insertTransactionWithTagsAndImages(any(), any(), any())
        }

    @Test
    fun `onSaveTapped shows validation error for missing account`() =
        runTest {
            // ACT
            viewModel.onSaveTapped("Test", "100.0", null, 1, null, 0L, "expense", emptyList()) {}
            advanceUntilIdle()

            // ASSERT
            viewModel.validationError.test {
                assertEquals("An account must be selected.", awaitItem())
            }
            verify(transactionRepository, never()).insertTransactionWithTagsAndImages(any(), any(), any())
        }

    @Test
    fun `onSaveTapped shows validation error for amount exceeding limit`() =
        runTest {
            // ACT
            viewModel.onSaveTapped("Test", "2000000000.0", 1, 1, null, 0L, "expense", emptyList()) {}
            advanceUntilIdle()

            // ASSERT
            viewModel.validationError.test {
                assertEquals("Maximum limit of 1 Billion (1,000,000,000) reached.", awaitItem())
            }
            verify(transactionRepository, never()).insertTransactionWithTagsAndImages(any(), any(), any())
        }

    @Test
    fun `markAsReviewed calls repository successfully`() =
        runTest {
            // Arrange
            val transactionId = 1

            // Act
            viewModel.markAsReviewed(transactionId)
            advanceUntilIdle()

            // Assert
            verify(transactionRepository).clearReviewFlag(transactionId)
        }

    @Test
    fun `saveManualTransaction failure updates validationError`() =
        runTest {
            // ARRANGE
            val errorMessage = "An error occurred while saving."
            whenever(transactionRepository.insertTransactionWithTagsAndImages(any(), any(), any()))
                .thenThrow(RuntimeException("Database insertion failed"))
            var onSaveCompleteCalled = false

            // ACT
            viewModel.onSaveTapped(
                description = "Test",
                amountStr = "100.0",
                accountId = 1,
                categoryId = 1,
                notes = null,
                date = 0L,
                transactionType = "expense",
                imageUris = emptyList(),
            ) { onSaveCompleteCalled = true }
            advanceUntilIdle()

            // ASSERT
            viewModel.validationError.test {
                assertEquals(errorMessage, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse("onSaveComplete should not be called on failure", onSaveCompleteCalled)
        }

    @Test
    fun `updateDescription failure sends uiEvent`() =
        runTest {
            // ARRANGE
            val errorMessage = "Failed to update description. Please try again."
            whenever(transactionRepository.updateDescription(anyInt(), anyString())).thenThrow(RuntimeException("DB update failed"))
            whenever(transactionRepository.getTransactionById(anyInt())).thenReturn(flowOf(null)) // To simplify the test logic

            // ACT & ASSERT
            viewModel.uiEvent.test {
                viewModel.updateTransactionDescription(1, "New Description")
                advanceUntilIdle()
                assertEquals(errorMessage, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updateTransactionDescription deletes rule when description matches original`() =
        runTest {
            // Arrange
            val transactionId = 1
            val originalDesc = "Food in Office"
            val transaction =
                Transaction(
                    id = transactionId,
                    description = "Badminton",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    originalDescription = originalDesc
                )
            whenever(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(transaction))

            // Act
            viewModel.updateTransactionDescription(transactionId, "food in OFFICE") // Case-insensitive match
            advanceUntilIdle()

            // Assert
            verify(merchantRenameRuleRepository).deleteByOriginalName(originalDesc)
            verify(transactionRepository).updateDescription(transactionId, "food in OFFICE")
        }

    @Test
    fun `updateTransactionDescription does not delete rule when description differs`() =
        runTest {
            // Arrange
            val transactionId = 1
            val originalDesc = "Food in Office"
            val transaction =
                Transaction(
                    id = transactionId,
                    description = "Badminton",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    originalDescription = originalDesc
                )
            whenever(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(transaction))

            // Act
            viewModel.updateTransactionDescription(transactionId, "Another Name")
            advanceUntilIdle()

            // Assert
            verify(merchantRenameRuleRepository, never()).deleteByOriginalName(anyString())
            verify(transactionRepository).updateDescription(transactionId, "Another Name")
        }

    @Test
    fun `updateTransactionType calls repository successfully`() =
        runTest {
            // Arrange
            val transactionId = 1
            val newType = TransactionType.INCOME
            // No exception mocked, so the suspend function will just return (simulating success)

            // Act
            viewModel.updateTransactionType(transactionId, newType)
            advanceUntilIdle()

            // Assert
            verify(transactionRepository).updateTransactionType(transactionId, newType)
            // Ensure no error event was sent on success
            viewModel.uiEvent.test {
                expectNoEvents()
            }
        }

    @Test
    fun `updateTransactionType on repository failure sends uiEvent`() =
        runTest {
            // Arrange
            val transactionId = 1
            val newType = TransactionType.INCOME
            val errorMessage = "DB Error"
            whenever(transactionRepository.updateTransactionType(anyInt(), org.mockito.kotlin.any())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.updateTransactionType(transactionId, newType)
                advanceUntilIdle()
                assertEquals("Failed to update transaction type.", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onAddTransactionDescriptionChanged updates suggestion flow and resets manual select on blank`() =
        runTest {
            // Arrange
            val foodCategory = Category(1, "Food & Drinks", "icon", "color")
            whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
            initializeViewModel()

            viewModel.suggestedCategory.test(timeout = 5.seconds) { // Increase timeout for debounce
                assertNull("Initial suggestion should be null", awaitItem())

                // Act 1: Type a keyword
                viewModel.onAddTransactionDescriptionChanged("Coffee")
                advanceTimeBy(500) // Wait for debounce

                // Assert 1: Suggestion appears
                assertEquals("Food & Drinks", awaitItem()?.name)

                // Act 2: Manually select a category (simulated)
                viewModel.onUserManuallySelectedCategory()
                advanceUntilIdle()

                // Act 3: Clear description
                viewModel.onAddTransactionDescriptionChanged("")
                advanceTimeBy(500) // Wait for debounce

                // Assert 3: Suggestion becomes null
                assertEquals(null, awaitItem())

                // Act 4: Type keyword again to see if manual select was reset
                viewModel.onAddTransactionDescriptionChanged("Pizza")
                advanceTimeBy(500) // Wait for debounce

                // Assert 4: Suggestion should reappear, proving manual select was reset
                assertEquals("Food & Drinks", awaitItem()?.name)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearAddTransactionState resets all add-transaction states`() =
        runTest {
            // Arrange
            val foodCategory = Category(1, "Food & Drinks", "icon", "color")
            val testTag = Tag(1, "Test")
            whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
            initializeViewModel()

            // Set up a dirty state
            viewModel.onAddTransactionDescriptionChanged("Coffee")
            viewModel.onTagSelected(testTag)
            viewModel.onUserManuallySelectedCategory()
            advanceUntilIdle()

            // Pre-condition asserts
            assertEquals(setOf(testTag), viewModel.selectedTags.value)

            // Act
            viewModel.clearAddTransactionState()
            advanceUntilIdle()

            // Assert 1: Check cleared states
            assertTrue("Selected tags should be empty", viewModel.selectedTags.value.isEmpty())

            // Assert 2: Check if description and manual select were reset by testing suggestedCategory
            viewModel.suggestedCategory.test(timeout = 5.seconds) {
                assertNull("Suggestion should be null initially after clear", awaitItem())

                viewModel.onAddTransactionDescriptionChanged("Coffee")
                advanceTimeBy(500)

                assertEquals("Suggestion should appear, proving state was reset", "Food & Drinks", awaitItem()?.name)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saveTransactionSplits calls DAOs within transaction`() =
        runTest {
            // Arrange
            val transactionId = 1
            val parentTxn =
                Transaction(
                    id = transactionId,
                    description = "Parent",
                    amount = 100.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
            val category = Category(1, "Food", "icon", "color")
            val splitItems =
                listOf(
                    SplitItem(-1, "60.0", category, "Lunch"),
                    SplitItem(-2, "40.0", null, "Snacks"),
                )
            var onCompleteCalled = false

            whenever(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(parentTxn))

            // Mock the withTransaction block
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { any<AppDatabase>().withTransaction<Any?>(any()) } coAnswers {
                val block = secondArg<suspend () -> Any?>()
                block()
            }
            val listCaptor = argumentCaptor<List<SplitTransaction>>()

            // Act
            viewModel.saveTransactionSplits(transactionId, splitItems) { onCompleteCalled = true }
            advanceUntilIdle()

            // Assert
            coVerify { db.withTransaction<Unit>(any()) }
            verify(transactionDao).markAsSplit(transactionId, true)
            verify(splitTransactionDao).deleteSplitsForParent(transactionId)
            verify(splitTransactionDao).insertAll(listCaptor.capture())

            val capturedSplits = listCaptor.firstValue
            assertEquals(2, capturedSplits.size)
            assertEquals(60.0, capturedSplits[0].amount, 0.001)
            assertEquals(1, capturedSplits[0].categoryId)
            assertEquals("Lunch", capturedSplits[0].notes)
            assertEquals(40.0, capturedSplits[1].amount, 0.001)
            assertNull(capturedSplits[1].categoryId)

            assertTrue(onCompleteCalled)
            unmockkStatic("androidx.room.RoomDatabaseKt")
        }

    @Test
    fun `createAccount success case`() =
        runTest {
            // Arrange
            val newAccountName = "New Bank"
            val newAccountType = "Bank"
            val newAccount = Account(1, newAccountName, newAccountType)
            var createdAccount: Account? = null

            whenever(db.accountDao().findByName(newAccountName)).thenReturn(null)
            whenever(accountRepository.insert(Account(name = newAccountName, type = newAccountType))).thenReturn(1L)
            whenever(accountRepository.getAccountById(1)).thenReturn(flowOf(newAccount))

            // Act
            viewModel.createAccount(newAccountName, newAccountType) { createdAccount = it }
            advanceUntilIdle()

            // Assert
            verify(accountRepository).insert(Account(name = newAccountName, type = newAccountType))
            assertEquals(newAccount, createdAccount)
        }

    @Test
    fun `createAccount failure on duplicate name`() =
        runTest {
            // Arrange
            val existingAccountName = "Existing Bank"
            val existingAccount = Account(1, existingAccountName, "Bank")
            var createdAccount: Account? = null

            whenever(db.accountDao().findByName(existingAccountName)).thenReturn(existingAccount)

            // Act & Assert
            viewModel.validationError.test {
                assertNull(awaitItem()) // Initial null
                viewModel.createAccount(existingAccountName, "Bank") { createdAccount = it }
                advanceUntilIdle()

                assertEquals("An account named '$existingAccountName' already exists.", awaitItem())
                assertNull(createdAccount)
                verify(accountRepository, never()).insert(any())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createCategory success case`() =
        runTest {
            // Arrange
            val newCategoryName = "New Stuff"
            val newIcon = "icon"
            val newColor = "color"
            val newCategory = Category(1, newCategoryName, newIcon, newColor)
            var createdCategory: Category? = null

            whenever(db.categoryDao().findByName(newCategoryName)).thenReturn(null)
            whenever(categoryRepository.allCategories).thenReturn(flowOf(emptyList())) // For color helper
            whenever(categoryRepository.insert(Category(name = newCategoryName, iconKey = newIcon, colorKey = newColor))).thenReturn(1L)
            whenever(categoryRepository.getCategoryById(1)).thenReturn(newCategory)

            // Act
            viewModel.createCategory(newCategoryName, newIcon, newColor) { createdCategory = it }
            advanceUntilIdle()

            // Assert
            verify(categoryRepository).insert(Category(name = newCategoryName, iconKey = newIcon, colorKey = newColor))
            assertEquals(newCategory, createdCategory)
        }

    @Test
    fun `createCategory failure on duplicate name`() =
        runTest {
            // Arrange
            val existingCategoryName = "Food"
            val existingCategory = Category(1, existingCategoryName, "icon", "color")
            var createdCategory: Category? = null

            whenever(db.categoryDao().findByName(existingCategoryName)).thenReturn(existingCategory)

            // Act & Assert
            viewModel.validationError.test {
                assertNull(awaitItem()) // Initial null
                viewModel.createCategory(existingCategoryName, "icon", "color") { createdCategory = it }
                advanceUntilIdle()

                assertEquals("A category named '$existingCategoryName' already exists.", awaitItem())
                assertNull(createdCategory)
                verify(categoryRepository, never()).insert(any())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearError resets validationError flow`() =
        runTest {
            // Act & Assert
            viewModel.validationError.test {
                assertNull("Initial state should be null", awaitItem())

                // Manually trigger an error to set the state
                viewModel.onSaveTapped("Test", "0.0", 1, 1, null, 0L, "expense", emptyList()) {}
                advanceUntilIdle()

                assertEquals("Error should be set", "Please enter a valid, positive amount.", awaitItem())

                // Act: Call clearError
                viewModel.clearError()

                assertNull("Error should be cleared to null", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- NEW TEST ---

    @Test
    fun `onQuickFillSelected populates state correctly`() =
        runTest {
            // Arrange - Setup specific mocks for this test
            whenever(accountRepository.allAccounts).thenReturn(
                flowOf(
                    listOf(
                        Account(id = 1, name = "Cash", type = "Cash"),
                        Account(id = 2, name = "Bank", type = "Bank"),
                    ),
                ),
            )
            whenever(categoryRepository.allCategories).thenReturn(
                flowOf(
                    listOf(
                        Category(id = 10, name = "Food", iconKey = "food", colorKey = "green"),
                        Category(id = 11, name = "Transport", iconKey = "car", colorKey = "blue"),
                    ),
                ),
            )

            // Re-initialize ViewModel to pick up the new flows
            initializeViewModel()

            // FIX: Start collecting `allAccounts` to trigger the `stateIn(WhileSubscribed)` upstream flow.
            // Without this, `allAccounts.first()` inside the ViewModel returns the initial `emptyList()`
            // because the StateFlow hasn't connected to the repository flow yet.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allAccounts.collect { }
            }

            // Ensure values are propagated
            advanceUntilIdle()

            val mockTransaction =
                Transaction(
                    id = 100,
                    description = "Quick Lunch",
                    // Has decimals
                    amount = 120.50,
                    date = System.currentTimeMillis(),
                    // "Cash"
                    accountId = 1,
                    // "Food"
                    categoryId = 10,
                    source = "Manual Entry",
                    transactionType = TransactionType.EXPENSE,
                    notes = null,
                )
            val mockDetails =
                TransactionDetails(
                    transaction = mockTransaction,
                    images = emptyList(),
                    accountName = "Cash",
                    categoryName = "Food",
                    categoryIconKey = "food",
                    categoryColorKey = "green",
                    tagNames = null,
                )

            // Mock getting tags for this transaction (e.g., return empty list)
            whenever(transactionRepository.getTagsForTransactionSimple(100)).thenReturn(emptyList())

            // Act
            viewModel.onQuickFillSelected(mockDetails)

            // Advance time to allow coroutines launched within the VM to complete
            testScheduler.advanceUntilIdle()

            // Assert
            assertEquals("Description should match", "Quick Lunch", viewModel.addTransactionDescription.value)
            assertEquals("Amount should be formatted to 2 decimals", "120.50", viewModel.addTransactionAmount.value)
            assertEquals("Category ID should match", 10, viewModel.addTransactionCategory.value?.id)
            assertEquals("Category Name should match", "Food", viewModel.addTransactionCategory.value?.name)
            assertEquals("Account ID should match", 1, viewModel.addTransactionAccount.value?.id)
            assertEquals("Account Name should match", "Cash", viewModel.addTransactionAccount.value?.name)
        }

    @Test
    fun `createAccount validation fails for blank name or type`() =
        runTest {
            // Act
            viewModel.createAccount("", "Bank") { fail("Should not be called") }
            viewModel.createAccount("Name", "") { fail("Should not be called") }
            advanceUntilIdle()

            // Assert
            verify(accountRepository, never()).insert(any())
        }

    @Test
    fun `createAccount fails for existing account name`() =
        runTest {
            // Arrange
            whenever(db.accountDao().findByName("Savings")).thenReturn(Account(1, "Savings", "Bank"))

            // Act
            viewModel.createAccount("Savings", "Bank") { fail("Should not be called") }
            advanceUntilIdle()

            // Assert
            viewModel.validationError.test {
                assertEquals("An account named 'Savings' already exists.", awaitItem())
            }
        }

    @Test
    fun `createCategory validation fails for blank name`() =
        runTest {
            // Act
            viewModel.createCategory("", "icon", "color") { fail("Should not be called") }
            advanceUntilIdle()

            // Assert
            verify(categoryRepository, never()).insert(any())
        }

    @Test
    fun `onAddTransactionAmountChanged updates addTransactionAmount state`() =
        runTest {
            // Act & Assert
            viewModel.addTransactionAmount.test {
                assertEquals("", awaitItem()) // initial

                viewModel.onAddTransactionAmountChanged("250.75")
                assertEquals("250.75", awaitItem())

                viewModel.onAddTransactionAmountChanged("")
                assertEquals("", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onAddTransactionCategoryChanged with null category does NOT set userManuallySelected`() =
        runTest {
            // Arrange: set manual flag to false explicitly, then call with null
            viewModel.onAddTransactionCategoryChanged(null)
            advanceUntilIdle()

            // Assert: category is null, manual flag remains false (so suggestedCategory still works)
            assertNull(viewModel.addTransactionCategory.value)

            // Verify the auto-suggest still works (manual flag not set to true)
            val foodCategory = Category(1, "Food & Drinks", "icon", "color")
            whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
            initializeViewModel()

            viewModel.suggestedCategory.test(timeout = 5.seconds) {
                assertNull(awaitItem())
                viewModel.onAddTransactionDescriptionChanged("Coffee")
                advanceTimeBy(500)
                assertEquals("Food & Drinks", awaitItem()?.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onAddTransactionCategoryChanged with non-null category sets userManuallySelected to true`() =
        runTest {
            // Arrange
            val foodCategory = Category(1, "Food & Drinks", "food_icon", "green")
            whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
            initializeViewModel()

            // Act: set a category
            viewModel.onAddTransactionCategoryChanged(foodCategory)
            advanceUntilIdle()

            // Assert category is set
            assertEquals(foodCategory, viewModel.addTransactionCategory.value)

            // Assert manual flag is true by verifying suggestions stop
            viewModel.suggestedCategory.test(timeout = 5.seconds) {
                assertNull(awaitItem())
                viewModel.onAddTransactionDescriptionChanged("Pizza")
                advanceTimeBy(500)
                // Should remain null because manual flag is true.
                // StateFlow does not emit duplicate nulls, so expect no events.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onAddTransactionAccountChanged updates addTransactionAccount state`() =
        runTest {
            // Arrange
            val account = Account(id = 5, name = "Savings", type = "Bank")

            // Act & Assert
            viewModel.addTransactionAccount.test {
                assertNull(awaitItem()) // initial

                viewModel.onAddTransactionAccountChanged(account)
                assertEquals(account, awaitItem())

                viewModel.onAddTransactionAccountChanged(null)
                assertNull(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onQuickFillSelected formats integer amount without decimals`() =
        runTest {
            // Arrange
            whenever(accountRepository.allAccounts).thenReturn(
                flowOf(
                    listOf(
                        Account(id = 1, name = "Cash", type = "Cash"),
                    ),
                ),
            )
            whenever(categoryRepository.allCategories).thenReturn(
                flowOf(
                    listOf(
                        Category(id = 10, name = "Food", iconKey = "food", colorKey = "green"),
                    ),
                ),
            )
            initializeViewModel()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allAccounts.collect { }
            }
            advanceUntilIdle()

            // Transaction with a whole number amount (e.g. 100.0)
            val mockTransaction =
                Transaction(
                    id = 200,
                    description = "ATM Withdrawal",
                    // Integer amount
                    amount = 500.0,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = 10,
                    source = "Manual Entry",
                    transactionType = TransactionType.EXPENSE,
                    notes = null,
                )
            val mockDetails =
                TransactionDetails(
                    transaction = mockTransaction,
                    images = emptyList(),
                    accountName = "Cash",
                    categoryName = "Food",
                    categoryIconKey = "food",
                    categoryColorKey = "green",
                    tagNames = null,
                )
            whenever(transactionRepository.getTagsForTransactionSimple(200)).thenReturn(emptyList())

            // Act
            viewModel.onQuickFillSelected(mockDetails)
            testScheduler.advanceUntilIdle()

            // Assert: integer amount should be formatted without decimals
            assertEquals("500", viewModel.addTransactionAmount.value)
            assertEquals("ATM Withdrawal", viewModel.addTransactionDescription.value)
        }

    @Test
    fun `clearAddTransactionState resets account to null when not matching default`() =
        runTest {
            // Arrange: set a non-default account
            val nonDefaultAccount = Account(id = 99, name = "Non-default", type = "Bank")
            viewModel.onAddTransactionAccountChanged(nonDefaultAccount)
            advanceUntilIdle()
            assertEquals(nonDefaultAccount, viewModel.addTransactionAccount.value)

            // Act
            viewModel.clearAddTransactionState()
            advanceUntilIdle()

            // Assert: account is cleared to null (since it doesn't match defaultAccount which is null)
            assertNull(viewModel.addTransactionAccount.value)
        }

    @Test
    fun `clearAddTransactionState does not reset account when it equals default account`() =
        runTest {
            // Arrange: simulate a default account being loaded by mocking accountDao
            val defaultAccount = Account(id = 1, name = "Cash Spends", type = "Cash")
            whenever(db.accountDao().findByName("Cash Spends")).thenReturn(defaultAccount)
            initializeViewModel()
            advanceUntilIdle()

            // Set the account to the same as default
            viewModel.onAddTransactionAccountChanged(defaultAccount)
            advanceUntilIdle()

            // Act
            viewModel.clearAddTransactionState()
            advanceUntilIdle()

            // Assert: account is NOT cleared because it matches the default (the if-branch is false)
            // The _addTransactionAccount should NOT be set to null
            assertEquals(defaultAccount, viewModel.addTransactionAccount.value)
        }

    // --- NEW: applyAliases Tests via findTransactionDetailsById ---

    @Test
    fun `applyAliases does nothing when alias is null`() =
        runTest {
            // Arrange
            val aliases = emptyMap<String, String>()
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

            val transaction =
                Transaction(
                    id = 1,
                    description = "Gateway",
                    originalDescription = "Gateway",
                    amount = 1.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
            val details = TransactionDetails(transaction, emptyList(), "Account", "Category", "icon", "color", null)
            whenever(transactionRepository.getTransactionDetailsById(1)).thenReturn(flowOf(details))

            initializeViewModel()
            advanceUntilIdle()

            // Act & Assert
            viewModel.findTransactionDetailsById(1).test {
                val result = awaitItem()
                assertNotNull(result)
                assertEquals("Gateway", result!!.transaction.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `applyAliases applies alias when description equals originalDescription`() =
        runTest {
            // Arrange
            val aliases = mapOf("gateway" to "Water Charges")
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

            val transaction =
                Transaction(
                    id = 1,
                    description = "Gateway",
                    originalDescription = "Gateway",
                    amount = 1.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
            val details = TransactionDetails(transaction, emptyList(), "Account", "Category", "icon", "color", null)
            whenever(transactionRepository.getTransactionDetailsById(1)).thenReturn(flowOf(details))

            initializeViewModel()
            advanceUntilIdle()

            // Act & Assert
            viewModel.findTransactionDetailsById(1).test {
                val result = awaitItem()
                assertEquals("Water Charges", result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `applyAliases applies alias when description equals alias`() =
        runTest {
            // Arrange
            val aliases = mapOf("gateway" to "Water Charges")
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

            // Description is already the alias, meaning it was applied by SmsParser
            val transaction =
                Transaction(
                    id = 1,
                    description = "Water Charges",
                    originalDescription = "Gateway",
                    amount = 1.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
            val details = TransactionDetails(transaction, emptyList(), "Account", "Category", "icon", "color", null)
            whenever(transactionRepository.getTransactionDetailsById(1)).thenReturn(flowOf(details))

            initializeViewModel()
            advanceUntilIdle()

            // Act & Assert
            viewModel.findTransactionDetailsById(1).test {
                val result = awaitItem()
                assertEquals("Water Charges", result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `applyAliases preserves description when it differs from both originalDescription and alias`() =
        runTest {
            // Arrange
            val aliases = mapOf("gateway" to "Water Charges")
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

            // Description is DIFFERENT from original ("Gateway") AND alias ("Water Charges")
            val transaction =
                Transaction(
                    id = 1,
                    description = "Gas Bill",
                    originalDescription = "Gateway",
                    amount = 1.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
            val details = TransactionDetails(transaction, emptyList(), "Account", "Category", "icon", "color", null)
            whenever(transactionRepository.getTransactionDetailsById(1)).thenReturn(flowOf(details))

            initializeViewModel()
            advanceUntilIdle()

            // Act & Assert
            viewModel.findTransactionDetailsById(1).test {
                val result = awaitItem()
                assertEquals("Gas Bill", result?.transaction?.description) // Keep manual exception
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // --- NEW TESTS: Silent Rule Persistence (Bug Fix 1) ---
    // Covers the onAttemptToLeaveScreen else-branch that was previously a no-op.
    // =========================================================================

    /**
     * Helper to set up a transaction whose description has been changed and has no
     * similar transactions in the database, so the "else" branch in
     * onAttemptToLeaveScreen is exercised.
     */

    @Test
    fun `createCategory with default icon and color keys applies normalization`() =
        runTest {
            // Arrange
            // When iconKey == "category" it is normalized to "letter_default"
            // When colorKey == "gray_light" it is replaced by getNextAvailableColor()
            val newCategoryName = "Hobbies"
            val defaultIconKey = "category"
            val defaultColorKey = "gray_light"
            val resolvedIconKey = "letter_default" // expected after normalization
            // CategoryIconHelper.getNextAvailableColor([]) returns the first color
            // We only need to verify insert was called with the resolved icon key.
            val createdCategory = Category(1, newCategoryName, resolvedIconKey, "blue_light")
            var callbackResult: Category? = null

            whenever(db.categoryDao().findByName(newCategoryName)).thenReturn(null)
            whenever(categoryRepository.allCategories).thenReturn(flowOf(emptyList<Category>()))
            whenever(categoryRepository.insert(any<Category>())).thenReturn(1L)
            whenever(categoryRepository.getCategoryById(1)).thenReturn(createdCategory)

            // Act
            viewModel.createCategory(newCategoryName, defaultIconKey, defaultColorKey) { callbackResult = it }
            advanceUntilIdle()

            // Assert — icon key must have been normalized from "category" to "letter_default"
            val captor = argumentCaptor<Category>()
            verify(categoryRepository).insert(captor.capture())
            assertEquals(resolvedIconKey, captor.firstValue.iconKey)
            // color key must have been replaced (anything other than "gray_light")
            assertTrue("Color should have been resolved", captor.firstValue.colorKey != defaultColorKey)
            assertEquals(createdCategory, callbackResult)
        }
}
