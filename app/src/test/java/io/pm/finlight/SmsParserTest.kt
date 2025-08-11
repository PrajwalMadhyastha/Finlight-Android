package io.pm.finlight

import io.pm.finlight.core.CustomSmsRule
import io.pm.finlight.core.CustomSmsRuleProvider
import io.pm.finlight.core.DEFAULT_IGNORE_PHRASES
import io.pm.finlight.core.IgnoreRule
import io.pm.finlight.core.IgnoreRuleProvider
import io.pm.finlight.core.MerchantCategoryMappingProvider
import io.pm.finlight.core.MerchantRenameRule
import io.pm.finlight.core.MerchantRenameRuleProvider
import io.pm.finlight.core.ParseResult
import io.pm.finlight.core.RuleType
import io.pm.finlight.core.SmsMessage
import io.pm.finlight.core.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SmsParserTest {

    // Mocks for the provider interfaces required by the refactored SmsParser
    private lateinit var mockCustomSmsRuleProvider: CustomSmsRuleProvider
    private lateinit var mockMerchantRenameRuleProvider: MerchantRenameRuleProvider
    private lateinit var mockIgnoreRuleProvider: IgnoreRuleProvider
    private lateinit var mockMerchantCategoryMappingProvider: MerchantCategoryMappingProvider

    // An empty map to be used in tests where account mappings are not relevant.
    private val emptyMappings = emptyMap<String, Long>()

    /**
     * Sets up the mock providers before each test runs.
     */
    @Before
    fun setup() {
        mockCustomSmsRuleProvider = mock()
        mockMerchantRenameRuleProvider = mock()
        mockIgnoreRuleProvider = mock()
        mockMerchantCategoryMappingProvider = mock()
    }

    /**
     * Helper function to configure the mock providers with specific data for a test.
     */
    private suspend fun setupTest(
        customRules: List<CustomSmsRule> = emptyList(),
        renameRules: List<MerchantRenameRule> = emptyList(),
        ignoreRules: List<IgnoreRule> = emptyList(),
        merchantCategory: Pair<String, Int>? = null
    ) {
        whenever(mockCustomSmsRuleProvider.getAllRules()).thenReturn(customRules)
        whenever(mockMerchantRenameRuleProvider.getAllRules()).thenReturn(renameRules)
        whenever(mockIgnoreRuleProvider.getEnabledRules()).thenReturn(ignoreRules)
        if (merchantCategory != null) {
            whenever(mockMerchantCategoryMappingProvider.getCategoryIdForMerchant(merchantCategory.first))
                .thenReturn(merchantCategory.second)
        }
    }

    @Test
    fun `test simple debit transaction`() = runBlocking {
        setupTest() // Setup with default empty rules
        val smsBody = "Rs.500 debited from your account."
        val mockSms = SmsMessage(id = 1L, sender = "Bank", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(500.0, transaction.amount, 0.0)
        assertEquals("debit", transaction.transactionType)
    }

    @Test
    fun `test ignores otp messages`() = runBlocking {
        // Use the default ignore phrases which include "OTP"
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Your OTP is 123456."
        val mockSms = SmsMessage(id = 2L, sender = "Service", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Parser should ignore OTP messages", result is ParseResult.Ignored)
    }

    @Test
    fun `test applies custom sms rule`() = runBlocking {
        val customRule = CustomSmsRule(
            id = 1,
            senderId = "My-App",
            messagePattern = "Paid \\\$(\\d+\\.\\d{2}) to (.+)",
            amountPattern = "\\\$(\\d+\\.\\d{2})",
            merchantPattern = "to (.+)",
            transactionType = "debit"
        )
        setupTest(customRules = listOf(customRule))

        val smsBody = "Paid \$12.34 to Starbucks"
        val mockSms = SmsMessage(id = 3L, sender = "My-App", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(12.34, transaction.amount, 0.0)
        assertEquals("Starbucks", transaction.merchantName)
        assertEquals("debit", transaction.transactionType)
    }

    @Test
    fun `test applies merchant rename rule`() = runBlocking {
        val renameRule = MerchantRenameRule(id = 1, pattern = "AMZNMKTPLACE", newName = "Amazon")
        setupTest(renameRules = listOf(renameRule))

        val smsBody = "Thank you for your purchase of Rs. 1500 at AMZNMKTPLACE."
        val mockSms = SmsMessage(id = 4L, sender = "Bank", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals("Amazon", transaction.merchantName)
    }

    @Test
    fun `test applies merchant category mapping`() = runBlocking {
        setupTest(merchantCategory = Pair("Zomato", 5)) // Assume category ID 5 is 'Food'

        val smsBody = "Rs. 350 spent on Zomato."
        val mockSms = SmsMessage(id = 5L, sender = "Bank", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(5, transaction.categoryId)
    }

    // Additional tests to cover more complex scenarios and edge cases

    @Test
    fun `test transaction with INR currency symbol`() = runBlocking {
        setupTest()
        val smsBody = "INR 2,500.00 has been debited from your account."
        val mockSms = SmsMessage(id = 101L, sender = "Bank", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(2500.0, transaction.amount, 0.0)
        assertEquals("debit", transaction.transactionType)
    }

    @Test
    fun `test transaction with 'spent at' keyword`() = runBlocking {
        setupTest()
        val smsBody = "You have spent Rs. 750 at Coffee Day."
        val mockSms = SmsMessage(id = 102L, sender = "Bank", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(750.0, transaction.amount, 0.0)
        assertEquals("Coffee Day", transaction.merchantName)
    }

    @Test
    fun `test credit or credited transaction`() = runBlocking {
        setupTest()
        val smsBody = "Your account has been credited with Rs. 10000."
        val mockSms = SmsMessage(id = 103L, sender = "Bank", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(10000.0, transaction.amount, 0.0)
        assertEquals("credit", transaction.transactionType)
    }

    @Test
    fun `test refund transaction`() = runBlocking {
        setupTest()
        val smsBody = "A refund of Rs. 850 has been processed to your account from Amazon."
        val mockSms = SmsMessage(id = 104L, sender = "Bank", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(850.0, transaction.amount, 0.0)
        assertEquals("credit", transaction.transactionType) // Refunds are treated as credits
        assertEquals("Amazon", transaction.merchantName)
    }

    @Test
    fun `test ATM withdrawal transaction`() = runBlocking {
        setupTest()
        val smsBody = "Cash withdrawal of INR 5000 from ATM."
        val mockSms = SmsMessage(id = 105L, sender = "Bank", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Result should be a success", result is ParseResult.Success)
        val transaction = (result as ParseResult.Success).transaction
        assertEquals(5000.0, transaction.amount, 0.0)
        assertEquals("debit", transaction.transactionType)
        assertEquals("ATM Withdrawal", transaction.merchantName)
    }

    @Test
    fun `test ignores promotional messages with amounts`() = runBlocking {
        val ignoreRules = listOf(
            IgnoreRule(pattern = "loan offer", type = RuleType.BODY_PHRASE, isDefault = true, isEnabled = true)
        )
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Special loan offer! Get Rs. 50,000 instantly."
        val mockSms = SmsMessage(id = 201L, sender = "Promo", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Parser should ignore promotional messages", result is ParseResult.Ignored)
    }

    @Test
    fun `test ignores delivery confirmation messages`() = runBlocking {
        val ignoreRules = listOf(
            IgnoreRule(pattern = "has been delivered", type = RuleType.BODY_PHRASE, isDefault = true, isEnabled = true)
        )
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your SBI Card has been delivered through Delhivery and received by MS Rani Reference No EV25170653359 - Delhivery"
        val mockSms = SmsMessage(id = 203L, sender = "VM-DLHVRY", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Parser should ignore delivery confirmations", result is ParseResult.Ignored)
    }

    @Test
    fun `test ignores Delhivery delivery attempt notification`() = runBlocking {
        val ignoreRules = listOf(
            IgnoreRule(pattern = "*DLHVRY", type = RuleType.SENDER, isDefault = true, isEnabled = true)
        )
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your SBI Card sent via Delhivery Reference No EV25170653359 will be attempted today. Pls provide this Delivery Authentication Code 3832 - Delhivery"
        val mockSms = SmsMessage(id = 204L, sender = "AM-DLHVRY", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, mockCustomSmsRuleProvider, mockMerchantRenameRuleProvider, mockIgnoreRuleProvider, mockMerchantCategoryMappingProvider)

        assertTrue("Parser should ignore delivery attempts from sender", result is ParseResult.Ignored)
    }
}
