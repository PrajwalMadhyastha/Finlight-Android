// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/RuleCreationViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RuleCreationViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var customSmsRuleDao: CustomSmsRuleDao

    @Captor
    private lateinit var ruleCaptor: ArgumentCaptor<CustomSmsRule>

    private lateinit var viewModel: RuleCreationViewModel

    @Before
    override fun setup() {
        super.setup()
        viewModel = RuleCreationViewModel(customSmsRuleDao)
    }

    @Test
    fun `initializeStateForCreation correctly pre-populates state from PotentialTransaction`() = runTest {
        // Arrange
        val potentialTxn = PotentialTransaction(
            sourceSmsId = 1L,
            smsSender = "Test",
            amount = 123.45,
            transactionType = "expense",
            merchantName = "Test Merchant",
            originalMessage = "Paid 123.45 to Test Merchant with HDFC Card xx9876.",
            potentialAccount = PotentialAccount("HDFC Card xx9876", "Card")
        )

        // Act
        viewModel.initializeStateForCreation(potentialTxn)

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("123.45", state.amountSelection.selectedText)
            assertEquals("Test Merchant", state.merchantSelection.selectedText)
            assertEquals("xx9876", state.accountSelection.selectedText)
            assertEquals("expense", state.transactionType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadRuleForEditing correctly populates state from existing rule`() = runTest {
        // Arrange
        val ruleId = 1
        val existingRule = CustomSmsRule(
            id = ruleId,
            triggerPhrase = "spent on card",
            merchantRegex = null,
            amountRegex = null,
            accountRegex = null,
            merchantNameExample = "Amazon",
            amountExample = "500.00",
            accountNameExample = "xx1234",
            priority = 10,
            sourceSmsBody = "some body"
        )
        `when`(customSmsRuleDao.getRuleById(ruleId)).thenReturn(flowOf(existingRule))

        // Act
        viewModel.loadRuleForEditing(ruleId)

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(ruleId, state.ruleIdToEdit)
            assertEquals("spent on card", state.triggerSelection.selectedText)
            assertEquals("Amazon", state.merchantSelection.selectedText)
            assertEquals("500.00", state.amountSelection.selectedText)
            assertEquals("xx1234", state.accountSelection.selectedText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Ignore
    fun `saveRule calls insert for new rule`() = runTest {
        // Arrange
        var onCompleteCalled = false
        val smsBody = "Transaction of 100 at Merchant X"
        val triggerSelection = RuleSelection("Transaction of", 0, 14)
        val merchantSelection = RuleSelection("Merchant X", 20, 30)

        // Act
        viewModel.onMarkAsTrigger(triggerSelection)
        viewModel.onMarkAsMerchant(merchantSelection)
        viewModel.saveRule(smsBody) { onCompleteCalled = true }

        // Assert
        verify(customSmsRuleDao).insert(ruleCaptor.capture())
        val capturedRule = ruleCaptor.value
        assertEquals(0, capturedRule.id) // 0 for new rule
        assertEquals(triggerSelection.selectedText, capturedRule.triggerPhrase)
        assertEquals(merchantSelection.selectedText, capturedRule.merchantNameExample)
        assertNotNull(capturedRule.merchantRegex)
        assertEquals(true, onCompleteCalled)
    }

    @Test
    @Ignore
    fun `saveRule calls update for existing rule`() = runTest {
        // Arrange
        var onCompleteCalled = false
        val ruleId = 5
        val smsBody = "Transaction of 100 at Merchant X"
        val triggerSelection = RuleSelection("Transaction of", 0, 14)
        val merchantSelection = RuleSelection("Merchant Y", 20, 30) // Updated merchant

        // Load the existing rule first
        val existingRule = CustomSmsRule(ruleId, "old trigger", null, null, null, null, null, null, 10, smsBody)
        `when`(customSmsRuleDao.getRuleById(ruleId)).thenReturn(flowOf(existingRule))
        viewModel.loadRuleForEditing(ruleId)
        viewModel.uiState.test { awaitItem() } // consume initial load

        // Act
        viewModel.onMarkAsTrigger(triggerSelection)
        viewModel.onMarkAsMerchant(merchantSelection)
        viewModel.saveRule(smsBody) { onCompleteCalled = true }

        // Assert
        verify(customSmsRuleDao).update(ruleCaptor.capture())
        val capturedRule = ruleCaptor.value
        assertEquals(ruleId, capturedRule.id) // Should have the existing ID
        assertEquals(triggerSelection.selectedText, capturedRule.triggerPhrase)
        assertEquals(merchantSelection.selectedText, capturedRule.merchantNameExample)
        assertEquals(true, onCompleteCalled)
    }
}