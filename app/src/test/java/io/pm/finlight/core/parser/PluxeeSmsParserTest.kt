// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/PluxeeSmsParserTest.kt
// REASON: NEW FILE - Contains all unit tests specifically for parsing SMS
// messages from Pluxee (formerly Sodexo), refactored from the generic test file.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PluxeeSmsParserTest : BaseSmsParserTest() {

    @Test
    fun `test parses Pluxee Meal Card wallet message`() = runBlocking {
        setupTest()
        val smsBody = "Rs. 60.00 spent from Pluxee Meal Card wallet, card no.xx1345 on 30-06-2025 18:41:56 at KITCHEN AFF . Avl bal Rs.1824.65. Not you call 18002106919"
        val mockSms = SmsMessage(
            id = 7L,
            sender = "VD-PLUXEE",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(60.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("KITCHEN AFF", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Pluxee - xx1345", result?.potentialAccount?.formattedName)
        assertEquals("Meal Card wallet", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses Pluxee Meal Card debit`() = runBlocking {
        setupTest()
        val smsBody = "Rs. 53.00 was spent from Meal Card Wallet linked to your Pluxee Card xx1345 on 26-10-2023 13:22:39 at NIDHISH CAT. Txn no. 4451251820938. Avl bal is Rs. 1037.00. Not you? Call 18002106919. Pluxee"
        val mockSms = SmsMessage(id = 10L, sender = "VD-PLUXEE", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(53.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("NIDHISH CAT", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("Meal Card Wallet linked to your Pluxee Card xx1345", result?.potentialAccount?.formattedName)
        assertEquals("Meal Card", result?.potentialAccount?.accountType)
    }
}
