// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/IciciSmsParserTest.kt
// REASON: NEW FILE - Contains all unit tests specifically for parsing SMS
// messages from ICICI Bank, refactored from the original SmsParserTest.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IciciSmsParserTest : BaseSmsParserTest() {

    @Test
    fun `test parses ICICI debit with ACH and Avl Bal`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acc XX244 debited Rs. 8,700.00 on 02-Aug-25 InfoACH*ZERODHA B.Avl Bal Rs. 3,209.31.To dispute call 18002662 or SMS BLOCK 646 to 9215676766"
        val mockSms = SmsMessage(
            id = 17L,
            sender = "DM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(8700.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("ACH*ZERODHA B", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx244", result?.potentialAccount?.formattedName)
        assertEquals("Savings Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI debit message with Rs symbol correctly`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acct XX823 debited for Rs 240.00 on 28-Jul-25; DAKSHIN CAFE credited. UPI: 552200221100. Call 18002661 for dispute. SMS BLOCK 823 to 123123123"
        val mockSms = SmsMessage(
            id = 15L,
            sender = "DM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(240.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
        assertEquals("expense", result?.transactionType)
        assertEquals("DAKSHIN CAFE", result?.merchantName)
    }

    @Test
    fun `test parses ICICI debit message`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acct XX823 debited for Rs 240.00 on 21-Jun-25; DAKSHIN CAFE credited. UPI: 552200221100. Call 18002661 for dispute."
        val mockSms = SmsMessage(
            id = 5L,
            sender = "DM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(240.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("DAKSHIN CAFE", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx823", result?.potentialAccount?.formattedName)
        assertEquals("Savings Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI credit message with tricky format`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Acct XX823 is credited with Rs 6000.00 on 26-Jun-25 from GANGA MANGA. UPI:5577822323232-ICICI Bank"
        val mockSms = SmsMessage(
            id = 8L,
            sender = "QP-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(6000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("GANGA MANGA", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx823", result?.potentialAccount?.formattedName)
        assertEquals("Savings Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI NEFT credit message`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Account XX823 credited:Rs. 1,133.00 on 01-Jul-25. Info NEFT-HDFCN5202507024345356218-. Available Balance is Rs. 1,858.35."
        val mockSms = SmsMessage(
            id = 11L,
            sender = "VM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(1133.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("NEFT-HDFCN5202507024345356218-", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx823", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }
}