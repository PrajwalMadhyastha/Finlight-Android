// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/SbiSmsParserTest.kt
// REASON: NEW FILE - Contains all unit tests specifically for parsing SMS
// messages from State Bank of India (SBI), refactored from the original
// SmsParserTest.
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

class SbiSmsParserTest : BaseSmsParserTest() {

    @Test
    fun `test parses SBI Credit Card message`() = runBlocking {
        setupTest()
        val smsBody = "Rs.267.00 spent on your SBI Credit Card ending with 3201 at HALLI THOTA on 29-06-25 via UPI (Ref No. 1231230123). Trxn. Not done by you? Report at [https://sbicards.com/Dispute](https://sbicards.com/Dispute))"
        val mockSms = SmsMessage(
            id = 4L,
            sender = "VM-SBICRD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull(result)
        assertEquals(267.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("HALLI THOTA", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("SBI - xx3201", result?.potentialAccount?.formattedName)
        assertEquals("Credit Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI UPI expense with 'frm' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Rs200.0 user27@SBI UPI frm A/cX0763 on 04Jun21 RefNo 115563968049. If not done by u, fwd this SMS to0680397940/Call9169070230 or 09449112211 to block UPI"
        val mockSms = SmsMessage(
            id = 901L,
            sender = "BX-SBIUPI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

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
        val mockSms = SmsMessage(
            id = 902L,
            sender = "BP-CBSSBI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

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
        val mockSms = SmsMessage(
            id = 903L,
            sender = "BP-SBIUPI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull(result)
        assertEquals(300.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("PERUMAL C", result?.merchantName)
        assertEquals("A/c X0763", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses SBI debit with 'has a debit by transfer' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Your A/C XXXXX436715 has a debit by transfer of Rs 147.50 on 19/06/23. Avl Bal Rs 3,58,354.26.-SBI"
        val mockSms = SmsMessage(
            id = 906L,
            sender = "BW-CBSSBI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull(result)
        assertEquals(147.50, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("A/C XXXXX436715", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test parses SBI credit with 'has a credit by Transfer' format`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Your A/C XXXXX436715 has a credit by Transfer of Rs 3,710.00 on 31/05/22 by Bank. Avl Bal Rs 4,98,955.01.-SBI"
        val mockSms = SmsMessage(
            id = 1653998981316L,
            sender = "JK-CBSSBI",
            body = smsBody,
            date = 1653998981316L
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(3710.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("Bank", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("SBI - A/C XXXXX436715", result?.potentialAccount?.formattedName)
    }

    @Test
    fun `test correctly parses SBI UPI debit, account, and merchant`() = runBlocking {
        setupTest()
        val smsBody = "Dear UPI user A/C X0763 debited by 185.0 on date 09Dec24 trf to KAGGIS CAKES AND Refno 434416868357. If not u? call9169070230. -SBI"
        val mockSms = SmsMessage(
            id = 2L,
            sender = "DM-SBI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals("Amount should be 185.0, not the account number fragment", 185.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("KAGGIS CAKES AND", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("A/C X0763", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI Debit Card AMC and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Your A/C ending with 9515 has been debited for INR 147.5 on 21-06-21 towards annual maintenance charges for your SBI Debit Card ending with 9417"
        val mockSms = SmsMessage(
            id = 2L,
            sender = "VM-SBICRD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

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
        val mockSms = SmsMessage(
            id = 3L,
            sender = "VM-SBINEF",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(27750.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("Mr. Praveen Kumar K", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("A/C XXXXX293201", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI credit with Acc No and account`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Payment of Rs. 3,710.00 credited to your Acc No. XXXXX769515 on 27/03/25-SBI"
        val mockSms = SmsMessage(
            id = 2L,
            sender = "DM-SBICRD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

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
        val mockSms = SmsMessage(
            id = 3L,
            sender = "DM-SBITXN",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(20000.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("SBI ATM S1BD011351004", result?.merchantName)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SBI - A/cX0763", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI debit with 'Your A_c' keyword`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer, Your A/c XX9258 has been debited with INR 15,000.00 on 19/02/2025 towards NEFT with UTR SBIN125050589474 sent to My hdfc HDFC0005075-SBI"
        val mockSms = SmsMessage(
            id = 707L,
            sender = "VM-SBINEF",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull(result)
        assertEquals(15000.0, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertEquals("My hdfc HDFC0005075", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("A/c XX9258", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI income with 'Your A_C' format`() = runBlocking {
        setupTest()
        val smsBody = "Your A/C XXXXX650077 Credited INR 1,41,453.00 on 15/07/25 -Deposit by transfer from ESIC MODEL HOSP. Avl Bal INR 3,89,969.28-SBI"
        val mockSms = SmsMessage(
            id = 805L,
            sender = "VM-SBINEF",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull(result)
        assertEquals("income", result?.transactionType)
        assertEquals(141453.00, result?.amount)
        assertEquals("ESIC MODEL HOSP", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("A/C XXXXX650077", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI debit with 'Towards cash payment'`() = runBlocking {
        setupTest()
        val smsBody = "Your AC XXXXX769515 Debited INR 1,20,000.00 on 22/01/25 -Towards cash payment by Cheque No.518565. Avl Bal INR 40,675.45-SBI"
        val mockSms = SmsMessage(
            id = 4L,
            sender = "VM-SBIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull("Parser should return a result", result)
        assertEquals(120000.00, result?.amount)
        assertEquals("expense", result?.transactionType)
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SBI - AC XXXXX769515", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI Credit Card without the word 'with'`() = runBlocking {
        setupTest()
        val smsBody = "Rs.2.00 spent on your SBI Credit Card ending 8260 at SWIGGY LIMITED on 04/06/25. Trxn. not done by you? Report at https://sbicard.com/Dispute"
        val mockSms = SmsMessage(
            id = 804L,
            sender = "DM-SBICRD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull(result)
        assertEquals("expense", result?.transactionType)
        assertNotNull(result?.potentialAccount)
        assertEquals("SBI Credit Card 8260", result?.potentialAccount?.formattedName)
        assertEquals("Credit Card", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI UPI credit with no merchant`() = runBlocking {
        setupTest()
        val smsBody = "Dear SBI UPI User, ur A/cX9709 credited by Rs519 on 03Dec23 by  (Ref no 333785625735)"
        val mockSms = SmsMessage(id = 9L, sender = "DM-SBIUPI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(519.00, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals(null, result?.merchantName) // No merchant name present
        assertNotNull("Account info should be parsed", result?.potentialAccount)
        assertEquals("SBI - A/cX9709", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }

    @Test
    fun `test parses SBI credit with account at start and bank at end`() = runBlocking {
        setupTest()
        val smsBody = "Dear SBI User, your A/c X6710-credited by Rs.6226 on 27Apr25 transfer from VARSHA R Ref No 797385760274 -SBI"
        val mockSms = SmsMessage(
            id = 9012L,
            sender = "DM-SBI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider)

        assertNotNull("Parser should not ignore this valid transaction", result)
        assertEquals(6226.0, result?.amount)
        assertEquals("income", result?.transactionType)
        assertEquals("VARSHA R", result?.merchantName)
        assertNotNull(result?.potentialAccount)
        assertEquals("SBI - A/c X6710", result?.potentialAccount?.formattedName)
        assertEquals("Bank Account", result?.potentialAccount?.accountType)
    }
}
