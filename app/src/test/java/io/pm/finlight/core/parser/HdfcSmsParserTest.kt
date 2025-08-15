// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/HdfcSmsParserTest.kt
// REASON: NEW FILE - Contains all unit tests specifically for parsing SMS
// messages from HDFC Bank, refactored from the original SmsParserTest.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HdfcSmsParserTest : BaseSmsParserTest() {

    @Test
    fun `test parses HDFC UPI sent message`() = runBlocking {
        setupTest()
        val smsBody = "Sent Rs.11.00\nFrom HDFC Bank A/C *1243\nTo Raju\nOn 07/08/25\nRef 558523453508\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"
        val mockSms = SmsMessage(
            id = 18L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(11.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Raju", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank - *1243", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC card message`() = runBlocking {
        setupTest()
        val smsBody = "[JD-HDFCBK-S] Spent Rs.388.19 On HDFC Bank Card 9922 At ..MC DONALDS_ on2025-06-22:08:01:24.Not You> To Block+Reissue Call 18002323232/SMS BLOCK CC 9922 to 123098123"
        val mockSms = SmsMessage(
            id = 6L,
            sender = "JD-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(388.19, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("MC DONALDS", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank - xx9922", result?.potentialAccount?.formattedName)
        assertEquals("Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses new HDFC Credit Card format`() = runBlocking {
        setupTest()
        val smsBody = "You've spent Rs.349 On HDFC Bank CREDIT Card xx1335 At RAZ*StickON..."
        val mockSms = SmsMessage(
            id = 103L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(349.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("RAZ*StickON", result?.merchantName)
        assertEquals("HDFC Bank CREDIT Card - xx1335", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses HDFC deposit with 'in' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Update! INR 11,000.00 deposited in HDFC Bank A/c XX2536 on 15-OCT-24 for XXXXXXXXXX0461-TPT-Transfer-PRAJWAL MADHYASTHA K P.Avl bal INR 26,475.53. Cheque deposits in A/C are subject to clearing"
        val mockSms = SmsMessage(
            id = 701L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(11000.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("TPT-Transfer-PRAJWAL MADHYASTHA K P", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c XX2536", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC card spend with 'on' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Rs.6175 spent on HDFC Bank Card x2477 at ..SUDARSHAN FAMILY_ on 2024-11-13:17:18:38.Not U? To Block & Reissue Call 18002586161/SMS BLOCK CC 2477 to8216821344"
        val mockSms = SmsMessage(
            id = 703L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(6175.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("SUDARSHAN FAMILY", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank Card x2477", result?.potentialAccount?.formattedName)
        assertEquals("Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC multiline sent message with 'From' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Sent Rs.30.00\nFrom HDFC Bank A/C x2536\nTo Sukrith pharma\nOn 06/12/24\nRef 434117067801\nNot You?\nCall 18002586161/SMS BLOCK UPI to8216821344"
        val mockSms = SmsMessage(
            id = 704L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(30.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Sukrith pharma", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/C x2536", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC credit alert with 'to' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Credit Alert!\nRs.2.00 credited to HDFC Bank A/c xx2536 on 07-12-24 from VPA user34@axisbank (UPI 526830463424)"
        val mockSms = SmsMessage(
            id = 705L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(2.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("user34@axisbank", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c xx2536", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC NEFT deposit with 'in' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Update! INR 30,000.00 deposited in HDFC Bank A/c XX2536 on 15-FEB-25 for NEFT Cr-SBIN0007631-Kavyashree Bhat-My hdfc-SBINN52025021511431836.Avl bal INR 55,727.04. Cheque deposits in A/C are subject to clearing"
        val mockSms = SmsMessage(
            id = 706L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(30000.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("NEFT Cr-SBIN0007631-Kavyashree Bhat-My hdfc-SBINN52025021511431836", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c XX2536", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC income with 'in your' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Money Received - INR 1.00 in your HDFC Bank A/c xx2536 on 17-12-24 by A/c linked to mobile no xx4455 (IMPS Ref No. 435202444399) Avl bal: INR 14,565.00"
        val mockSms = SmsMessage(
            id = 801L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals("income", result?.transactionType)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c xx2536", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC multiline expense with 'sent from' keyword`() = runBlocking {
        setupTest()
        val smsBody = "IMPS INR 10.00\nsent from HDFC Bank A/c XX2536 on 14-04-25\nTo A/c xxxxxxx8305\nRef-510419375047\nNot you?Call 18002586161/SMS BLOCK OB to8216821344"
        val mockSms = SmsMessage(
            id = 802L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals("expense", result?.transactionType)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c XX2536", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC expense with 'debited from' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Rs. 2.00 debited from HDFC Bank A/c **6982 on 17-12 to A/c **3377 (UPI Ref No 435221138842).\nNot You? Call 18002586161 or SMS BLOCK UPI to8216821344"
        val mockSms = SmsMessage(
            id = 803L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals("expense", result?.transactionType)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c **6982", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC recurring payment without OTP`() = runBlocking {
        setupTest()
        val smsBody = "Rs.199 without OTP/PIN HDFC Bank Card x4433 At NETFLIX On 2024-07-05:23:03:33.Not U? Block&Reissue:Call 18002586161/SMS BLOCK CC 4433 to2015121976"
        val mockSms = SmsMessage(
            id = 9001L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(199.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("NETFLIX", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank Card x4433", result?.potentialAccount?.formattedName)
        assertEquals("Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC NEFT debit and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Rs.180,000.00 is debited from A/c XXXX5733 for NEFT transaction via HDFC Bank NetBanking. Call 18002586161 if txn not done by you"
        val mockSms = SmsMessage(
            id = 9005L,
            sender = "VM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(180000.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("NEFT transaction", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank - A/c XXXX5733", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC IMPS transfer and account`() = runBlocking {
        setupTest()
        val smsBody = "Money Sent-INR 15,000.00 From HDFC Bank A/c XX5733 on 28-10-23 To A/c xxxxxxxx9709 IMPS Ref-330120320348 Avl bal:INR 5,132.30 Not you?Call 18002586161"
        val mockSms = SmsMessage(
            id = 9006L,
            sender = "VM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(15000.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("A/c xxxxxxxx9709", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c XX5733", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC credit card credit and account`() = runBlocking {
        setupTest()
        val smsBody = "HDFC Bank Cardmember, Rs 3300 has been credited on your credit card ending 4433 from AIRTEL Gurgaon IND on 02/JUN/2024. Ask Eva for details, http://hdfcbk.io/k/DUvfEq5n5ex"
        val mockSms = SmsMessage(
            id = 9007L,
            sender = "VM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(3300.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("AIRTEL Gurgaon IND", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank Card - credit card ending 4433", result?.potentialAccount?.formattedName)
        assertEquals("Credit Card", result?.potentialAccount?.accountType)
    }
}
