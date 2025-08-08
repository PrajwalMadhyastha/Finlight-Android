// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/SmsParserTest.kt
// REASON: FEATURE - Added new unit tests to verify the currency detection logic.
// The tests now cover scenarios where an SMS contains a foreign currency code
// (MYR), the home currency code (INR), or the home currency symbol (Rs),
// ensuring the parser correctly extracts both the amount and the currency.
// FIX - Added a new test case for the specific ICICI debit message format that
// was failing, ensuring the improved regex correctly handles it.
// FIX - Added a new test case to ensure the parser prioritizes amounts with
// currency symbols over other numbers in an SMS, like account numbers.
// FIX - Added a new test case for the `InfoACH*...Avl Bal` format to verify
// the new, more specific merchant parsing regex.
// =================================================================================
package io.pm.finlight

import io.pm.finlight.utils.SmsParser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SmsParserTest {

    @Mock
    private lateinit var mockCustomSmsRuleDao: CustomSmsRuleDao

    @Mock
    private lateinit var mockMerchantRenameRuleDao: MerchantRenameRuleDao

    @Mock
    private lateinit var mockIgnoreRuleDao: IgnoreRuleDao

    @Mock
    private lateinit var mockMerchantCategoryMappingDao: MerchantCategoryMappingDao

    private val emptyMappings = emptyMap<String, String>()

    @Before
    fun setUp() {
        // No need to mock AppDatabase anymore
    }

    private suspend fun setupTest(
        customRules: List<CustomSmsRule> = emptyList(),
        renameRules: List<MerchantRenameRule> = emptyList(),
        ignoreRules: List<IgnoreRule> = emptyList()
    ) {
        `when`(mockCustomSmsRuleDao.getAllRules()).thenReturn(flowOf(customRules))
        `when`(mockMerchantRenameRuleDao.getAllRules()).thenReturn(flowOf(renameRules))
        `when`(mockIgnoreRuleDao.getEnabledRules()).thenReturn(ignoreRules.filter { it.isEnabled })
    }

    @Test
    fun `test parses HDFC UPI sent message`() = runBlocking {
        setupTest()
        val smsBody = "Sent Rs.11.00\nFrom HDFC Bank A/C *1243\nTo Raju\nOn 07/08/25\nRef 558523453508\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"
        val mockSms = SmsMessage(id = 18L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result", result)
        assertEquals(11.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Raju", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank - *1243", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI debit with ACH and Avl Bal`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acc XX244 debited Rs. 8,700.00 on 02-Aug-25 InfoACH*ZERODHA B.Avl Bal Rs. 3,209.31.To dispute call 18002662 or SMS BLOCK 646 to 9215676766"
        val mockSms = SmsMessage(id = 17L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result", result)
        assertEquals(8700.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("ACH*ZERODHA B", result?.merchantName) // Note: The parser correctly gets the merchant now
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx244", result?.potentialAccount?.formattedName)
        assertEquals("Savings Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses debit message with foreign currency code`() = runBlocking {
        setupTest()
        val smsBody = "You have spent MYR 55.50 at STARBUCKS."
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

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
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result", result)
        assertEquals(120.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
    }

    @Test
    fun `test parses debit message with home currency symbol`() = runBlocking {
        setupTest()
        val smsBody = "You have spent Rs. 300 at a local store."
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result", result)
        assertEquals(300.0, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode) // Rs should be normalized to INR
    }

    @Test
    fun `test parses debit message with no currency`() = runBlocking {
        setupTest()
        val smsBody = "A purchase of 500.00 was made at some store."
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result", result)
        assertEquals(500.00, result?.amount)
        assertNull("Detected currency should be null", result?.detectedCurrencyCode)
    }

    @Test
    fun `test parses ICICI debit message with Rs symbol correctly`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acct XX823 debited for Rs 240.00 on 28-Jul-25; DAKSHIN CAFE credited. UPI: 552200221100. Call 18002661 for dispute. SMS BLOCK 823 to 123123123"
        val mockSms = SmsMessage(id = 15L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result", result)
        assertEquals(240.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
        assertEquals("expense", result?.transactionType)
        assertEquals("DAKSHIN CAFE", result?.merchantName)
    }

    @Test
    fun `test prioritizes amount with currency over other numbers`() = runBlocking {
        setupTest()
        // This SMS has multiple numbers: an account number (823) and the actual amount (240.00)
        val smsBody = "ICICI Bank Acct XX823 debited for Rs 240.00 on 28-Jul-25; DAKSHIN CAFE credited."
        val mockSms = SmsMessage(id = 16L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result", result)
        // CRITICAL: Assert that it picked the correct amount, not the account number part.
        assertEquals(240.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
    }


    @Test
    fun `test parses debit message successfully`() = runBlocking {
        setupTest()
        val smsBody = "Your account with HDFC Bank has been debited for Rs. 750.50 at Amazon on 22-Jun-2025."
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result for a debit message", result)
        assertEquals(750.50, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Amazon", result?.merchantName)
    }

    @Test
    fun `test parses credit message successfully`() = runBlocking {
        setupTest()
        val smsBody = "You have received a credit of INR 5,000.00 from Freelance Client."
        val mockSms = SmsMessage(id = 2L, sender = "DM-SOMEBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull("Parser should return a result for a credit message", result)
        assertEquals(5000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Freelance Client", result?.merchantName)
    }

    @Test
    fun `test returns null for non-financial message`() = runBlocking {
        setupTest()
        val smsBody = "Hello, just checking in. Are we still on for dinner tomorrow evening?"
        val mockSms = SmsMessage(id = 3L, sender = "+1234567890", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNull("Parser should return null for a non-financial message", result)
    }

    @Test
    fun `test parses SBI Credit Card message`() = runBlocking {
        setupTest()
        val smsBody = "Rs.267.00 spent on your SBI Credit Card ending with 3201 at HALLI THOTA on 29-06-25 via UPI (Ref No. 1231230123). Trxn. Not done by you? Report at [https://sbicards.com/Dispute](https://sbicards.com/Dispute))"
        val mockSms = SmsMessage(id = 4L, sender = "VM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull(result)
        assertEquals(267.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("HALLI THOTA", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("SBI - xx3201", result?.potentialAccount?.formattedName)
        assertEquals("Credit Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI debit message`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acct XX823 debited for Rs 240.00 on 21-Jun-25; DAKSHIN CAFE credited. UPI: 552200221100. Call 18002661 for dispute."
        val mockSms = SmsMessage(id = 5L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull(result)
        assertEquals(240.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("DAKSHIN CAFE", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx823", result?.potentialAccount?.formattedName)
        assertEquals("Savings Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC card message`() = runBlocking {
        setupTest()
        val smsBody = "[JD-HDFCBK-S] Spent Rs.388.19 On HDFC Bank Card 9922 At ..MC DONALDS_ on2025-06-22:08:01:24.Not You> To Block+Reissue Call 18002323232/SMS BLOCK CC 9922 to 123098123"
        val mockSms = SmsMessage(id = 6L, sender = "JD-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull(result)
        assertEquals(388.19, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("MC DONALDS", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank - xx9922", result?.potentialAccount?.formattedName)
        assertEquals("Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses Pluxee Meal Card message`() = runBlocking {
        setupTest()
        val smsBody = "Rs. 60.00 spent from Pluxee Meal Card wallet, card no.xx1345 on 30-06-2025 18:41:56 at KITCHEN AFF . Avl bal Rs.1824.65. Not you call 18002106919"
        val mockSms = SmsMessage(id = 7L, sender = "VD-PLUXEE", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull(result)
        assertEquals(60.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("KITCHEN AFF", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Pluxee - xx1345", result?.potentialAccount?.formattedName)
        assertEquals("Meal Card wallet", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses ICICI credit message with tricky format`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Acct XX823 is credited with Rs 6000.00 on 26-Jun-25 from GANGA MANGA. UPI:5577822323232-ICICI Bank"
        val mockSms = SmsMessage(id = 8L, sender = "QP-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull(result)
        assertEquals(6000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("GANGA MANGA", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx823", result?.potentialAccount?.formattedName)
        assertEquals("Savings Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test ignores successful payment confirmation`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "payment of.*is successful", isEnabled = true)))
        val smsBody = "Your payment of Rs.330.80 for A4-108 against Water Charges is successful. Regards NoBrokerHood"
        val mockSms = SmsMessage(id = 10L, sender = "VM-NBHOOD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)
        assertNull("Parser should ignore successful payment confirmations", result)
    }

    @Test
    fun `test parses ICICI NEFT credit message`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Account XX823 credited:Rs. 1,133.00 on 01-Jul-25. Info NEFT-HDFCN5202507024345356218-. Available Balance is Rs. 1,858.35."
        val mockSms = SmsMessage(id = 11L, sender = "VM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)

        assertNotNull(result)
        assertEquals(1133.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("NEFT-HDFCN5202507024345356218-", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("ICICI Bank - xx823", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test ignores HDFC NEFT credit message`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "has been credited to", isEnabled = true)))
        val smsBody = "HDFC Bank : NEFT money transfer Txn No HDFCN520253454560344 for Rs INR 1,500.00 has been credited to Manga Penga on 01-07-2025 at 08:05:30"
        val mockSms = SmsMessage(id = 12L, sender = "VM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)
        assertNull("Parser should ignore has been credited to messages", result)
    }

    @Test
    fun `test ignores credit card payment confirmation`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "payment of.*has been received towards", isEnabled = true)))
        val smsBody = "Payment of INR 1180.01 has been received towards your SBI card XX1121"
        val mockSms = SmsMessage(id = 13L, sender = "DM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)
        assertNull("Parser should ignore credit card payment confirmations", result)
    }

    @Test
    fun `test ignores bharat bill pay confirmation`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "Payment of.*has been received on your.*Credit Card", isEnabled = true)))
        val smsBody = "Payment of Rs 356.33 has been received on your ICICI Bank Credit Card XX2529 through Bharat Bill Payment System on 03-JUL-25."
        val mockSms = SmsMessage(id = 14L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)
        assertNull("Parser should ignore Bharat Bill Pay confirmations", result)
    }

    @Test
    fun `test ignores message with user-defined ignore phrase`() = runBlocking {
        val ignoreRules = listOf(IgnoreRule(id = 1, pattern = "invoice of", isEnabled = true))
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "An Invoice of Rs.330.8 for A4 Block-108 is raised."
        val mockSms = SmsMessage(id = 9L, sender = "VM-NBHOOD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleDao, mockMerchantRenameRuleDao, mockIgnoreRuleDao, mockMerchantCategoryMappingDao)
        assertNull("Parser should ignore messages with user-defined phrases", result)
    }
}
