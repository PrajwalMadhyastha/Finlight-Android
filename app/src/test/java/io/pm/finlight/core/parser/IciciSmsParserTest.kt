// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/IciciSmsParserTest.kt
// REASON: NEW FILE - Contains all unit tests specifically for parsing SMS
// messages from ICICI Bank, refactored from the original SmsParserTest.
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
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

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
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

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
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

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
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

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
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull(result)
        assertEquals(1133.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("NEFT-HDFCN5202507024345356218-", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx823", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI ACH credit and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, your ICICI Bank Account XX508 has been credited with INR 328.00 on 30-Aug-22. Info:ACH*RELIANCEINDUSTRIES*000000000002. The Available Balance is INR 42,744.71."
        val mockSms = SmsMessage(id = 1L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(328.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("ACH*RELIANCEINDUSTRIES*000000000002", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("ICICI Bank Account XX508", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI UPI reversal and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, your ICICI Bank Account XXX508 has been credited with Rs 50.00 on 27-Sep-22 as reversal of transaction with UPI: 227040664818."
        val mockSms = SmsMessage(id = 2L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(50.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("reversal of transaction", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("ICICI Bank Account XXX508", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI NEFT debit and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, ICICI Bank Account XX508 is debited with INR 5,000.00 on 02-OCT-22. Info: BIL*NEFT*0005. The Available Balance is INR 9,981.28. Call 18002662 for dispute or SMS BLOCK 646 to4082711530"
        val mockSms = SmsMessage(id = 3L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(5000.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("BIL*NEFT*0005", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("ICICI Bank Account XX508", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI Card spend and account`() = runBlocking {
        setupTest()
        val smsBody = "INR 7,502.00 spent on ICICI Bank Card XX8011 on 31-Aug-22 at Amazon. Avl Lmt: INR 2,51,545.65. To dispute,call 18002662/SMS BLOCK 8011 to4082711530"
        val mockSms = SmsMessage(id = 4L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(7502.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Amazon", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("ICICI Bank Card XX8011", result?.potentialAccount?.formattedName)
        assertEquals("Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI Card spend with 'spent using' format`() = runBlocking {
        setupTest()
        val smsBody = "INR 782.00 spent using ICICI Bank Card XX1001 on 29-Jul-25 on IND*Amazon.in -. Avl Limit: INR 4,94,835.00. If not you, call 1800 2662/SMS BLOCK 1001 to4082711530."
        val mockSms = SmsMessage(id = 5L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(782.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("IND*Amazon.in", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("ICICI Bank Card XX1001", result?.potentialAccount?.formattedName)
        assertEquals("Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI AutoPay debit`() = runBlocking {
        setupTest()
        val smsBody = "Your account has been successfully debited with Rs 149.00 on 17-Sep-22 towards JioHotstar for Autopay AutoPay, RRN 506941017769-ICICI Bank."
        val mockSms = SmsMessage(id = 6L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(149.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("JioHotstar", result?.merchantName)
        assertEquals(null, result?.potentialAccount) // No account info in this message
    }

    @Test
    fun `test parses ICICI IMPS credit with mobile number`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Account XX508 is credited with Rs 1,800.00 on 07-Jan-25 by Account linked to mobile number XXXXX00000. IMPS Ref. no. 500713512538."
        val mockSms = SmsMessage(id = 8L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(1800.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Account linked to mobile number XXXXX00000", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("ICICI Bank Account XX508", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI credit card refund without 'your'`() = runBlocking {
        setupTest()
        val smsBody = "IND*AMAZON refund of Rs 372 credited to ICICI Bank Credit Card XX2001 on 25-JUN-24. Revised total due Rs 0, minimum due Rs 0"
        val mockSms = SmsMessage(
            id = 9010L,
            sender = "DM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(372.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("IND*AMAZON", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank Credit Card XX2001", result?.potentialAccount?.formattedName)
        assertEquals("Credit Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI debit with account at start and bank at end`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Account XX898 has been debited for Rs 15.00 on 22-Dec-24, towards linked user635@axisbank. UPI Ref. no. 310902003574-ICICI Bank."
        val mockSms = SmsMessage(
            id = 9011L,
            sender = "DM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(15.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("linked user635@axisbank", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - Account XX898", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI credit with 'We have credited'`() = runBlocking {
        setupTest()
        val smsBody = "We have credited your ICICI Bank Account XX898 with INR 20,000.00 on 01-Aug-23. Info:NEFT-N213232572818450-PRAGAT. The Available Balance is INR 22,965.07."
        val mockSms = SmsMessage(
            id = 9014L,
            sender = "VM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(20000.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("NEFT-N213232572818450-PRAGAT", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank Account XX898", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }
}
