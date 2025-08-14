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
        // --- FIX: Use default ignore rules by default to make tests more realistic ---
        ignoreRules: List<IgnoreRule> = DEFAULT_IGNORE_PHRASES
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
        assertEquals("user34@axisbank", result?.merchantName)
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
        val smsBody = "Your A/C XXXXX650077 Credited INR 1,41,453.00 on 15/07/25 -Deposit by transfer from ESIC MODEL HOSP. Avl Bal INR 3,89,969.28-SBI"
        val mockSms = SmsMessage(id = 805L, sender = "VM-SBINEF", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals("income", result?.transactionType)
        assertEquals(141453.00, result?.amount)
        assertEquals("ESIC MODEL HOSP", result?.merchantName)
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

    // =================================================================================
    // NEW TEST CASES FOR UNCAPTURED TRANSACTIONS
    // =================================================================================

    @Test
    fun `test parses SBI UPI expense with 'frm' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Rs200.0 user27@SBI UPI frm A/cX0763 on 04Jun21 RefNo 115563968049. If not done by u, fwd this SMS to0680397940/Call9169070230 or 09449112211 to block UPI"
        val mockSms = SmsMessage(id = 901L, sender = "BX-SBIUPI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(200.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("user27@SBI", result?.merchantName)
        assertEquals("A/cX0763", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses SBI salary credit with 'has credit' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Your A/C XXXXX436715 has credit for BY SALARY of Rs 1,161.00 on 16/11/21. Avl Bal Rs 0,87,695.52.-SBI"
        val mockSms = SmsMessage(id = 902L, sender = "BP-CBSSBI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(1161.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("BY SALARY", result?.merchantName)
        assertEquals("A/C XXXXX436715", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses SBI debit with 'debited by' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Dear SBI User, your A/c X0763-debited by Rs300.0 on 13Apr22 transfer to PERUMAL C Ref No 210321346095. If not done by u, fwd this SMS to0680397940/Call9169070230 or 09449112211 to block UPI -SBI"
        val mockSms = SmsMessage(id = 903L, sender = "BP-SBIUPI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(300.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("PERUMAL C", result?.merchantName)
        assertEquals("A/c X0763", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses Dept of Posts credit with 'CREDIT with amount' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Account  No. XXXXXX5755 CREDIT with amount Rs. 5700.00 on 30-12-2022. Balance: Rs.216023.00. [ S3256155]"
        val mockSms = SmsMessage(id = 904L, sender = "BH-DOPBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(5700.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Account No. XXXXXX5755", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses Canara Bank credit with 'CREDITED to your account' format`() = runBlocking {
        setupTest()
        val smsBody = "An amount of INR 20,000.00 has been CREDITED to your account XXX810 on 03/05/2023 towards Cash Deposit. Total Avail.bal INR 1,12,210.00. - Canara Bank"
        val mockSms = SmsMessage(id = 905L, sender = "AD-CANBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(20000.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Cash Deposit", result?.merchantName)
        assertEquals("Canara Bank - account XXX810", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses SBI debit with 'has a debit by transfer' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Your A/C XXXXX436715 has a debit by transfer of Rs 147.50 on 19/06/23. Avl Bal Rs 3,58,354.26.-SBI"
        val mockSms = SmsMessage(id = 906L, sender = "BW-CBSSBI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull(result)
        assertEquals(147.50, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("A/C XXXXX436715", result?.potentialAccount?.formattedName)
    }

    // --- NEW: Test cases for the three newly identified failing messages ---

    @Test
    fun `test parses SBI credit with 'has a credit by Transfer' format`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Your A/C XXXXX436715 has a credit by Transfer of Rs 3,710.00 on 31/05/22 by Bank. Avl Bal Rs 4,98,955.01.-SBI"
        val mockSms = SmsMessage(id = 1653998981316L, sender = "JK-CBSSBI", body = smsBody, date = 1653998981316L)
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(3710.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Bank", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("SBI - A/C XXXXX436715", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses Canara Bank credit with 'has been CREDITED to your account'`() = runBlocking {
        setupTest()
        val smsBody = "An amount of INR 2,000.00 has been CREDITED to your account XXX810 on 17/03/2023 towards Cash Deposit. Total Avail.bal INR 2,000.00. - Canara Bank"
        val mockSms = SmsMessage(id = 1679042011072L, sender = "CP-CANBNK", body = smsBody, date = 1679042011072L)
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

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
        val mockSms = SmsMessage(id = 1750144389022L, sender = "JK-CANBNK-S", body = smsBody, date = 1750144389022L)
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(25000.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("CASH", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("Canara Bank - A/c XXX810", result?.potentialAccount?.formattedName)
    }

    // --- NEW: Test cases for incorrectly parsed non-financial messages ---

    @Test
    fun `test ignores Jio activation message`() = runBlocking {
        setupTest()
        val smsBody = "Your number0151027867 will be activated on Jio network by 6:00 AM. Once your existing SIM stops working, please insert the Jio SIM in SIM slot 1 of your smartphone and do Tele-verification by dialling 1977. To know your device settings, please refer to earlier settings SMS sent by us or click https://youtu.be/o18LboDi1ho\nIf you are an eSIM user, use the QR code to download and install the eSIM profile on your device before Tele-verification. You can also set eSIM profile manually by referring to the SMS sent to your number when eSIM request was given.\nPrepaid users, need to do a recharge after activation to start using Jio services. To recharge, click www.jio.com/rechargenow or Visit the nearest Jio retailer after activation of your number. For details of exciting recharge plans, call 1991 from your Jio SIM after activation or 18008900000 from any number. Please note, Tele-verification is not required for Jio Corporate connections."
        val mockSms = SmsMessage(id = 1L, sender = "JK-JioSvc", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore Jio activation messages", result)
    }

    @Test
    fun `test ignores Bank of Baroda informational message`() = runBlocking {
        setupTest()
        val smsBody = "Funds credited in your account by NEFT/RTGS with VIJB0001040 received from L+T CONSTRUCTION EQUIPMENT LIMITED. Please advise your remitter to use new IFSC only-BARB0VJDASC(Fifth digit is ZER0) Team Bank of Baroda"
        val mockSms = SmsMessage(id = 2L, sender = "VD-BOBSMS", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore informational IFSC messages", result)
    }

    @Test
    fun `test ignores SBI Life premium payment message`() = runBlocking {
        setupTest()
        val smsBody = "Thank you for premium payment of Rs.100000. on 06-11-2021 for Policy 1H461419008 through State Bank. Receipt will be sent shortly-SBI Life."
        val mockSms = SmsMessage(id = 3L, sender = "JM-SBLIFE", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore SBI Life premium messages", result)
    }

    @Test
    fun `test ignores DICGCI insurance claim message`() = runBlocking {
        setupTest()
        val smsBody = "Insurance claim u/s 18A of DICGC Act received from S G Raghvendra CBL. Submit willingness, alternate bank AC or Adhaar details to bank for payment"
        val mockSms = SmsMessage(id = 4L, sender = "JD-DICGCI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore DICGCI claim messages", result)
    }

    @Test
    fun `test ignores MyGovt RT-PCR sample message`() = runBlocking {
        setupTest()
        val smsBody = "RT-PCR sample collected for MANJULA PRAVEEN (Id:2952525386749) SRF ID 2952525695921  on Jan 18 2022 12:06PM. Please save for future reference. You are advised to isolate yourself till the sample is tested and test report is provided by the Lab. https://covid19cc.nic.in/PDFService/Specimenfefform.aspx?formid=Ukx%2bYrhm8Hq2EzLtOF%2fpWg%3d%3d Your Sample has been sent to Sagar Hospital, Jayanagar, Bangalore"
        val mockSms = SmsMessage(id = 5L, sender = "VM-MYGOVT", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore RT-PCR sample messages", result)
    }

    @Test
    fun `test ignores IndiaFirst premium debit notification`() = runBlocking {
        setupTest()
        val smsBody = "Renewal premium of Rs.330 for Pradhan Mantri Jeevan Jyoti Bima Yojana will be debited from your account. Pls maintain sufficient balance in a/c 74370100011143 for auto-debit.IFL"
        val mockSms = SmsMessage(id = 6L, sender = "BP-IndiaF", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore future debit notifications", result)
    }

    @Test
    fun `test ignores SBI OTP message`() = runBlocking {
        setupTest()
        val smsBody = "OTP for online purchase of Rs. 8140.50 at AMAZON thru State Bank Debit Card 6074*****17 is 512192. Do not share this with anyone."
        val mockSms = SmsMessage(id = 7L, sender = "BZ-SBIOTP", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore OTP messages", result)
    }

    // --- NEW: Test cases for the latest batch of non-financial messages ---

    @Test
    fun `test ignores Lok Adalat Notice`() = runBlocking {
        setupTest()
        val smsBody = "This is a Lok Adalat Notice issued by District Legal Services Authority, Bengaluru Urban http://122.185.94.100:3000/lokadalt/notification?regno=KA04P4631&noticeno=508412.  You are called upon to pay the fine amount of Rs. 500  for the traffic violation against vehicle Reg. No: KA04P4631.  The fine shall be deposited via online or offline mode as mentioned in the notice to avoid legal action From DLSA and Bangalore Traffic Police"
        val mockSms = SmsMessage(id = 1001L, sender = "VM-LOKADL", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore Lok Adalat notices", result)
    }

    @Test
    fun `test ignores Fixed Deposit closure message`() = runBlocking {
        setupTest()
        val smsBody = "Your FD  a/c XXXXX7047 is closed on 05.12.2022 and proceeds are credited to a/c XXXXX5945 .Contact branch immediately, if not requested by you. -IndianBank"
        val mockSms = SmsMessage(id = 1002L, sender = "BT-INDBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore FD closure messages", result)
    }

    @Test
    fun `test ignores Porter wallet credit message`() = runBlocking {
        setupTest()
        val smsBody = "Hi, PORTER has credited Rs.200 (Code: FIRST50) in your wallet for 1st truck order! Send parcel using bike at Rs.30 @ weurl.co/bqHMj9"
        val mockSms = SmsMessage(id = 1003L, sender = "TX-PORTER", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore Porter promotional wallet credits", result)
    }

    @Test
    fun `test ignores bob World maintenance message`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer: bob World will be unavailable frm 11:30PM on 07-01-23 to 06:30AM on 08-01-23 for a System Upgrade. Pl use Net Banking or UPI for urgent txn-BOB"
        val mockSms = SmsMessage(id = 1004L, sender = "VD-BOBSMS", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore system maintenance messages", result)
    }

    @Test
    fun `test ignores Apollo Pharmacy promotional message`() = runBlocking {
        setupTest()
        val smsBody = "Get Free Stainless Steel Water Bottle on purchase of Medicine plus Apollo brands worth Rs.1000 & above. Visit your nearest Apollo Pharmacy klr.pw/V4HJTc *T&C"
        val mockSms = SmsMessage(id = 1005L, sender = "VM-APOLLO", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore Apollo promotional messages", result)
    }

    @Test
    fun `test ignores JioHealthHub informational message`() = runBlocking {
        setupTest()
        val smsBody = "More than 36 million medical records have been added to Health Locker by our users. Go digital. Try Health Locker on JioHealthHub."
        val mockSms = SmsMessage(id = 1006L, sender = "VM-JIOHH", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore JioHealthHub informational messages", result)
    }

    @Test
    fun `test ignores India Post delivery message`() = runBlocking {
        setupTest()
        val smsBody = "Article No:JB955870859IN received @ Bengaluru NSH on 23/03/2023 20:08:32.Track @ www.indiapost.gov.in"
        val mockSms = SmsMessage(id = 1007L, sender = "AD-IndPst", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore India Post delivery messages", result)
    }

    @Test
    fun `test ignores Canara Bank cheque book delivery message`() = runBlocking {
        setupTest()
        val smsBody = "Personalised cheques sent to you vide INDIA POST Ref No JB955207542IN and you will receive the same shortly. If not received within 7 days please contact Your Canara Bank Branch. -Canara Bank"
        val mockSms = SmsMessage(id = 1008L, sender = "AD-CANBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore cheque book delivery messages", result)
    }

    @Test
    fun `test ignores Practo family member message`() = runBlocking {
        setupTest()
        val smsBody = "Prajwal Madhyastha K P has added you as a family member on their Marsh - KPMG Health plan 2022. Download the practo app to avail its benefits. prac.to/get-plus\n- Practo"
        val mockSms = SmsMessage(id = 1009L, sender = "VM-PRACTO", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore Practo informational messages", result)
    }

    @Test
    fun `test ignores JioTV promotional message`() = runBlocking {
        setupTest()
        val smsBody = "Get super-charged with great laughs!\nWatch Siddharth Malhotra & Ajay Devgn starrer Thank God on JioTV\nhttps://bit.ly/SMSJioTV4"
        val mockSms = SmsMessage(id = 1010L, sender = "QP-JIOCIN", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore JioTV promotional messages", result)
    }

    @Test
    fun `test ignores My11Circle promotional message`() = runBlocking {
        setupTest()
        val smsBody = "Dear ,\nRs.5000 Bonus to be credited on My11Circle.\nBLR vs UPE\nPrize Pool - 12,07,00,000 (12.07Cr)\nEntry Fees - Rs.49\nClick - http://1kx.in/TNpHVV"
        val mockSms = SmsMessage(id = 1011L, sender = "VM-MY11CE", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore My11Circle promotional messages", result)
    }

    // --- NEW: Test cases for the four informational messages identified ---

    @Test
    fun `test ignores SBIL renewal premium notice`() = runBlocking {
        setupTest()
        val smsBody = "Thank you for the  payment made on 05-NOV-2023 for Rs 100000 against renewal premium of SBIL policy No 1H461419008 Amt. will be credited subject to realization."
        val mockSms = SmsMessage(id = 2001L, sender = "XX-SBILIF", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore SBIL premium notices", result)
    }

    @Test
    fun `test ignores Bank of Baroda email added confirmation`() = runBlocking {
        setupTest()
        val smsBody = "We confirm you that Email Id user285@GMAIL.COM has been added in CUST_ID XXXXX3858 w.e.f 18-01-2024 18:17:29 at your request.-Bank of Baroda"
        val mockSms = SmsMessage(id = 2002L, sender = "XX-BOB", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore email added confirmations", result)
    }

    @Test
    fun `test ignores Jio eSIM activation instructions`() = runBlocking {
        setupTest()
        val smsBody = "To activate your eSIM for Jio Number9656315416, Please follow the below-mentioned steps..." // Truncated for brevity
        val mockSms = SmsMessage(id = 2003L, sender = "XX-JIO", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore eSIM activation instructions", result)
    }

    @Test
    fun `test ignores Godrej real estate advertisement`() = runBlocking {
        setupTest()
        val smsBody = "Received-Rera-Approval & Allotments-Underway Your-2,3&4BHK frm-1.28Cr Add-Godrej Woodscapes, Whitefield-Bangalore Appointment- https://wa.link/godrej-woodscapes"
        val mockSms = SmsMessage(id = 2004L, sender = "XX-GODREJ", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore real estate ads", result)
    }

    // --- NEW: Test cases for the latest batch of incorrectly parsed amounts/accounts ---

    @Test
    fun `test correctly parses Union Bank debit and account`() = runBlocking {
        setupTest()
        val smsBody = "Your SB A/c *3618 Debited for Rs:147.5 on 16-11-2021 16:45:07 by Transfer Avl Bal Rs:21949.7 -Union Bank of India"
        val mockSms = SmsMessage(id = 1L, sender = "AM-UBOI", body = smsBody, date = System.currentTimeMillis())
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
    fun `test correctly parses SBI UPI debit, account, and merchant`() = runBlocking {
        setupTest()
        val smsBody = "Dear UPI user A/C X0763 debited by 185.0 on date 09Dec24 trf to KAGGIS CAKES AND Refno 434416868357. If not u? call9169070230. -SBI"
        val mockSms = SmsMessage(id = 2L, sender = "DM-SBI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals("Amount should be 185.0, not the account number fragment", 185.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("KAGGIS CAKES AND", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("A/C X0763", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    // =================================================================================
    // NEW TEST CASES FOR THIS SESSION
    // =================================================================================

    @Test
    fun `test parses Union Bank credit and account`() = runBlocking {
        setupTest()
        val smsBody = "Your SB A/c **23618 is Credited for Rs.1743 on 31-01-2021 03:42:47 by Transfer. Avl Bal Rs:5811.3 -Union Bank of India DOWNLOAD U MB HTTP://ONELINK.TO/BUYHR7"
        val mockSms = SmsMessage(id = 1L, sender = "AM-UBOI", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses SBI Debit Card AMC and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Your A/C ending with 9515 has been debited for INR 147.5 on 21-06-21 towards annual maintenance charges for your SBI Debit Card ending with 9417"
        val mockSms = SmsMessage(id = 2L, sender = "VM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(147.5, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("annual maintenance charges", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SBI Debit Card ending with 9417", result?.potentialAccount?.formattedName)
        assertEquals("Debit Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI transfer and account`() = runBlocking {
        setupTest()
        val smsBody = "Your A/C XXXXX293201 Debited INR 27,750.00 on 30/06/21 -Transferred to Mr. Praveen Kumar K. Avl Balance INR 15,00,000.00-SBI"
        val mockSms = SmsMessage(id = 3L, sender = "VM-SBINEF", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(27750.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Mr. Praveen Kumar K", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("A/C XXXXX293201", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    // --- NEW: Test cases for the three specific failing messages ---

    @Test
    fun `test parses L and T construction credit and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, INR 8,204.00 credited to your A/c No XX0763 on 27/10/2021 through NEFT with UTR KKBK213009046241 by L AND T CONSTRUCTION EQUIPMENT LIMITED, INFO: 76108PRAVEEN KUMAR K L-SBI"
        val mockSms = SmsMessage(id = 10L, sender = "AX-KOTAKB", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(8204.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("L AND T CONSTRUCTION EQUIPMENT LIMITED", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("A/c No XX0763", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test ignores 'Received with thanks' receipt message`() = runBlocking {
        setupTest() // Uses the updated default ignore rules
        val smsBody = "Received with thanks Rs 600 /- by receipt number RA2021-22/00000576.If found incorrect write to us user77@gmail.com or Whats App to6919864999"
        val mockSms = SmsMessage(id = 11L, sender = "IX-Update", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)
        assertNull("Parser should ignore the receipt confirmation", result)
    }

    @Test
    fun `test parses DEBIT with amount message and account`() = runBlocking {
        setupTest()
        val smsBody = "Account  No. XXXXXX5755 DEBIT with amount Rs. 15000.00 on 15-07-2022. Balance: Rs.80173.00. [S11997812]"
        val mockSms = SmsMessage(id = 12L, sender = "BH-DOPBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(15000.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertNull("Merchant should be null as it's not present", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("Account No. XXXXXX5755", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    // --- NEW: Test cases for this session's failing messages ---

    @Test
    fun `test parses PNB credit and account`() = runBlocking {
        setupTest()
        val smsBody = "Ac XXXXXXXX00007271 Credited with Rs.200000.00 , 28-11-2022 09:52:52. Aval Bal Rs.241439.80 CR. Helpline 18001802222.Register for e-statement,if not done.-PNB"
        val mockSms = SmsMessage(id = 1L, sender = "IM-PNB", body = smsBody, date = System.currentTimeMillis())
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
    fun `test parses SBI credit with Acc No and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Payment of Rs. 3,710.00 credited to your Acc No. XXXXX769515 on 27/03/25-SBI"
        val mockSms = SmsMessage(id = 2L, sender = "DM-SBICRD", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(3710.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Payment", result?.merchantName) // Fallback merchant
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SBI - Acc No. XXXXX769515", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI ATM withdrawal and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear SBI Customer, Rs.20000 withdrawn at SBI ATM S1BD011351004 from A/cX0763 on 07Aug25 Transaction Number 9661. Available Balance Rs.134906.16. If not withdrawn by you, forward this SMS to3158773679 / call9169070230 or 09449112211 to block your card. Call 18001234 if cash not received."
        val mockSms = SmsMessage(id = 3L, sender = "DM-SBITXN", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(20000.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("SBI ATM S1BD011351004", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SBI - A/cX0763", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    // --- NEW: Test cases for the newly identified failing messages ---
    @Test
    fun `test parses Canara Bank debit and account`() = runBlocking {
        setupTest()
        val smsBody = "An amount of INR 60,000.00 has been DEBITED to your account XXX810 on 27/11/2024 towards Cheque Withdrawal. Total Avail.bal INR 41,928.00. - Canara Bank"
        val mockSms = SmsMessage(id = 1L, sender = "AD-CANBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(60000.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Cheque Withdrawal", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("Canara Bank - account XXX810", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses Dept of Posts credit and account`() = runBlocking {
        setupTest()
        val smsBody = "Account  No. XXXXXXXX1901 CREDIT with amount Rs. 100000.00 on 12-03-2025. Balance: Rs.100000.00. [IN3956101]"
        val mockSms = SmsMessage(id = 2L, sender = "BH-DOPBNK", body = smsBody, date = System.currentTimeMillis())
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
        val mockSms = SmsMessage(id = 3L, sender = "AM-UBOI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(2383.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SB A/c *3618", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }
}
