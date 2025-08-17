// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/GenericSmsParserTest.kt
// REASON: NEW FILE - Contains all unit tests for parsing generic SMS formats
// and messages from various other banks (BoB, PNB, etc.), refactored from the
// original SmsParserTest.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GenericSmsParserTest : BaseSmsParserTest() {

    @Test
    fun `test parses debit message with foreign currency code`() = runBlocking {
        setupTest()
        val smsBody = "You have spent MYR 55.50 at STARBUCKS."
        val mockSms = SmsMessage(
            id = 1L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(55.50, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("STARBUCKS", result?.merchantName)
        assertEquals("MYR", result?.detectedCurrencyCode)
    }

    @Test
    fun `test parses debit message with home currency code`() = runBlocking {
        setupTest()
        val smsBody = "You have spent INR 120.00 at CCD."
        val mockSms = SmsMessage(
            id = 1L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(120.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
    }

    @Test
    fun `test parses debit message with home currency symbol`() = runBlocking {
        setupTest()
        val smsBody = "You have spent Rs. 300 at a local store."
        val mockSms = SmsMessage(
            id = 1L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(300.0, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
    }

    @Test
    fun `test parses debit message with no currency`() = runBlocking {
        setupTest()
        val smsBody = "A purchase of 500.00 was made at some store."
        val mockSms = SmsMessage(
            id = 1L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(500.00, result?.amount)
        assertNull("Detected currency should be null", result?.detectedCurrencyCode)
    }

    @Test
    fun `test prioritizes amount with currency over other numbers`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acct XX823 debited for Rs 240.00 on 28-Jul-25; DAKSHIN CAFE credited."
        val mockSms = SmsMessage(
            id = 16L,
            sender = "DM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(240.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
    }


    @Test
    fun `test parses debit message successfully`() = runBlocking {
        setupTest()
        val smsBody = "Your account with HDFC Bank has been debited for Rs. 750.50 at Amazon on 22-Jun-2025."
        val mockSms = SmsMessage(
            id = 1L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result for a debit message", result)
        assertEquals(750.50, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Amazon", result?.merchantName)
    }

    @Test
    fun `test parses credit message successfully`() = runBlocking {
        setupTest()
        val smsBody = "You have received a credit of INR 5,000.00 from Freelance Client."
        val mockSms = SmsMessage(
            id = 2L,
            sender = "DM-SOMEBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result for a credit message", result)
        assertEquals(5000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Freelance Client", result?.merchantName)
    }

    @Test
    fun `test parses transaction reversal as income`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, your ICICI Bank Account XXX508 has been credited with Rs 208.42 on 03-Sep-22 as reversal of transaction with UPI: 224679541058."
        val mockSms = SmsMessage(
            id = 101L,
            sender = "DM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(208.42, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("reversal of transaction", result?.merchantName)
    }

    @Test
    fun `test parses Sodexo card loading as income`() = runBlocking {
        setupTest()
        val smsBody = "Your Sodexo Card has been successfully loaded with Rs.1250 towards Meal Card A/c on 15:00,15 Dec."
        val mockSms = SmsMessage(
            id = 102L,
            sender = "VM-SODEXO",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(1250.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Sodexo Card", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses Bank of Baroda UPI transfer message`() = runBlocking {
        setupTest()
        val smsBody = "Rs.1656 transferred from A/c ...7656 to:UPI/429269093079. Total Bal:Rs.6114.76CR. Avlbl Amt:Rs.6114.76(18-10-2024 22:52:46) - Bank of Baroda"
        val mockSms = SmsMessage(
            id = 501L,
            sender = "CP-BOBTXN",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(1656.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("UPI/429269093079", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Bank of Baroda - ...7656", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses amount correctly when currency code is attached`() = runBlocking {
        setupTest()
        val smsBody = "Dear 919480XXX, your wallet A/c is successfully Credited with 15000INR on 11-11-2024 13:34:40. Check now - http://Kx10.in/FICGXU/ePh6Rv Finance Guru"
        val mockSms = SmsMessage(
            id = 401L,
            sender = "VM-FINGRU",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals("Parser should prioritize the amount attached to a currency code", 15000.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Finance Guru", result?.merchantName)
    }

    @Test
    fun `test parses merchant correctly from UPI credit message with 'by'`() = runBlocking {
        setupTest()
        val smsBody = "Rs.1600 Credited to A/c ...7656 thru UPI/069871591309 by 9686714029_axl. Total Bal:Rs.33438.48CR. Avlbl Amt:Rs.33438.48(18-11-2024 08:02:26) - Bank of Baroda"
        val mockSms = SmsMessage(
            id = 402L,
            sender = "VM-BOB",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(1600.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("9686714029 axl", result?.merchantName) // Note: underscore is replaced with space
    }

    @Test
    fun `test parses merchant correctly from NEFT credit message with 'by'`() = runBlocking {
        setupTest()
        val smsBody = "Rs.6327 Credited to A/c ...7656 thru NEFT UTR RBI3532499914098 by AIIMS JODHPUR. Total Bal:Rs.7092.4CR. Avlbl Amt:Rs.7092.4(17-12-2024 17:22:45) - Bank of Baroda"
        val mockSms = SmsMessage(
            id = 403L,
            sender = "VM-BOB",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(6327.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("AIIMS JODHPUR", result?.merchantName)
    }

    @Test
    fun `test parses BOB debit with 'from' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Rs 918.00 debited from A/C XXXXXX9130 and credited to user12@apl UPI Ref:466970233165. Not you? Call 18005700 -BOB"
        val mockSms = SmsMessage(
            id = 702L,
            sender = "VM-BOB",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(918.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("user12@apl", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("A/C XXXXXX9130", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses Dept of Posts credit with 'CREDIT with amount' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Account  No. XXXXXX5755 CREDIT with amount Rs. 5700.00 on 30-12-2022. Balance: Rs.216023.00. [ S3256155]"
        val mockSms = SmsMessage(
            id = 904L,
            sender = "BH-DOPBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(5700.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Account No. XXXXXX5755", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test correctly parses Union Bank debit and account`() = runBlocking {
        setupTest()
        val smsBody = "Your SB A/c *3618 Debited for Rs:147.5 on 16-11-2021 16:45:07 by Transfer Avl Bal Rs:21949.7 -Union Bank of India"
        val mockSms = SmsMessage(
            id = 1L,
            sender = "AM-UBOI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals("Amount should be 147.5, not the account number fragment", 147.5, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Transfer", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SB A/c *3618", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses Union Bank credit and account`() = runBlocking {
        setupTest()
        val smsBody = "Your SB A/c **23618 is Credited for Rs.1743 on 31-01-2021 03:42:47 by Transfer. Avl Bal Rs:5811.3 -Union Bank of India DOWNLOAD U MB HTTP://ONELINK.TO/BUYHR7"
        val mockSms = SmsMessage(
            id = 1L,
            sender = "AM-UBOI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(1743.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Transfer", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SB A/c **23618", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses Dept of Posts credit and account`() = runBlocking {
        setupTest()
        val smsBody = "Account  No. XXXXXXXX1901 CREDIT with amount Rs. 100000.00 on 12-03-2025. Balance: Rs.100000.00. [IN3956101]"
        val mockSms = SmsMessage(
            id = 2L,
            sender = "BH-DOPBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(100000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertNull("Merchant should be null", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("Account No. XXXXXXXX1901", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses Union Bank credit by int and account`() = runBlocking {
        setupTest()
        val smsBody = "Your SB A/c *3618 Credited for Rs:2383.00 on 31-07-2025 03:24:18 by int of TD A/c *0106 Avl Bal Rs:29467.70- Union Bank of India"
        val mockSms = SmsMessage(
            id = 3L,
            sender = "AM-UBOI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(2383.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SB A/c *3618", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses DEBIT with amount message and account`() = runBlocking {
        setupTest()
        val smsBody = "Account  No. XXXXXX5755 DEBIT with amount Rs. 15000.00 on 15-07-2022. Balance: Rs.80173.00. [S11997812]"
        val mockSms = SmsMessage(
            id = 12L,
            sender = "BH-DOPBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(15000.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertNull("Merchant should be null as it's not present", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("Account No. XXXXXX5755", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses PNB credit and account`() = runBlocking {
        setupTest()
        val smsBody = "Ac XXXXXXXX00007271 Credited with Rs.200000.00 , 28-11-2022 09:52:52. Aval Bal Rs.241439.80 CR. Helpline 18001802222.Register for e-statement,if not done.-PNB"
        val mockSms = SmsMessage(
            id = 1L,
            sender = "IM-PNB",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(200000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertNull("Merchant should be null", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("PNB - Ac XXXXXXXX00007271", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses Indusind Bank debit with 'Dr with'`() = runBlocking {
        setupTest()
        val smsBody = "user204@yescred linked to Credit Card XX4062 is Dr with INR.26.00 by VPA user941@ybl (UPI Ref no 558529523785) - Indusind Bank"
        val mockSms = SmsMessage(
            id = 9009L,
            sender = "VM-IndusB",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(26.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("user941@ybl", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Indusind Bank - Credit Card XX4062", result?.potentialAccount?.formattedName)
        assertEquals("Credit Card", result?.potentialAccount?.accountType)
    }
}
