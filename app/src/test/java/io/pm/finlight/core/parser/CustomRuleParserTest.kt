// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/CustomRuleParserTest.kt
// REASON: A placeholder test file for validating user-defined custom
// parsing rules. This separates the testing of core parser logic from the
// user-configurable logic.
// FIX: Added a placeholder test to resolve the "No runnable methods" build error.
//
// REASON: MODIFIED - Replaced placeholder test with a full test suite for
// the new `transactionType` logic in `SmsParser.parseWithOnlyCustomRules`.
// These tests verify that the explicit type overrides keyword detection and
// that `null` correctly falls back to keyword detection.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.CustomSmsRule
import io.pm.finlight.ParseResult
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRuleParserTest : BaseSmsParserTest() {

    @Test
    fun `test custom rule transactionType 'income' overrides 'spent' keyword`() = runBlocking {
        // Arrange
        val smsBody = "You spent Rs. 100 at Test Merchant."
        val sms = SmsMessage(1, "TEST", smsBody, 1L)
        val rule = CustomSmsRule(
            id = 1,
            triggerPhrase = "spent Rs",
            amountRegex = "Rs. ([\\d,.]+)",
            merchantRegex = "at (.*)\\.",
            transactionType = "income", // Explicitly set to INCOME
            priority = 10,
            sourceSmsBody = smsBody,
            accountRegex = null,
            merchantNameExample = null,
            amountExample = null,
            accountNameExample = null
        )
        setupTest(customRules = listOf(rule))

        // Act
        val result = SmsParser.parseWithOnlyCustomRules(
            sms,
            customSmsRuleProvider,
            merchantRenameRuleProvider,
            merchantCategoryMappingProvider,
            categoryFinderProvider
        )

        // Assert
        assertNotNull("Parser should have matched the custom rule", result)
        assertTrue("Result should be Success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(100.0, transaction.amount, 0.0)
        assertEquals("Test Merchant", transaction.merchantName)
        assertEquals("income", transaction.transactionType) // <-- The important check
    }

    @Test
    fun `test custom rule transactionType 'expense' overrides 'credited' keyword`() = runBlocking {
        // Arrange
        val smsBody = "You were credited Rs. 500 by Test Merchant."
        val sms = SmsMessage(1, "TEST", smsBody, 1L)
        val rule = CustomSmsRule(
            id = 1,
            triggerPhrase = "credited Rs",
            amountRegex = "Rs. ([\\d,.]+)",
            merchantRegex = "by (.*)\\.",
            transactionType = "expense", // Explicitly set to EXPENSE
            priority = 10,
            sourceSmsBody = smsBody,
            accountRegex = null,
            merchantNameExample = null,
            amountExample = null,
            accountNameExample = null
        )
        setupTest(customRules = listOf(rule))

        // Act
        val result = SmsParser.parseWithOnlyCustomRules(
            sms,
            customSmsRuleProvider,
            merchantRenameRuleProvider,
            merchantCategoryMappingProvider,
            categoryFinderProvider
        )

        // Assert
        assertNotNull("Parser should have matched the custom rule", result)
        assertTrue("Result should be Success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(500.0, transaction.amount, 0.0)
        assertEquals("Test Merchant", transaction.merchantName)
        assertEquals("expense", transaction.transactionType) // <-- The important check
    }

    @Test
    fun `test custom rule transactionType 'null' falls back to 'spent' keyword`() = runBlocking {
        // Arrange
        val smsBody = "You spent Rs. 100 at Test Merchant."
        val sms = SmsMessage(1, "TEST", smsBody, 1L)
        val rule = CustomSmsRule(
            id = 1,
            triggerPhrase = "spent Rs",
            amountRegex = "Rs. ([\\d,.]+)",
            merchantRegex = "at (.*)\\.",
            transactionType = null, // "Auto-Detect"
            priority = 10,
            sourceSmsBody = smsBody,
            accountRegex = null,
            merchantNameExample = null,
            amountExample = null,
            accountNameExample = null
        )
        setupTest(customRules = listOf(rule))

        // Act
        val result = SmsParser.parseWithOnlyCustomRules(
            sms,
            customSmsRuleProvider,
            merchantRenameRuleProvider,
            merchantCategoryMappingProvider,
            categoryFinderProvider
        )

        // Assert
        assertNotNull("Parser should have matched the custom rule", result)
        assertTrue("Result should be Success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals("expense", transaction.transactionType) // <-- Should fall back to keyword
    }

    @Test
    fun `test custom rule transactionType 'null' falls back to 'credited' keyword`() = runBlocking {
        // Arrange
        val smsBody = "You were credited Rs. 500 by Test Merchant."
        val sms = SmsMessage(1, "TEST", smsBody, 1L)
        val rule = CustomSmsRule(
            id = 1,
            triggerPhrase = "credited Rs",
            amountRegex = "Rs. ([\\d,.]+)",
            merchantRegex = "by (.*)\\.",
            transactionType = null, // "Auto-Detect"
            priority = 10,
            sourceSmsBody = smsBody,
            accountRegex = null,
            merchantNameExample = null,
            amountExample = null,
            accountNameExample = null
        )
        setupTest(customRules = listOf(rule))

        // Act
        val result = SmsParser.parseWithOnlyCustomRules(
            sms,
            customSmsRuleProvider,
            merchantRenameRuleProvider,
            merchantCategoryMappingProvider,
            categoryFinderProvider
        )

        // Assert
        assertNotNull("Parser should have matched the custom rule", result)
        assertTrue("Result should be Success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals("income", transaction.transactionType) // <-- Should fall back to keyword
    }
}