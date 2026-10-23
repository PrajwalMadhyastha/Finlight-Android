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
import java.util.Locale
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
    fun `initializeStateForCreation correctly matches dot amount even when default locale uses comma separator`() =
        runTest {
            val originalLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.GERMANY)
                val potentialTxn =
                    PotentialTransaction(
                        sourceSmsId = 1L,
                        smsSender = "Test",
                        amount = 123.45,
                        transactionType = "expense",
                        merchantName = "Test Merchant",
                        originalMessage = "Paid 123.45 to Test Merchant.",
                        potentialAccount = null,
                    )

                viewModel.initializeStateForCreation(potentialTxn)

                viewModel.uiState.test {
                    val state = awaitItem()
                    assertEquals("123.45", state.amountSelection.selectedText)
                    assertEquals(5, state.amountSelection.startIndex)
                    assertEquals(11, state.amountSelection.endIndex)
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                Locale.setDefault(originalLocale)
            }
        }

    @Test
    fun `initializeStateForCreation correctly matches comma amount when SMS contains comma decimal`() =
        runTest {
            val potentialTxn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = "Test",
                    amount = 123.45,
                    transactionType = "expense",
                    merchantName = "Test Merchant",
                    originalMessage = "Paid 123,45 to Test Merchant.",
                    potentialAccount = null,
                )

            viewModel.initializeStateForCreation(potentialTxn)

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals("123,45", state.amountSelection.selectedText)
                assertEquals(5, state.amountSelection.startIndex)
                assertEquals(11, state.amountSelection.endIndex)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initializeStateForCreation handles amount not found in SMS message`() =
        runTest {
            val potentialTxn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = "Test",
                    amount = 999.99,
                    transactionType = "expense",
                    merchantName = "Test Merchant",
                    originalMessage = "Paid some amount to Test Merchant.",
                    potentialAccount = null,
                )

            viewModel.initializeStateForCreation(potentialTxn)

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals("", state.amountSelection.selectedText)
                assertEquals(-1, state.amountSelection.startIndex)
                assertEquals(-1, state.amountSelection.endIndex)
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
    fun `saveRule does not insert and sets error when trigger phrase is blank`() =
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
            assertEquals(false, onCompleteCalled)
            assertEquals("Trigger phrase cannot be blank", viewModel.uiState.value.errorMessage)
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

    @Test
    fun `initializeStateForCreation fallback when amount is not found in message`() =
        runTest {
            val potentialTxn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = "Test",
                    amount = 99.99,
                    transactionType = "expense",
                    merchantName = "Store",
                    originalMessage = "Paid ninety nine dollars at Store",
                    potentialAccount = null,
                )

            viewModel.initializeStateForCreation(potentialTxn)

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals("", state.amountSelection.selectedText)
                assertEquals(-1, state.amountSelection.startIndex)
                assertEquals(-1, state.amountSelection.endIndex)
                assertEquals("Store", state.merchantSelection.selectedText)
                assertEquals("", state.accountSelection.selectedText)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initializeStateForCreation fallback when merchant is null or not found in message`() =
        runTest {
            val potentialTxnNullMerchant =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = "Test",
                    amount = 50.0,
                    transactionType = "expense",
                    merchantName = null,
                    originalMessage = "Paid 50.00 cash",
                    potentialAccount = null,
                )

            viewModel.initializeStateForCreation(potentialTxnNullMerchant)
            assertEquals("", viewModel.uiState.value.merchantSelection.selectedText)
            assertEquals(-1, viewModel.uiState.value.merchantSelection.startIndex)

            val potentialTxnNotFoundMerchant =
                PotentialTransaction(
                    sourceSmsId = 2L,
                    smsSender = "Test",
                    amount = 50.0,
                    transactionType = "expense",
                    merchantName = "Target",
                    originalMessage = "Paid 50.00 at Walmart",
                    potentialAccount = null,
                )

            viewModel.initializeStateForCreation(potentialTxnNotFoundMerchant)
            assertEquals("", viewModel.uiState.value.merchantSelection.selectedText)
            assertEquals(-1, viewModel.uiState.value.merchantSelection.startIndex)
        }

    @Test
    fun `initializeStateForCreation fallback when account is null, has no digits, or not found`() =
        runTest {
            val txnNullAccount =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = "Test",
                    amount = 10.0,
                    transactionType = "expense",
                    merchantName = "Store",
                    originalMessage = "Paid 10.00 at Store",
                    potentialAccount = null,
                )
            viewModel.initializeStateForCreation(txnNullAccount)
            assertEquals("", viewModel.uiState.value.accountSelection.selectedText)
            assertEquals(-1, viewModel.uiState.value.accountSelection.startIndex)

            val txnAccountNoDigits =
                PotentialTransaction(
                    sourceSmsId = 2L,
                    smsSender = "Test",
                    amount = 10.0,
                    transactionType = "expense",
                    merchantName = "Store",
                    originalMessage = "Paid 10.00 at Store via Cash Wallet",
                    potentialAccount = PotentialAccount("Cash Wallet", "Cash"),
                )
            viewModel.initializeStateForCreation(txnAccountNoDigits)
            assertEquals("", viewModel.uiState.value.accountSelection.selectedText)
            assertEquals(-1, viewModel.uiState.value.accountSelection.startIndex)

            val txnAccountNotFound =
                PotentialTransaction(
                    sourceSmsId = 3L,
                    smsSender = "Test",
                    amount = 10.0,
                    transactionType = "expense",
                    merchantName = "Store",
                    originalMessage = "Paid 10.00 at Store",
                    potentialAccount = PotentialAccount("HDFC Card xx9999", "Card"),
                )
            viewModel.initializeStateForCreation(txnAccountNotFound)
            assertEquals("", viewModel.uiState.value.accountSelection.selectedText)
            assertEquals(-1, viewModel.uiState.value.accountSelection.startIndex)
        }

    @Test
    fun `loadRuleForEditing sets error message when rule is not found`() =
        runTest {
            val ruleId = 999
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(kotlinx.coroutines.flow.emptyFlow())

            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.ruleIdToEdit)
            assertEquals("Rule not found", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `loadRuleForEditing sets error message when repository emits null rule`() =
        runTest {
            val ruleId = 999
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(kotlinx.coroutines.flow.flowOf(null))

            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.ruleIdToEdit)
            assertEquals("Rule not found", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `loadRuleForEditing sets default message when exception message is null`() =
        runTest {
            val ruleId = 99
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(
                kotlinx.coroutines.flow.flow { throw RuntimeException(null as String?) },
            )

            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()

            assertEquals("Failed to load rule", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `saveRule generates regexes for amount and account selections`() =
        runTest {
            var onCompleteCalled = false
            val smsBody = "Paid 150.00 at Store from Account 9876"
            viewModel.onMarkAsTrigger(RuleSelection("Paid", 0, 4))
            viewModel.onMarkAsAmount(RuleSelection("150.00", 5, 11))
            viewModel.onMarkAsMerchant(RuleSelection("Store", 15, 20))
            viewModel.onMarkAsAccount(RuleSelection("9876", 34, 38))

            viewModel.saveRule(smsBody) { onCompleteCalled = true }
            advanceUntilIdle()

            verify(customSmsRuleRepository).insert(capture(ruleCaptor))
            val rule = ruleCaptor.value
            assertNotNull(rule.amountRegex)
            assertNotNull(rule.accountRegex)
            assertNotNull(rule.merchantRegex)
            assertEquals("150.00", rule.amountExample)
            assertEquals("Store", rule.merchantNameExample)
            assertEquals("9876", rule.accountNameExample)
            assertEquals(10, rule.priority)
            assertEquals(true, onCompleteCalled)
        }

    @Test
    fun `saveRule sets example fields to null when selections are blank`() =
        runTest {
            var onCompleteCalled = false
            val smsBody = "Alert triggered"
            viewModel.onMarkAsTrigger(RuleSelection("Alert", 0, 5))
            viewModel.onMarkAsAmount(RuleSelection("", -1, -1))
            viewModel.onMarkAsMerchant(RuleSelection("   ", -1, -1))
            viewModel.onMarkAsAccount(RuleSelection("", -1, -1))

            viewModel.saveRule(smsBody) { onCompleteCalled = true }
            advanceUntilIdle()

            verify(customSmsRuleRepository).insert(capture(ruleCaptor))
            val rule = ruleCaptor.value
            assertNull(rule.amountExample)
            assertNull(rule.merchantNameExample)
            assertNull(rule.accountNameExample)
            assertEquals(true, onCompleteCalled)
        }

    @Test
    fun `saveRule preserves custom priority when editing rule`() =
        runTest {
            var onCompleteCalled = false
            val ruleId = 42
            val existingRule =
                CustomSmsRule(
                    id = ruleId,
                    triggerPhrase = "Paid",
                    merchantRegex = null,
                    amountRegex = null,
                    accountRegex = null,
                    merchantNameExample = null,
                    amountExample = null,
                    accountNameExample = null,
                    priority = 25,
                    sourceSmsBody = "Paid 10 at Shop",
                )
            `when`(customSmsRuleRepository.getRuleById(ruleId)).thenReturn(flowOf(existingRule))

            viewModel.loadRuleForEditing(ruleId)
            advanceUntilIdle()

            viewModel.saveRule("Paid 10 at Shop") { onCompleteCalled = true }
            advanceUntilIdle()

            verify(customSmsRuleRepository).update(capture(ruleCaptor))
            val updatedRule = ruleCaptor.value
            assertEquals(ruleId, updatedRule.id)
            assertEquals(25, updatedRule.priority)
            assertEquals(true, onCompleteCalled)
        }

    @Test
    fun `saveRule sets default message when write exception has null message`() =
        runTest {
            val smsBody = "Paid 10 at Shop"
            viewModel.onMarkAsTrigger(RuleSelection("Paid", 0, 4))
            doThrow(RuntimeException(null as String?)).`when`(customSmsRuleRepository).insert(io.pm.finlight.anyObject())

            viewModel.saveRule(smsBody) {}
            advanceUntilIdle()

            assertEquals("Failed to save rule", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `generateRegex returns null for invalid or blank selection`() {
        assertNull(viewModel.generateRegex("Full text", RuleSelection("", -1, -1)))
        assertNull(viewModel.generateRegex("Full text", RuleSelection("   ", 0, 3)))
        assertNull(viewModel.generateRegex("Short", RuleSelection("test", 10, 15)))
        assertNull(viewModel.generateRegex("Short", RuleSelection("test", 2, 10)))
        assertNull(viewModel.generateRegex("Short", RuleSelection("test", 4, 2)))
        assertNull(viewModel.generateRegex("Short", RuleSelection("test", -2, 2)))
    }

    @Test
    fun `generateRegex returns prefix regex when token is at end of text`() {
        val text = "Total spent was 100"
        val selection = RuleSelection("100", 16, 19)
        val regex = viewModel.generateRegex(text, selection)
        assertNotNull(regex)
        assertEquals("\\Qspent was\\E\\s*(.*)", regex)
    }

    @Test
    fun `generateRegex returns suffix regex when token is at start of text`() {
        val text = "100 was debited"
        val selection = RuleSelection("100", 0, 3)
        val regex = viewModel.generateRegex(text, selection)
        assertNotNull(regex)
        assertEquals("(.*?)\\s*\\Qwas debited\\E", regex)
    }

    @Test
    fun `generateRegex returns quoted text when neither prefix nor suffix exists`() {
        val text = "100"
        val selection = RuleSelection("100", 0, 3)
        val regex = viewModel.generateRegex(text, selection)
        assertNotNull(regex)
        assertEquals("\\Q100\\E", regex)
    }

    @Test
    fun `generateRegex returns middle pattern when both prefix and suffix exist`() {
        val text = "Paid 100 to Store"
        val selection = RuleSelection("100", 5, 8)
        val regex = viewModel.generateRegex(text, selection)
        assertNotNull(regex)
        assertEquals("\\QPaid\\E\\s*(.*?)\\s*\\Qto Store\\E", regex)
    }
}
