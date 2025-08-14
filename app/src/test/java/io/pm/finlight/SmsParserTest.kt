package io.pm.finlight

import kotlinx.coroutines.flow.first
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

    // --- Mocks for the new Provider interfaces ---
    private lateinit var customSmsRuleProvider: CustomSmsRuleProvider
    private lateinit var merchantRenameRuleProvider: MerchantRenameRuleProvider
    private lateinit var ignoreRuleProvider: IgnoreRuleProvider
    private lateinit var merchantCategoryMappingProvider: MerchantCategoryMappingProvider


    @Before
    fun setUp() {
        // Create anonymous implementations of the providers that use our DAO mocks.
        // This adapts the test to the new decoupled architecture.
        customSmsRuleProvider = object : CustomSmsRuleProvider {
            override suspend fun getAllRules(): List<CustomSmsRule> = mockCustomSmsRuleDao.getAllRules().first()
        }
        merchantRenameRuleProvider = object : MerchantRenameRuleProvider {
            override suspend fun getAllRules(): List<MerchantRenameRule> = mockMerchantRenameRuleDao.getAllRules().first()
        }
        ignoreRuleProvider = object : IgnoreRuleProvider {
            override suspend fun getEnabledRules(): List<IgnoreRule> = mockIgnoreRuleDao.getEnabledRules()
        }
        merchantCategoryMappingProvider = object : MerchantCategoryMappingProvider {
            override suspend fun getCategoryIdForMerchant(merchantName: String): Int? =
                mockMerchantCategoryMappingDao.getCategoryIdForMerchant(merchantName)
        }
    }

    private suspend fun setupTest(
        customRules: List<CustomSmsRule> = emptyList(),
        renameRules: List<MerchantRenameRule> = emptyList(),
        ignoreRules: List<IgnoreRule> = emptyList()
    ) {
        // Mock the DAO methods. The providers above will then use these mocks.
        `when`(mockCustomSmsRuleDao.getAllRules()).thenReturn(flowOf(customRules))
        `when`(mockMerchantRenameRuleDao.getAllRules()).thenReturn(flowOf(renameRules))
        `when`(mockIgnoreRuleDao.getEnabledRules()).thenReturn(ignoreRules.filter { it.isEnabled })
    }

    @Test
    fun `test parses HDFC UPI sent message`() = runBlocking {
        setupTest()
        val smsBody = "Sent Rs.11.00\nFrom HDFC Bank A/C *1243\nTo Raju\nOn 07/08/25\nRef 558523453508\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"
        val mockSms = SmsMessage(id = 18L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses ICICI debit with ACH and Avl Bal`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acc XX244 debited Rs. 8,700.00 on 02-Aug-25 InfoACH*ZERODHA B.Avl Bal Rs. 3,209.31.To dispute call 18002662 or SMS BLOCK 646 to 9215676766"
        val mockSms = SmsMessage(id = 17L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses debit message with foreign currency code`() = runBlocking {
        setupTest()
        val smsBody = "You have spent MYR 55.50 at STARBUCKS."
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

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
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(120.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
    }

    @Test
    fun `test parses debit message with home currency symbol`() = runBlocking {
        setupTest()
        val smsBody = "You have spent Rs. 300 at a local store."
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(300.0, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
    }

    @Test
    fun `test parses debit message with no currency`() = runBlocking {
        setupTest()
        val smsBody = "A purchase of 500.00 was made at some store."
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(500.00, result?.amount)
        assertNull("Detected currency should be null", result?.detectedCurrencyCode)
    }

    @Test
    fun `test parses ICICI debit message with Rs symbol correctly`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acct XX823 debited for Rs 240.00 on 28-Jul-25; DAKSHIN CAFE credited. UPI: 552200221100. Call 18002661 for dispute. SMS BLOCK 823 to 123123123"
        val mockSms = SmsMessage(id = 15L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(240.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
        assertEquals("expense", result?.transactionType)
        assertEquals("DAKSHIN CAFE", result?.merchantName)
    }

    @Test
    fun `test prioritizes amount with currency over other numbers`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Acct XX823 debited for Rs 240.00 on 28-Jul-25; DAKSHIN CAFE credited."
        val mockSms = SmsMessage(id = 16L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(240.00, result?.amount)
        assertEquals("INR", result?.detectedCurrencyCode)
    }


    @Test
    fun `test parses debit message successfully`() = runBlocking {
        setupTest()
        val smsBody = "Your account with HDFC Bank has been debited for Rs. 750.50 at Amazon on 22-Jun-2025."
        val mockSms = SmsMessage(id = 1L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())

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
        val mockSms = SmsMessage(id = 2L, sender = "DM-SOMEBK", body = smsBody, date = System.currentTimeMillis())

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

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

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNull("Parser should return null for a non-financial message", result)
    }

    @Test
    fun `test parses SBI Credit Card message`() = runBlocking {
        setupTest()
        val smsBody = "Rs.267.00 spent on your SBI Credit Card ending with 3201 at HALLI THOTA on 29-06-25 via UPI (Ref No. 1231230123). Trxn. Not done by you? Report at [https://sbicards.com/Dispute](https://sbicards.com/Dispute))"
        val mockSms = SmsMessage(id = 4L, sender = "VM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

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
    fun `test parses HDFC card message`() = runBlocking {
        setupTest()
        val smsBody = "[JD-HDFCBK-S] Spent Rs.388.19 On HDFC Bank Card 9922 At ..MC DONALDS_ on2025-06-22:08:01:24.Not You> To Block+Reissue Call 18002323232/SMS BLOCK CC 9922 to 123098123"
        val mockSms = SmsMessage(id = 6L, sender = "JD-HDFCBK", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses Pluxee Meal Card message`() = runBlocking {
        setupTest()
        val smsBody = "Rs. 60.00 spent from Pluxee Meal Card wallet, card no.xx1345 on 30-06-2025 18:41:56 at KITCHEN AFF . Avl bal Rs.1824.65. Not you call 18002106919"
        val mockSms = SmsMessage(id = 7L, sender = "VD-PLUXEE", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses ICICI credit message with tricky format`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Acct XX823 is credited with Rs 6000.00 on 26-Jun-25 from GANGA MANGA. UPI:5577822323232-ICICI Bank"
        val mockSms = SmsMessage(id = 8L, sender = "QP-ICIBNK", body = smsBody, date = System.currentTimeMillis())
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
    fun `test ignores successful payment confirmation`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "payment of.*is successful", isEnabled = true)))
        val smsBody = "Your payment of Rs.330.80 for A4-108 against Water Charges is successful. Regards NoBrokerHood"
        val mockSms = SmsMessage(id = 10L, sender = "VM-NBHOOD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore successful payment confirmations", result)
    }

    @Test
    fun `test parses ICICI NEFT credit message`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Account XX823 credited:Rs. 1,133.00 on 01-Jul-25. Info NEFT-HDFCN5202507024345356218-. Available Balance is Rs. 1,858.35."
        val mockSms = SmsMessage(id = 11L, sender = "VM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

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
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore has been credited to messages", result)
    }

    @Test
    fun `test ignores credit card payment confirmation`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "payment of.*has been received towards", isEnabled = true)))
        val smsBody = "Payment of INR 1180.01 has been received towards your SBI card XX1121"
        val mockSms = SmsMessage(id = 13L, sender = "DM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore credit card payment confirmations", result)
    }

    @Test
    fun `test ignores bharat bill pay confirmation`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "Payment of.*has been received on your.*Credit Card", isEnabled = true)))
        val smsBody = "Payment of Rs 356.33 has been received on your ICICI Bank Credit Card XX2529 through Bharat Bill Payment System on 03-JUL-25."
        val mockSms = SmsMessage(id = 14L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore Bharat Bill Pay confirmations", result)
    }

    @Test
    fun `test ignores message with user-defined ignore phrase`() = runBlocking {
        val ignoreRules = listOf(IgnoreRule(id = 1, pattern = "invoice of", isEnabled = true))
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "An Invoice of Rs.330.8 for A4 Block-108 is raised."
        val mockSms = SmsMessage(id = 9L, sender = "VM-NBHOOD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore messages with user-defined phrases", result)
    }

    @Test
    fun `test parses transaction reversal as income`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, your ICICI Bank Account XXX508 has been credited with Rs 208.42 on 03-Sep-22 as reversal of transaction with UPI: 224679541058."
        val mockSms = SmsMessage(id = 101L, sender = "DM-ICIBNK", body = smsBody, date = System.currentTimeMillis())
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
        val mockSms = SmsMessage(id = 102L, sender = "VM-SODEXO", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(1250.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Sodexo Card", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses new HDFC Credit Card format`() = runBlocking {
        setupTest()
        val smsBody = "You've spent Rs.349 On HDFC Bank CREDIT Card xx1335 At RAZ*StickON..."
        val mockSms = SmsMessage(id = 103L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(349.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("RAZ*StickON", result?.merchantName)
        assertEquals("HDFC Bank CREDIT Card - xx1335", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test ignores Paytm Wallet payment`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "from Paytm Balance", isDefault = true, isEnabled = true)))
        val smsBody = "Paid Rs.1559 to Reliance Jio from Paytm Balance. Updated Balance: Paytm Wallet- Rs 0."
        val mockSms = SmsMessage(id = 104L, sender = "VM-Paytm", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNull("Parser should ignore payments from Paytm Balance", result)
    }

    @Test
    fun `test ignores declined transaction`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "is declined", isDefault = true, isEnabled = true)))
        val smsBody = "Your transaction on HDFC Bank card for 245.00 at AMAZON is declined due to wrong input of ExpiryDate or CVV."
        val mockSms = SmsMessage(id = 105L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNull("Parser should ignore declined transactions", result)
    }

    @Test
    fun `test ignores AutoPay mandate setup`() = runBlocking {
        setupTest(ignoreRules = listOf(IgnoreRule(pattern = "AutoPay (E-mandate) Active", isDefault = true, isEnabled = true)))
        val smsBody = "AutoPay (E-mandate) Active! Merchant: YouTube... On HDFC Bank Credit Card xx1335"
        val mockSms = SmsMessage(id = 106L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNull("Parser should ignore AutoPay setup confirmations", result)
    }

    @Test
    fun `test parses Bank of Baroda UPI transfer message`() = runBlocking {
        setupTest()
        val smsBody = "Rs.1656 transferred from A/c ...7656 to:UPI/429269093079. Total Bal:Rs.6114.76CR. Avlbl Amt:Rs.6114.76(18-10-2024 22:52:46) - Bank of Baroda"
        val mockSms = SmsMessage(id = 501L, sender = "CP-BOBTXN", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(1656.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("UPI/429269093079", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Bank of Baroda - ...7656", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    // =================================================================================
    // NEW TEST CASES FOR PARSING ACCURACY
    // =================================================================================

    @Test
    fun `test parses amount correctly when currency code is attached`() = runBlocking {
        setupTest()
        val smsBody = "Dear 919480XXX, your wallet A/c is successfully Credited with 15000INR on 11-11-2024 13:34:40. Check now - http://Kx10.in/FICGXU/ePh6Rv Finance Guru"
        val mockSms = SmsMessage(id = 401L, sender = "VM-FINGRU", body = smsBody, date = System.currentTimeMillis())
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
        val mockSms = SmsMessage(id = 402L, sender = "VM-BOB", body = smsBody, date = System.currentTimeMillis())
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
        val mockSms = SmsMessage(id = 403L, sender = "VM-BOB", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(6327.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("AIIMS JODHPUR", result?.merchantName)
    }

    // =================================================================================
    // NEW TEST CASES FOR BATCH 2 OF PARSER INCONSISTENCIES
    // =================================================================================

    @Test
    fun `test ignores HDFC payee modification alert`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        // --- UPDATED: Use the more realistic SMS body that was failing ---
        val smsBody = "You've added/modified a payee Prajwal Madhyastha K P with A/c XX8207 via HDFC Bank Online Banking. Not you?Call 18002586161"
        val mockSms = SmsMessage(id = 301L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore payee modification alerts", result)
    }

    @Test
    fun `test ignores Airtel recharge credited confirmation`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Hi, recharge of Rs. 859 successfully credited to your Airtel number7793484112, also the validity has been extended till 03-05-2025."
        val mockSms = SmsMessage(id = 302L, sender = "VM-AIRTEL", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore recharge credited confirmations", result)
    }

    @Test
    fun `test ignores insurance policy conversion alert`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your Axis Max Life Insurance Limited policy 174171785 successfully converted & added to eIA 8100144160103. Check Bima Central app.bimacentral.in for more details. -Bima Central -CAMS Insurance Repository"
        val mockSms = SmsMessage(id = 303L, sender = "CP-BIMACN", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore insurance policy conversion alerts", result)
    }

    @Test
    fun `test ignores failed payment notification`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Hi, Payment of Rs. 33.0 has failed for your Airtel Mobile7793484112. Any amount, if debited will be refunded to your source account within a day. Please keep order ID 7328718104265809920 for reference."
        val mockSms = SmsMessage(id = 304L, sender = "VM-AIRTEL", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore failed payment notifications", result)
    }

    @Test
    fun `test ignores bonus points notification`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "WE'VE ADDED 501 BONUS POINTS! The Levi's Blue Jean celebration is here. Redeem your 601.0 points at your nearest Levi's store or on Levi.in before 27/5 *T&C"
        val mockSms = SmsMessage(id = 305L, sender = "TX-LEVIS", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore bonus points notifications", result)
    }

    @Test
    fun `test ignores Delhivery authentication code message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your SBI Card sent via Delhivery Reference No EV25170653359 will be attempted today. Pls provide this Delivery Authentication Code 3832 - Delhivery"
        val mockSms = SmsMessage(id = 306L, sender = "VM-DliVry", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore delivery authentication code messages", result)
    }

    @Test
    fun `test ignores Amazon gift card voucher code message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Dear SimplyCLICK Cardholder, e-code of your Amazon.in Gift Card is given below. Visit amazon.in/addgiftcard to add the e-code to your Amazon Pay Balance before claim date given below.Voucher Code for Rs.500 is G9OUF-C1I65-B33R2 to be added by 27/11/2025. Valid for 12 months from the date of adding the e-code.TnC Apply."
        val mockSms = SmsMessage(id = 307L, sender = "DM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore gift card voucher code messages", result)
    }

    @Test
    fun `test ignores Gruha Jyoti scheme application message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your Application No. GJ005S250467224 for Gruha Jyoti Scheme received & sent to your ESCOM for Processing. For quarries please dial 1912 From-Seva Sindhu"
        val mockSms = SmsMessage(id = 308L, sender = "IM-SEVSIN", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore scheme application messages", result)
    }

    @Test
    fun `test ignores Bhima promotional message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Step into the magic of Bhima Brilliance Diamond Jewellery Festival, from 7th to 27th July 2025. Treat yourself with Flat Rs.7,000 off per carat of dazzling diamonds. And that's not all. Get free gold or silver jewellery worth upto Rs.2,00,000 on purchase of diamond jewellery. For more details, call us on 18001219076 or click https://vm.ltd/BHIMAJ/Y-UKm7 to know more. T&C apply."
        val mockSms = SmsMessage(id = 309L, sender = "VM-BHIMAJ", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore promotional messages with monetary offers", result)
    }


    @Test
    fun `test ignores Navi Mutual Fund unit allotment message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Unit Allotment Update: Your request for purchase of Rs.6,496.18 in Navi Liquid Fund GDG has been processed at applicable NAV. The units will be alloted in 1-2 working days.For further queries, please visit the Navi app.Navi Mutual Fund"
        val mockSms = SmsMessage(id = 201L, sender = "VM-NAVI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore mutual fund allotment messages", result)
    }

    @Test
    fun `test ignores SBI Credit Card e-statement notification`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "E-statement of SBI Credit Card ending XX76 dated 09/08/2025 has been mailed. If not received, SMS ENRS to 5676791. Total Amt Due Rs 8153; Min Amt Due Rs 200; Payable by 29/08/2025. Click https://sbicard.com/quickpaynet to pay your bill"
        val mockSms = SmsMessage(id = 202L, sender = "DM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore e-statement notifications", result)
    }

    @Test
    fun `test ignores Orient Exchange order received confirmation`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your order is received at our Bangalore - Basavangudi branch. Feel free to contact at 080-26677118/080-26677113 for any clarification. Orient Exchange"
        val mockSms = SmsMessage(id = 203L, sender = "VM-ORIENT", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore non-financial 'order received' messages", result)
    }

    @Test
    fun `test ignores MakeMyTrip booking confirmation`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Dear Kavyashree,\n\nThank you for booking with us. We are happy to inform you that your flight with booking ID NF2A9N4J42556272467 is confirmed.\n\nYour Trip Details:\n\nDeparture Flight: Jodhpur-Bangalore | Date: 26 Sep 24 | Add-ons: JDH-BLR, 6E-6033(Seats: 26B)\n\nTotal amount paid: INR 9,266.00\n\nYou can manage your booking at https://app.mmyt.co/Xm2V/1iur6n6j\n\nWe wish you a safe journey.\nTeam MakeMyTrip"
        val mockSms = SmsMessage(id = 601L, sender = "VM-MMTRIP", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore flight booking confirmations", result)
    }

    @Test
    fun `test ignores promotional wallet credit message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Hi 9480XXXX67, Rs.15,OOO/- is Credited on your wallet account. Join Now to withdrawal directly: SR3.in/O13A0-2351A564B"
        val mockSms = SmsMessage(id = 602L, sender = "CP-WALLET", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore promotional wallet credit messages", result)
    }

    @Test
    fun `test ignores HDFC Welcome Kit delivery confirmation`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Delivered! Your HDFC Bank Welcome Kit Awb -33906843213 has been delivered. It was received by KAVYASHREEBHAT on 14/10/2024."
        val mockSms = SmsMessage(id = 603L, sender = "VM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore welcome kit delivery confirmations", result)
    }

    @Test
    fun `test ignores suspected spam Rummy message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Suspected Spam : Congrats 94808xxxxx, Am0unt 0f Rs.15OOO can be Successfully Credited T0 Y0ur Rummy A/C. Withdraw N0w: 7kz1.com/bh2e5bdp4l"
        val mockSms = SmsMessage(id = 604L, sender = "CP-RUMMY", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore messages marked as suspected spam", result)
    }

    @Test
    fun `test ignores informational NEFT sent message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "NEFT from Ac X3377 for Rs.10,000.00 with UTR SBIN525080708724 dt 21.03.25 to My hdf Ac X6982 with HDFC0005075 sent from YONO. If not done by you fwd this SMS to3711961089 or call9113274461 - SBI."
        val mockSms = SmsMessage(id = 605L, sender = "VM-SBINEF", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore informational NEFT sent messages", result)
    }

    @Test
    fun `test ignores informational NEFT credited to beneficiary message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Dear Customer, Your NEFT of Rs 20,000.00 with UTR SBIN425127845277 DTD 07/05/2025 credited to Beneficiary AC NO. XX6982 at HDFC0005075 on 07/05/2025 at 01:38 PM.-SBI"
        val mockSms = SmsMessage(id = 606L, sender = "VM-SBINEF", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore informational NEFT credited messages", result)
    }

    @Test
    fun `test ignores Zepto refund processed message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Dear user, \nRefund of Rs 277.51 has been processed for your Zepto order KVKQRLNK98664. Refund Ref. num. 514321799294. You can check details on Refunds page in the app."
        val mockSms = SmsMessage(id = 607L, sender = "VM-ZEPTO", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore refund processed notifications", result)
    }

    @Test
    fun `test ignores promotional birthday offer`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Yay!! Its your BIRTHDAY week\nget FLAT 25% OFF on purchase of Rs. 2000 on your shopping @UNLIMITED Fashion store. Hurry!\nCode: PAZKY8A272\nValid: till 05-Jun-25.TnC"
        val mockSms = SmsMessage(id = 608L, sender = "VM-UNLTD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore promotional birthday offers", result)
    }

    @Test
    fun `test ignores SBI Card delivery confirmation`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Your SBI Card has been delivered through Delhivery and received by MS KAVYASHREE BHAT Reference No EV25151188648 - Delhivery"
        val mockSms = SmsMessage(id = 609L, sender = "VM-DliVry", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore card delivery confirmations", result)
    }

    @Test
    fun `test ignores promotional offer reminder`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Alert: Your FLAT 25% OFF on purchase of Rs. 2000 is valid for 5 DAYS ONLY\nEnjoy your BIRTHDAY shopping @UNLIMITED Fashion store.\nCode: PAZKY8A272\nTnC"
        val mockSms = SmsMessage(id = 610L, sender = "VM-UNLTD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore promotional offer reminders", result)
    }

    @Test
    fun `test ignores payee modification without the word 'a'`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "You've added/modified payee Nithyananda Bhat with A/c XX8305 via HDFC Bank Online Banking. Not you?Call 18002586161"
        val mockSms = SmsMessage(id = 611L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Should ignore payee modification without 'a'", result)
    }

    @Test
    fun `test parses HDFC deposit with 'in' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Update! INR 11,000.00 deposited in HDFC Bank A/c XX2536 on 15-OCT-24 for XXXXXXXXXX0461-TPT-Transfer-PRAJWAL MADHYASTHA K P.Avl bal INR 26,475.53. Cheque deposits in A/C are subject to clearing"
        val mockSms = SmsMessage(id = 701L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses BOB debit with 'from' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Rs 918.00 debited from A/C XXXXXX9130 and credited to user12@apl UPI Ref:466970233165. Not you? Call 18005700 -BOB"
        val mockSms = SmsMessage(id = 702L, sender = "VM-BOB", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses HDFC card spend with 'on' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Rs.6175 spent on HDFC Bank Card x2477 at ..SUDARSHAN FAMILY_ on 2024-11-13:17:18:38.Not U? To Block & Reissue Call 18002586161/SMS BLOCK CC 2477 to8216821344"
        val mockSms = SmsMessage(id = 703L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
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
        val mockSms = SmsMessage(id = 704L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
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
        val mockSms = SmsMessage(id = 705L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(2.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("VPA user34@axisbank", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c xx2536", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses HDFC NEFT deposit with 'in' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Update! INR 30,000.00 deposited in HDFC Bank A/c XX2536 on 15-FEB-25 for NEFT Cr-SBIN0007631-Kavyashree Bhat-My hdfc-SBINN52025021511431836.Avl bal INR 55,727.04. Cheque deposits in A/C are subject to clearing"
        val mockSms = SmsMessage(id = 706L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses SBI debit with 'Your A_c' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Your A/c XX9258 has been debited with INR 15,000.00 on 19/02/2025 towards NEFT with UTR SBIN125050589474 sent to My hdfc HDFC0005075-SBI"
        val mockSms = SmsMessage(id = 707L, sender = "VM-SBINEF", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(15000.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("My hdfc HDFC0005075", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("A/c XX9258", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    // =================================================================================
    // NEW TEST CASES FOR ACCOUNT PARSING
    // =================================================================================

    @Test
    fun `test parses HDFC income with 'in your' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Money Received - INR 1.00 in your HDFC Bank A/c xx2536 on 17-12-24 by A/c linked to mobile no xx4455 (IMPS Ref No. 435202444399) Avl bal: INR 14,565.00"
        val mockSms = SmsMessage(id = 801L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
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
        val mockSms = SmsMessage(id = 802L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
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
        val mockSms = SmsMessage(id = 803L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals("expense", result?.transactionType)
        assertNotNull(result?.potentialAccount)
        assertEquals("HDFC Bank A/c **6982", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI Credit Card without the word 'with'`() = runBlocking {
        setupTest()
        val smsBody = "Rs.2.00 spent on your SBI Credit Card ending 8260 at SWIGGY LIMITED on 04/06/25. Trxn. not done by you? Report at https://sbicard.com/Dispute"
        val mockSms = SmsMessage(id = 804L, sender = "DM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals("expense", result?.transactionType)
        assertNotNull(result?.potentialAccount)
        assertEquals("SBI Credit Card 8260", result?.potentialAccount?.formattedName)
        assertEquals("Credit Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI income with 'Your A_C' format`() = runBlocking {
        setupTest()
        val smsBody = "Your A/C XXXXX650077 Credited INR 1,41,453.00 on 15/07/25 -Deposit by transfer from ESIC MODEL HOSP. RJJ. Avl Bal INR 3,89,969.28-SBI"
        val mockSms = SmsMessage(id = 805L, sender = "VM-SBINEF", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals("income", result?.transactionType)
        assertNotNull(result?.potentialAccount)
        assertEquals("A/C XXXXX650077", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test ignores RTGS informational message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "RTGS Money Deposited~INR 3,00,000.00~To Nithyananda Bhat~Txn No: HDFCR52025041560739217~On 15-04-2025 at 07:52:07~-HDFC Bank"
        val mockSms = SmsMessage(id = 806L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull(result)
    }

    @Test
    fun `test ignores Trends Footwear points message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Rs500 worth points credited in ur AC Use code TRFWSWEJXF3107 by 20Jul to redeem on Rs1250 user152@TRENDS FOOTWEAR Visit@ https://vil.ltd/TRNDFW/c/stlcjul T&C"
        val mockSms = SmsMessage(id = 807L, sender = "VM-TRENDS", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull(result)
    }
}
