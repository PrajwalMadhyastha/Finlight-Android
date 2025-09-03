// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/CanaraBankSmsParserTest.kt
// REASON: NEW FILE - Contains all unit tests specifically for parsing SMS
// messages from Canara Bank, refactored from the original SmsParserTest.
// FIX - All calls to SmsParser.parse now include the required
// categoryFinderProvider, resolving build errors.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CanaraBankSmsParserTest : BaseSmsParserTest() {

    @Test
    fun `test parses Canara Bank credit with 'CREDITED to your account' format`() = runBlocking {
        setupTest()
        val smsBody = "An amount of INR 20,000.00 has been CREDITED to your account XXX810 on 03/05/2023 towards Cash Deposit. Total Avail.bal INR 1,12,210.00. - Canara Bank"
        val mockSms = SmsMessage(
            id = 905L,
            sender = "AD-CANBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull(result)
        assertEquals(20000.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Cash Deposit", result?.merchantName)
        assertEquals("Canara Bank - account XXX810", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses Canara Bank credit with 'has been CREDITED to your account'`() = runBlocking {
        setupTest()
        val smsBody = "An amount of INR 2,000.00 has been CREDITED to your account XXX810 on 17/03/2023 towards Cash Deposit. Total Avail.bal INR 2,000.00. - Canara Bank"
        val mockSms = SmsMessage(
            id = 1679042011072L,
            sender = "CP-CANBNK",
            body = smsBody,
            date = 1679042011072L
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(2000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Cash Deposit", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Canara Bank - account XXX810", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses Canara Bank credit with 'has been CREDITED to your A_c'`() = runBlocking {
        setupTest()
        val smsBody = "INR 25,000.00 has been CREDITED to your A/c XXX810 on 17/06/2025 by CASH.Total bal is INR 38,118.00.  Please Install Canara ai1 app for Mobile Banking services - Canara Bank"
        val mockSms = SmsMessage(
            id = 1750144389022L,
            sender = "JK-CANBNK-S",
            body = smsBody,
            date = 1750144389022L
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(25000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("CASH", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Canara Bank - A/c XXX810", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses Canara Bank debit and account`() = runBlocking {
        setupTest()
        val smsBody = "An amount of INR 60,000.00 has been DEBITED to your account XXX810 on 27/11/2024 towards Cheque Withdrawal. Total Avail.bal INR 41,928.00. - Canara Bank"
        val mockSms = SmsMessage(
            id = 1L,
            sender = "AD-CANBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(60000.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Cheque Withdrawal", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("Canara Bank - account XXX810", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }
}
