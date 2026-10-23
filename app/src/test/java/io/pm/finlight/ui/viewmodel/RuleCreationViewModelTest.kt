package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.PotentialAccount
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionType
import io.pm.finlight.capture
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RuleCreationViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var customSmsRuleRepository: ICustomSmsRuleRepository

    @Captor
    private lateinit var ruleCaptor: ArgumentCaptor<CustomSmsRule>

    private lateinit var viewModel: RuleCreationViewModel
    private lateinit var testDispatcherProvider: TestDispatcherProvider

    @Before
    override fun setup() {
        super.setup()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        viewModel = RuleCreationViewModel(customSmsRuleRepository, testDispatcherProvider)
    }

    @Test
    fun `initializeStateForCreation correctly pre-populates state from PotentialTransaction`() =
        runTest {
            // Arrange
            val potentialTxn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = "Test",
                    amount = 123.45,
                    transactionType = "expense",
                    merchantName = "Test Merchant",
                    originalMessage = "Paid 123.45 to Test Merchant with HDFC Card xx9876.",
                    potentialAccount = PotentialAccount("HDFC Card xx9876", "Card"),
                )

            // Act
            viewModel.initializeStateForCreation(potentialTxn)

            // Assert
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals("123.45", state.amountSelection.selectedText)
                assertEquals("Test Merchant", state.merchantSelection.selectedText)
                assertEquals("xx9876", state.accountSelection.selectedText)
                assertEquals(TransactionType.EXPENSE, state.transactionType)
                assertEquals("Paid 123.45 to Test Merchant with HDFC Card xx9876.", state.sourceSmsBody)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadRuleForEditing correctly populates state from existing rule`() =
        runTest {
            // Arrange
            val ruleId = 1
            val existingRule =
                CustomSmsRule(
                    id = ruleId,
                    triggerPhrase = "spent on card",
                    merchantRegex = null,
                    amountRegex = null,
                    accountRegex = null,
                    merchantNameExample = "Amazon",
                    amountExample = "500.00",
                    accountNameExample = "xx1234",
                    priority = 10,
                    sourceSmsBody = "some body",
                )
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(flowOf(existingRule))

            // Act
            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()

            // Assert
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(ruleId, state.ruleIdToEdit)
                assertEquals("spent on card", state.triggerSelection.selectedText)
                assertEquals("Amazon", state.merchantSelection.selectedText)
                assertEquals("500.00", state.amountSelection.selectedText)
                assertEquals("xx1234", state.accountSelection.selectedText)
                assertEquals("some body", state.sourceSmsBody)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadRuleForEditing sets errorMessage on repository exception`() =
        runTest {
            // Arrange
            val ruleId = 99
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(
                kotlinx.coroutines.flow.flow { throw RuntimeException("Database read error") },
            )

            // Act
            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()

            // Assert
            assertEquals("Database read error", viewModel.uiState.value.errorMessage)
            viewModel.clearError()
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `loadRuleForEditing rethrows CancellationException without setting errorMessage`() =
        runTest {
            val ruleId = 1
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenAnswer {
                kotlinx.coroutines.flow.flow<CustomSmsRule> {
                    throw kotlinx.coroutines.CancellationException("Cancelled")
                }
            }

            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `saveRule calls insert for new rule`() =
        runTest {
            // Arrange
            var onCompleteCalled = false
            val smsBody = "Transaction of 100 at Merchant X"
            val triggerSelection = RuleSelection("Transaction of", 0, 14)
            val merchantSelection = RuleSelection("Merchant X", 20, 30)

            // Act
            viewModel.onMarkAsTrigger(triggerSelection)
            viewModel.onMarkAsMerchant(merchantSelection)
            viewModel.saveRule(smsBody) { onCompleteCalled = true }
            advanceUntilIdle()

            // Assert
            verify(customSmsRuleRepository).insert(capture(ruleCaptor))
            val capturedRule = ruleCaptor.value
            assertEquals(0, capturedRule.id)
            assertEquals(triggerSelection.selectedText, capturedRule.triggerPhrase)
            assertEquals(merchantSelection.selectedText, capturedRule.merchantNameExample)
            assertNotNull(capturedRule.merchantRegex)
            assertEquals(true, onCompleteCalled)
        }

    @Test
    fun `saveRule does not insert and completes when trigger phrase is blank`() =
        runTest {
            // Arrange
            var onCompleteCalled = false
            val smsBody = "Transaction of 100 at Merchant X"

            // Act (no trigger selection set)
            viewModel.saveRule(smsBody) { onCompleteCalled = true }
            advanceUntilIdle()

            // Assert
            verify(customSmsRuleRepository, never()).insert(io.pm.finlight.anyObject())
            verify(customSmsRuleRepository, never()).update(io.pm.finlight.anyObject())
            assertEquals(true, onCompleteCalled)
        }

    @Test
    fun `saveRule sets errorMessage when insert throws exception`() =
        runTest {
            // Arrange
            val smsBody = "Transaction of 100 at Merchant X"
            val triggerSelection = RuleSelection("Transaction of", 0, 14)
            viewModel.onMarkAsTrigger(triggerSelection)

            doThrow(RuntimeException("Write error")).`when`(customSmsRuleRepository).insert(io.pm.finlight.anyObject())

            // Act
            viewModel.saveRule(smsBody) {}
            advanceUntilIdle()

            // Assert
            assertEquals("Write error", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `saveRule rethrows CancellationException without setting errorMessage`() =
        runTest {
            val smsBody = "Transaction of 100 at Merchant X"
            val triggerSelection = RuleSelection("Transaction of", 0, 14)
            viewModel.onMarkAsTrigger(triggerSelection)

            doThrow(kotlinx.coroutines.CancellationException("Cancelled"))
                .`when`(customSmsRuleRepository).insert(io.pm.finlight.anyObject())

            viewModel.saveRule(smsBody) {}
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `saveRule calls update for existing rule`() =
        runTest {
            // Arrange
            var onCompleteCalled = false
            val ruleId = 5
            val smsBody = "Transaction of 100 at Merchant X"
            val triggerSelection = RuleSelection("Transaction of", 0, 14)
            val merchantSelection = RuleSelection("Merchant Y", 20, 30) // Updated merchant

            val existingRule = CustomSmsRule(ruleId, "old trigger", null, null, null, null, null, null, 10, smsBody)
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(flowOf(existingRule))
            viewModel.loadRuleForEditing(ruleId)
            viewModel.uiState.test { awaitItem() } // consume initial load
            advanceUntilIdle()

            // Act
            viewModel.onMarkAsTrigger(triggerSelection)
            viewModel.onMarkAsMerchant(merchantSelection)
            viewModel.saveRule(smsBody) { onCompleteCalled = true }
            advanceUntilIdle()

            // Assert
            verify(customSmsRuleRepository).update(capture(ruleCaptor))
            val capturedRule = ruleCaptor.value
            assertEquals(ruleId, capturedRule.id)
            assertEquals(triggerSelection.selectedText, capturedRule.triggerPhrase)
            assertEquals(merchantSelection.selectedText, capturedRule.merchantNameExample)
            assertEquals(true, onCompleteCalled)
        }

    @Test
    fun `onMarkAsTrigger updates triggerSelection in uiState`() =
        runTest {
            // Arrange
            val selection = RuleSelection("Trigger", 0, 7)

            // Act
            viewModel.onMarkAsTrigger(selection)

            // Assert
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(selection, state.triggerSelection)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onMarkAsMerchant updates merchantSelection in uiState`() =
        runTest {
            // Arrange
            val selection = RuleSelection("Merchant", 8, 16)

            // Act
            viewModel.onMarkAsMerchant(selection)

            // Assert
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(selection, state.merchantSelection)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onMarkAsAmount updates amountSelection in uiState`() =
        runTest {
            // Arrange
            val selection = RuleSelection("100.00", 17, 23)

            // Act
            viewModel.onMarkAsAmount(selection)

            // Assert
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(selection, state.amountSelection)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onMarkAsAccount updates accountSelection in uiState`() =
        runTest {
            // Arrange
            val selection = RuleSelection("xx1234", 24, 30)

            // Act
            viewModel.onMarkAsAccount(selection)

            // Assert
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(selection, state.accountSelection)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onTransactionTypeChanged updates transactionType in uiState`() =
        runTest {
            viewModel.uiState.test {
                val initialState = awaitItem()
                assertNull(initialState.transactionType)

                viewModel.onTransactionTypeChanged(TransactionType.INCOME)
                assertEquals(TransactionType.INCOME, awaitItem().transactionType)

                viewModel.onTransactionTypeChanged(TransactionType.EXPENSE)
                assertEquals(TransactionType.EXPENSE, awaitItem().transactionType)

                viewModel.onTransactionTypeChanged(null)
                assertNull(awaitItem().transactionType)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadRuleForEditing correctly populates transactionType`() =
        runTest {
            // Arrange
            val ruleId = 1
            val existingRule =
                CustomSmsRule(
                    id = ruleId,
                    triggerPhrase = "test",
                    transactionType = "income",
                    priority = 10,
                    sourceSmsBody = "some body",
                    merchantRegex = "regex1",
                    amountRegex = "regex2",
                    accountRegex = "regex3",
                    merchantNameExample = null,
                    amountExample = null,
                    accountNameExample = null,
                )
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(flowOf(existingRule))

            // Act
            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()

            // Assert
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(TransactionType.INCOME, state.transactionType)
                assertEquals("regex1", state.merchantRegex)
                assertEquals("regex2", state.amountRegex)
                assertEquals("regex3", state.accountRegex)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saveRule correctly saves transactionType`() =
        runTest {
            // Arrange
            var onCompleteCalled = false
            val smsBody = "Transaction of 100 at Merchant X"
            val triggerSelection = RuleSelection("Transaction of", 0, 14)
            val merchantSelection = RuleSelection("Merchant X", 20, 30)

            // Act
            viewModel.onMarkAsTrigger(triggerSelection)
            viewModel.onMarkAsMerchant(merchantSelection)
            viewModel.onTransactionTypeChanged(TransactionType.INCOME)
            viewModel.saveRule(smsBody) { onCompleteCalled = true }
            advanceUntilIdle()

            // Assert
            verify(customSmsRuleRepository).insert(capture(ruleCaptor))
            val capturedRule = ruleCaptor.value
            assertEquals("income", capturedRule.transactionType)
            assertEquals(true, onCompleteCalled)
        }

    @Test
    fun `saveRule preserves existing regex fields on edit`() =
        runTest {
            // ARRANGE: This is the critical bugfix test
            var onCompleteCalled = false
            val ruleId = 5
            val smsBody = "Transaction of 100 at Merchant X"
            val existingRule =
                CustomSmsRule(
                    id = ruleId,
                    triggerPhrase = "Transaction of",
                    merchantRegex = "old-merchant-regex",
                    amountRegex = "old-amount-regex",
                    accountRegex = "old-account-regex",
                    merchantNameExample = "Old Merchant",
                    amountExample = "100",
                    accountNameExample = "xx1234",
                    priority = 10,
                    sourceSmsBody = smsBody,
                    transactionType = null,
                )
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(flowOf(existingRule))

            // 1. Load the existing rule
            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()
            viewModel.uiState.test { awaitItem() } // consume initial load

            // 2. Make *only* one change to the transaction type
            viewModel.onTransactionTypeChanged(TransactionType.INCOME)
            advanceUntilIdle()

            // 3. Save the rule. No new selections were made, so all startIndexes are -1
            // ACT
            viewModel.saveRule(smsBody) { onCompleteCalled = true }
            advanceUntilIdle()

            // ASSERT
            verify(customSmsRuleRepository).update(capture(ruleCaptor))
            val updatedRule = ruleCaptor.value

            assertEquals(ruleId, updatedRule.id)
            assertEquals("income", updatedRule.transactionType)
            assertEquals("old-merchant-regex", updatedRule.merchantRegex)
            assertEquals("old-amount-regex", updatedRule.amountRegex)
            assertEquals("old-account-regex", updatedRule.accountRegex)
            assertEquals("Old Merchant", updatedRule.merchantNameExample)
            assertEquals(true, onCompleteCalled)
        }
}
