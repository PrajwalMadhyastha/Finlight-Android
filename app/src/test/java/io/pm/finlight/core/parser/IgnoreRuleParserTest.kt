// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/IgnoreRuleParserTest.kt
// REASON: FIX - Removed the `setupTemplateMock` parameter from all `setupTest`
// calls. The base test class now handles the necessary mocking unconditionally,
// which resolves the NullPointerException.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.DEFAULT_IGNORE_PHRASES
import io.pm.finlight.IgnoreRule
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.quality.Strictness
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(MockitoJUnitRunner.Silent::class)

class IgnoreRuleParserTest : BaseSmsParserTest() {

    @Test
    fun `test returns null for non-financial message`() = runBlocking {
        setupTest()
        val smsBody = "Hello, just checking in. Are we still on for dinner tomorrow evening?"
        val mockSms = SmsMessage(
            id = 3L,
            sender = "+1234567890",
            body = smsBody,
            date = System.currentTimeMillis()
        )

        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNull("Parser should return null for a non-financial message", result)
    }

    @Test
    fun `test ignores successful payment confirmation`() = runBlocking {
        setupTest(
            ignoreRules = listOf(
                IgnoreRule(
                    pattern = "payment of.*is successful",
                    isEnabled = true
                )
            )
        )
        val smsBody = "Your payment of Rs.330.80 for A4-108 against Water Charges is successful. Regards NoBrokerHood"
        val mockSms = SmsMessage(
            id = 10L,
            sender = "VM-NBHOOD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore successful payment confirmations", result)
    }

    @Test
    fun `test ignores HDFC NEFT credit message`() = runBlocking {
        setupTest(
            ignoreRules = listOf(
                IgnoreRule(
                    pattern = "NEFT money transfer.*has been credited to",
                    isEnabled = true
                )
            )
        )
        val smsBody = "HDFC Bank : NEFT money transfer Txn No HDFCN520253454560344 for Rs INR 1,500.00 has been credited to Manga Penga on 01-07-2025 at 08:05:30"
        val mockSms = SmsMessage(
            id = 12L,
            sender = "VM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore has been credited to messages", result)
    }

    @Test
    fun `test ignores credit card payment confirmation`() = runBlocking {
        setupTest(
            ignoreRules = listOf(
                IgnoreRule(
                    pattern = "payment of.*has been received towards",
                    isEnabled = true
                )
            )
        )
        val smsBody = "Payment of INR 1180.01 has been received towards your SBI card XX1121"
        val mockSms = SmsMessage(
            id = 13L,
            sender = "DM-SBICRD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore credit card payment confirmations", result)
    }

    @Test
    fun `test ignores bharat bill pay confirmation`() = runBlocking {
        setupTest(
            ignoreRules = listOf(
                IgnoreRule(
                    pattern = "Payment of.*has been received on your.*Credit Card",
                    isEnabled = true
                )
            )
        )
        val smsBody = "Payment of Rs 356.33 has been received on your ICICI Bank Credit Card XX2529 through Bharat Bill Payment System on 03-JUL-25."
        val mockSms = SmsMessage(
            id = 14L,
            sender = "DM-ICIBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore Bharat Bill Pay confirmations", result)
    }

    @Test
    fun `test ignores message with user-defined ignore phrase`() = runBlocking {
        val ignoreRules = listOf(IgnoreRule(id = 1, pattern = "invoice of", isEnabled = true))
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "An Invoice of Rs.330.8 for A4 Block-108 is raised."
        val mockSms = SmsMessage(
            id = 9L,
            sender = "VM-NBHOOD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore messages with user-defined phrases", result)
    }

    @Test
    fun `test ignores Paytm Wallet payment`() = runBlocking {
        setupTest(
            ignoreRules = listOf(
                IgnoreRule(
                    pattern = "from Paytm Balance",
                    isDefault = true,
                    isEnabled = true
                )
            )
        )
        val smsBody = "Paid Rs.1559 to Reliance Jio from Paytm Balance. Updated Balance: Paytm Wallet- Rs 0."
        val mockSms = SmsMessage(
            id = 104L,
            sender = "VM-Paytm",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNull("Parser should ignore payments from Paytm Balance", result)
    }

    @Test
    fun `test ignores declined transaction`() = runBlocking {
        setupTest(
            ignoreRules = listOf(
                IgnoreRule(
                    pattern = "is declined",
                    isDefault = true,
                    isEnabled = true
                )
            )
        )
        val smsBody = "Your transaction on HDFC Bank card for 245.00 at AMAZON is declined due to wrong input of ExpiryDate or CVV."
        val mockSms = SmsMessage(
            id = 105L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNull("Parser should ignore declined transactions", result)
    }

    @Test
    fun `test ignores AutoPay mandate setup`() = runBlocking {
        setupTest(
            ignoreRules = listOf(
                IgnoreRule(
                    pattern = "AutoPay (E-mandate) Active",
                    isDefault = true,
                    isEnabled = true
                )
            )
        )
        val smsBody = "AutoPay (E-mandate) Active! Merchant: YouTube... On HDFC Bank Credit Card xx1335"
        val mockSms = SmsMessage(
            id = 106L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNull("Parser should ignore AutoPay setup confirmations", result)
    }

    @Test
    fun `test ignores HDFC payee modification alert`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "You've added/modified a payee Prajwal Madhyastha K P with A/c XX8207 via HDFC Bank Online Banking. Not you?Call 18002586161"
        val mockSms = SmsMessage(
            id = 301L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore payee modification alerts", result)
    }

    @Test
    fun `test ignores Airtel recharge credited confirmation`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Hi, recharge of Rs. 859 successfully credited to your Airtel number7793484112, also the validity has been extended till 03-05-2025."
        val mockSms = SmsMessage(
            id = 302L,
            sender = "VM-AIRTEL",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore recharge credited confirmations", result)
    }

    @Test
    fun `test ignores insurance policy conversion alert`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your Axis Max Life Insurance Limited policy 174171785 successfully converted & added to eIA 8100144160103. Check Bima Central app.bimacentral.in for more details. -Bima Central -CAMS Insurance Repository"
        val mockSms = SmsMessage(
            id = 303L,
            sender = "CP-BIMACN",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore insurance policy conversion alerts", result)
    }

    @Test
    fun `test ignores failed payment notification`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Hi, Payment of Rs. 33.0 has failed for your Airtel Mobile7793484112. Any amount, if debited will be refunded to your source account within a day. Please keep order ID 7328718104265809920 for reference."
        val mockSms = SmsMessage(
            id = 304L,
            sender = "VM-AIRTEL",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore failed payment notifications", result)
    }

    @Test
    fun `test ignores bonus points notification`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "WE'VE ADDED 501 BONUS POINTS! The Levi's Blue Jean celebration is here. Redeem your 601.0 points at your nearest Levi's store or on Levi.in before 27/5 *T&C"
        val mockSms = SmsMessage(
            id = 305L,
            sender = "TX-LEVIS",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore bonus points notifications", result)
    }

    @Test
    fun `test ignores Delhivery authentication code message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your SBI Card sent via Delhivery Reference No EV25170653359 will be attempted today. Pls provide this Delivery Authentication Code 3832 - Delhivery"
        val mockSms = SmsMessage(
            id = 306L,
            sender = "VM-DliVry",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore delivery authentication code messages", result)
    }

    @Test
    fun `test ignores Amazon gift card voucher code message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Dear SimplyCLICK Cardholder, e-code of your Amazon.in Gift Card is given below. Visit amazon.in/addgiftcard to add the e-code to your Amazon Pay Balance before claim date given below.Voucher Code for Rs.500 is G9OUF-C1I65-B33R2 to be added by 27/11/2025. Valid for 12 months from the date of adding the e-code.TnC Apply."
        val mockSms = SmsMessage(
            id = 307L,
            sender = "DM-SBICRD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore gift card voucher code messages", result)
    }

    @Test
    fun `test ignores Gruha Jyoti scheme application message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your Application No. GJ005S250467224 for Gruha Jyoti Scheme received & sent to your ESCOM for Processing. For quarries please dial 1912 From-Seva Sindhu"
        val mockSms = SmsMessage(
            id = 308L,
            sender = "IM-SEVSIN",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore scheme application messages", result)
    }

    @Test
    fun `test ignores Bhima promotional message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Step into the magic of Bhima Brilliance Diamond Jewellery Festival, from 7th to 27th July 2025. Treat yourself with Flat Rs.7,000 off per carat of dazzling diamonds. And that's not all. Get free gold or silver jewellery worth upto Rs.2,00,000 on purchase of diamond jewellery. For more details, call us on 18001219076 or click [https://vm.ltd/BHIMAJ/Y-UKm7](https://vm.ltd/BHIMAJ/Y-UKm7) to know more. T&C apply."
        val mockSms = SmsMessage(
            id = 309L,
            sender = "VM-BHIMAJ",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore promotional messages with monetary offers", result)
    }


    @Test
    fun `test ignores Navi Mutual Fund unit allotment message`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Unit Allotment Update: Your request for purchase of Rs.6,496.18 in Navi Liquid Fund GDG has been processed at applicable NAV. The units will be alloted in 1-2 working days.For further queries, please visit the Navi app.Navi Mutual Fund"
        val mockSms = SmsMessage(
            id = 201L,
            sender = "VM-NAVI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore mutual fund allotment messages", result)
    }

    @Test
    fun `test ignores SBI Credit Card e-statement notification`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "E-statement of SBI Credit Card ending XX76 dated 09/08/2025 has been mailed. If not received, SMS ENRS to 5676791. Total Amt Due Rs 8153; Min Amt Due Rs 200; Payable by 29/08/2025. Click [https://sbicard.com/quickpaynet](https://sbicard.com/quickpaynet) to pay your bill"
        val mockSms = SmsMessage(
            id = 202L,
            sender = "DM-SBICRD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore e-statement notifications", result)
    }

    @Test
    fun `test ignores Orient Exchange order received confirmation`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "Your order is received at our Bangalore - Basavangudi branch. Feel free to contact at 080-26677118/080-26677113 for any clarification. Orient Exchange"
        val mockSms = SmsMessage(
            id = 203L,
            sender = "VM-ORIENT",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore non-financial 'order received' messages", result)
    }

    @Test
    fun `test ignores MakeMyTrip booking confirmation`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Dear Kavyashree,\n\nThank you for booking with us. We are happy to inform you that your flight with booking ID NF2A9N4J42556272467 is confirmed.\n\nYour Trip.kt Details:\n\nDeparture Flight: Jodhpur-Bangalore | Date: 26 Sep 24 | Add-ons: JDH-BLR, 6E-6033(Seats: 26B)\n\nTotal amount paid: INR 9,266.00\n\nYou can manage your booking at [https://app.mmyt.co/Xm2V/1iur6n6j](https://app.mmyt.co/Xm2V/1iur6n6j)\n\nWe wish you a safe journey.\nTeam MakeMyTrip"
        val mockSms = SmsMessage(
            id = 601L,
            sender = "VM-MMTRIP",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore flight booking confirmations", result)
    }

    @Test
    fun `test ignores promotional wallet credit message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Hi 9480XXXX67, Rs.15,OOO/- is Credited on your wallet account. Join Now to withdrawal directly: SR3.in/O13A0-2351A564B"
        val mockSms = SmsMessage(
            id = 602L,
            sender = "CP-WALLET",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore promotional wallet credit messages", result)
    }

    @Test
    fun `test ignores HDFC Welcome Kit delivery confirmation`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Delivered! Your HDFC Bank Welcome Kit Awb -33906843213 has been delivered. It was received by KAVYASHREEBHAT on 14/10/2024."
        val mockSms = SmsMessage(
            id = 603L,
            sender = "VM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore welcome kit delivery confirmations", result)
    }

    @Test
    fun `test ignores suspected spam Rummy message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Suspected Spam : Congrats 94808xxxxx, Am0unt 0f Rs.15OOO can be Successfully Credited T0 Y0ur Rummy A/C. Withdraw N0w: [7kz1.com/bh2e5bdp4l](https://7kz1.com/bh2e5bdp4l)"
        val mockSms = SmsMessage(
            id = 604L,
            sender = "CP-RUMMY",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore messages marked as suspected spam", result)
    }

    @Test
    fun `test ignores informational NEFT sent message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "NEFT from Ac X3377 for Rs.10,000.00 with UTR SBIN525080708724 dt 21.03.25 to My hdfc Ac X6982 with HDFC0005075 sent from YONO. If not done by you fwd this SMS to3711961089 or call9113274461 - SBI."
        val mockSms = SmsMessage(
            id = 605L,
            sender = "VM-SBINEF",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore informational NEFT sent messages", result)
    }

    @Test
    fun `test ignores informational NEFT credited to beneficiary message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Dear Customer, Your NEFT of Rs 20,000.00 with UTR SBIN425127845277 DTD 07/05/2025 credited to Beneficiary AC NO. XX6982 at HDFC0005075 on 07/05/2025 at 01:38 PM.-SBI"
        val mockSms = SmsMessage(
            id = 606L,
            sender = "VM-SBINEF",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore informational NEFT credited messages", result)
    }

    @Test
    fun `test ignores Zepto refund processed message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Dear user, \nRefund of Rs 277.51 has been processed for your Zepto order KVKQRLNK98664. Refund Ref. num. 514321799294. You can check details on Refunds page in the app."
        val mockSms = SmsMessage(
            id = 607L,
            sender = "VM-ZEPTO",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore refund processed notifications", result)
    }

    @Test
    fun `test ignores promotional birthday offer`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Yay!! Its your BIRTHDAY week\nget FLAT 25% OFF on purchase of Rs. 2000 on your shopping @UNLIMITED Fashion store. Hurry!\nCode: PAZKY8A272\nValid: till 05-Jun-25.TnC"
        val mockSms = SmsMessage(
            id = 608L,
            sender = "VM-UNLTD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore promotional birthday offers", result)
    }

    @Test
    fun `test ignores SBI Card delivery confirmation`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Your SBI Card has been delivered through Delhivery and received by MS KAVYASHREE BHAT Reference No EV25151188648 - Delhivery"
        val mockSms = SmsMessage(
            id = 609L,
            sender = "VM-DliVry",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore card delivery confirmations", result)
    }

    @Test
    fun `test ignores promotional offer reminder`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Alert: Your FLAT 25% OFF on purchase of Rs. 2000 is valid for 5 DAYS ONLY\nEnjoy your BIRTHDAY shopping @UNLIMITED Fashion store.\nCode: PAZKY8A272\nTnC"
        val mockSms = SmsMessage(
            id = 610L,
            sender = "VM-UNLTD",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore promotional offer reminders", result)
    }

    @Test
    fun `test ignores payee modification without the word 'a'`() = runBlocking {
        val ignoreRules = DEFAULT_IGNORE_PHRASES.filter { it.isEnabled }
        setupTest(ignoreRules = ignoreRules)
        val smsBody = "You've added/modified payee Nithyananda Bhat with A/c XX8305 via HDFC Bank Online Banking. Not you?Call 18002586161"
        val mockSms = SmsMessage(
            id = 611L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore payee modification without 'a'", result)
    }

    @Test
    fun `test ignores RTGS informational message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "RTGS Money Deposited~INR 3,00,000.00~To Nithyananda Bhat~Txn No: HDFCR52025041560739217~On 15-04-2025 at 07:52:07~-HDFC Bank"
        val mockSms = SmsMessage(
            id = 806L,
            sender = "AM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull(result)
    }

    @Test
    fun `test ignores Trends Footwear points message`() = runBlocking {
        setupTest(ignoreRules = DEFAULT_IGNORE_PHRASES)
        val smsBody = "Rs500 worth points credited in ur AC Use code TRFWSWEJXF3107 by 20Jul to redeem on Rs1250 user152@TRENDS FOOTWEAR Visit@ [https://vil.ltd/TRNDFW/c/stlcjul](https://vil.ltd/TRNDFW/c/stlcjul) T&C"
        val mockSms = SmsMessage(
            id = 807L,
            sender = "VM-TRENDS",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull(result)
    }

    @Test
    fun `test ignores SBIL renewal premium notice`() = runBlocking {
        setupTest()
        val smsBody = "Thank you for the  payment made on 05-NOV-2023 for Rs 100000 against renewal premium of SBIL policy No 1H461419008 Amt. will be credited subject to realization."
        val mockSms = SmsMessage(
            id = 2001L,
            sender = "XX-SBILIF",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore SBIL premium notices", result)
    }

    @Test
    fun `test ignores Bank of Baroda email added confirmation`() = runBlocking {
        setupTest()
        val smsBody = "We confirm you that Email Id user285@GMAIL.COM has been added in CUST_ID XXXXX3858 w.e.f 18-01-2024 18:17:29 at your request.-Bank of Baroda"
        val mockSms = SmsMessage(
            id = 2002L,
            sender = "XX-BOB",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore email added confirmations", result)
    }

    @Test
    fun `test ignores Jio eSIM activation instructions`() = runBlocking {
        setupTest()
        val smsBody = "To activate your eSIM for Jio Number9656315416, Please follow the below-mentioned steps..." // Truncated for brevity
        val mockSms = SmsMessage(
            id = 2003L,
            sender = "XX-JIO",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore eSIM activation instructions", result)
    }

    @Test
    fun `test ignores Godrej real estate advertisement`() = runBlocking {
        setupTest()
        val smsBody = "Received-Rera-Approval & Allotments-Underway Your-2,3&4BHK frm-1.28Cr Add-Godrej Woodscapes, Whitefield-Bangalore Appointment- [https://wa.link/godrej-woodscapes](https://wa.link/godrej-woodscapes)"
        val mockSms = SmsMessage(
            id = 2004L,
            sender = "XX-GODREJ",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore real estate ads", result)
    }

    @Test
    fun `test ignores Lok Adalat Notice`() = runBlocking {
        setupTest()
        val smsBody = "This is a Lok Adalat Notice issued by District Legal Services Authority, Bengaluru Urban [http://122.185.94.100:3000/lokadalt/notification?regno=KA04P4631&noticeno=508412](http://122.185.94.100:3000/lokadalt/notification?regno=KA04P4631&noticeno=508412).  You are called upon to pay the fine amount of Rs. 500  for the traffic violation against vehicle Reg. No: KA04P4631.  The fine shall be deposited via online or offline mode as mentioned in the notice to avoid legal action From DLSA and Bangalore Traffic Police"
        val mockSms = SmsMessage(
            id = 1001L,
            sender = "VM-LOKADL",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore Lok Adalat notices", result)
    }

    @Test
    fun `test ignores Fixed Deposit closure message`() = runBlocking {
        setupTest()
        val smsBody = "Your FD  a/c XXXXX7047 is closed on 05.12.2022 and proceeds are credited to a/c XXXXX5945 .Contact branch immediately, if not requested by you. -IndianBank"
        val mockSms = SmsMessage(
            id = 1002L,
            sender = "BT-INDBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore FD closure messages", result)
    }

    @Test
    fun `test ignores Porter wallet credit message`() = runBlocking {
        setupTest()
        val smsBody = "Hi, PORTER has credited Rs.200 (Code: FIRST50) in your wallet for 1st truck order! Send parcel using bike at Rs.30 @ weurl.co/bqHMj9"
        val mockSms = SmsMessage(
            id = 1003L,
            sender = "TX-PORTER",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore Porter promotional wallet credits", result)
    }

    @Test
    fun `test ignores bob World maintenance message`() = runBlocking {
        setupTest()
        val smsBody = "Dear Customer: bob World will be unavailable frm 11:30PM on 07-01-23 to 06:30AM on 08-01-23 for a System Upgrade. Pl use Net Banking or UPI for urgent txn-BOB"
        val mockSms = SmsMessage(
            id = 1004L,
            sender = "VD-BOBSMS",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore system maintenance messages", result)
    }

    @Test
    fun `test ignores Apollo Pharmacy promotional message`() = runBlocking {
        setupTest()
        val smsBody = "Get Free Stainless Steel Water Bottle on purchase of Medicine plus Apollo brands worth Rs.1000 & above. Visit your nearest Apollo Pharmacy klr.pw/V4HJTc *T&C"
        val mockSms = SmsMessage(
            id = 1005L,
            sender = "VM-APOLLO",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore Apollo promotional messages", result)
    }

    @Test
    fun `test ignores JioHealthHub informational message`() = runBlocking {
        setupTest()
        val smsBody = "More than 36 million medical records have been added to Health Locker by our users. Go digital. Try Health Locker on JioHealthHub."
        val mockSms = SmsMessage(
            id = 1006L,
            sender = "VM-JIOHH",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore JioHealthHub informational messages", result)
    }

    @Test
    fun `test ignores India Post delivery message`() = runBlocking {
        setupTest()
        val smsBody = "Article No:JB955870859IN received @ Bengaluru NSH on 23/03/2023 20:08:32.Track @ www.indiapost.gov.in"
        val mockSms = SmsMessage(
            id = 1007L,
            sender = "AD-IndPst",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore India Post delivery messages", result)
    }

    @Test
    fun `test ignores Canara Bank cheque book delivery message`() = runBlocking {
        setupTest()
        val smsBody = "Personalised cheques sent to you vide INDIA POST Ref No JB955207542IN and you will receive the same shortly. If not received within 7 days please contact Your Canara Bank Branch. -Canara Bank"
        val mockSms = SmsMessage(
            id = 1008L,
            sender = "AD-CANBNK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore cheque book delivery messages", result)
    }

    @Test
    fun `test ignores Practo family member message`() = runBlocking {
        setupTest()
        val smsBody = "Prajwal Madhyastha K P has added you as a family member on their Marsh - KPMG Health plan 2022. Download the practo app to avail its benefits. prac.to/get-plus\n- Practo"
        val mockSms = SmsMessage(
            id = 1009L,
            sender = "VM-PRACTO",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore Practo informational messages", result)
    }

    @Test
    fun `test ignores JioTV promotional message`() = runBlocking {
        setupTest()
        val smsBody = "Get super-charged with great laughs!\nWatch Siddharth Malhotra & Ajay Devgn starrer Thank God on JioTV\n[https://bit.ly/SMSJioTV4](https://bit.ly/SMSJioTV4)"
        val mockSms = SmsMessage(
            id = 1010L,
            sender = "QP-JIOCIN",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore JioTV promotional messages", result)
    }

    @Test
    fun `test ignores My11Circle promotional message`() = runBlocking {
        setupTest()
        val smsBody = "Dear ,\nRs.5000 Bonus to be credited on My11Circle.\nBLR vs UPE\nPrize Pool - 12,07,00,000 (12.07Cr)\nEntry Fees - Rs.49\nClick - [http://1kx.in/TNpHVV](http://1kx.in/TNpHVV)"
        val mockSms = SmsMessage(
            id = 1011L,
            sender = "VM-MY11CE",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore My11Circle promotional messages", result)
    }

    @Test
    fun `test ignores 'Received with thanks' receipt message`() = runBlocking {
        setupTest() // Uses the updated default ignore rules
        val smsBody = "Received with thanks Rs 600 /- by receipt number RA2021-22/00000576.If found incorrect write to us user77@gmail.com or Whats App to6919864999"
        val mockSms = SmsMessage(
            id = 11L,
            sender = "IX-Update",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore the receipt confirmation", result)
    }

    @Test
    fun `test ignores Jio activation message`() = runBlocking {
        setupTest()
        val smsBody = "Your number0151027867 will be activated on Jio network by 6:00 AM. Once your existing SIM stops working, please insert the Jio SIM in SIM slot 1 of your smartphone and do Tele-verification by dialling 1977. To know your device settings, please refer to earlier settings SMS sent by us or click [https://youtu.be/o18LboDi1ho](https://youtu.be/o18LboDi1ho)\nIf you are an eSIM user, use the QR code to download and install the eSIM profile on your device before Tele-verification. You can also set eSIM profile manually by referring to the SMS sent to your number when eSIM request was given.\nPrepaid users, need to do a recharge after activation to start using Jio services. To recharge, click [www.jio.com/rechargenow](https://www.jio.com/rechargenow) or Visit the nearest Jio retailer after activation of your number. For details of exciting recharge plans, call 1991 from your Jio SIM after activation or 18008900000 from any number. Please note, Tele-verification is not required for Jio Corporate connections."
        val mockSms = SmsMessage(
            id = 1L,
            sender = "JK-JioSvc",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore Jio activation messages", result)
    }

    @Test
    fun `test ignores Bank of Baroda informational message`() = runBlocking {
        setupTest()
        val smsBody = "Funds credited in your account by NEFT/RTGS with VIJB0001040 received from L+T CONSTRUCTION EQUIPMENT LIMITED. Please advise your remitter to use new IFSC only-BARB0VJDASC(Fifth digit is ZER0) Team Bank of Baroda"
        val mockSms = SmsMessage(
            id = 2L,
            sender = "VD-BOBSMS",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore informational IFSC messages", result)
    }

    @Test
    fun `test ignores SBI Life premium payment message`() = runBlocking {
        setupTest()
        val smsBody = "Thank you for premium payment of Rs.100000. on 06-11-2021 for Policy 1H461419008 through State Bank. Receipt will be sent shortly-SBI Life."
        val mockSms = SmsMessage(
            id = 3L,
            sender = "JM-SBLIFE",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore SBI Life premium messages", result)
    }

    @Test
    fun `test ignores DICGCI insurance claim message`() = runBlocking {
        setupTest()
        val smsBody = "Insurance claim u/s 18A of DICGC Act received from S G Raghvendra CBL. Submit willingness, alternate bank AC or Adhaar details to bank for payment"
        val mockSms = SmsMessage(
            id = 4L,
            sender = "JD-DICGCI",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore DICGCI claim messages", result)
    }

    @Test
    fun `test ignores MyGovt RT-PCR sample message`() = runBlocking {
        setupTest()
        val smsBody = "RT-PCR sample collected for MANJULA PRAVEEN (Id:2952525386749) SRF ID 2952525695921  on Jan 18 2022 12:06PM. Please save for future reference. You are advised to isolate yourself till the sample is tested and test report is provided by the Lab. [https://covid19cc.nic.in/PDFService/Specimenfefform.aspx?formid=Ukx%2bYrhm8Hq2EzLtOF%2fpWg%3d%3d](https://covid19cc.nic.in/PDFService/Specimenfefform.aspx?formid=Ukx%2bYrhm8Hq2EzLtOF%2fpWg%3d%3d) Your Sample has been sent to Sagar Hospital, Jayanagar, Bangalore"
        val mockSms = SmsMessage(
            id = 5L,
            sender = "VM-MYGOVT",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore RT-PCR sample messages", result)
    }

    @Test
    fun `test ignores IndiaFirst premium debit notification`() = runBlocking {
        setupTest()
        val smsBody = "Renewal premium of Rs.330 for Pradhan Mantri Jeevan Jyoti Bima Yojana will be debited from your account. Pls maintain sufficient balance in a/c 74370100011143 for auto-debit.IFL"
        val mockSms = SmsMessage(
            id = 6L,
            sender = "BP-IndiaF",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore future debit notifications", result)
    }

    @Test
    fun `test ignores SBI OTP message`() = runBlocking {
        setupTest()
        val smsBody = "OTP for online purchase of Rs. 8140.50 at AMAZON thru State Bank Debit Card 6074*****17 is 512192. Do not share this with anyone."
        val mockSms = SmsMessage(
            id = 7L,
            sender = "BZ-SBIOTP",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore OTP messages", result)
    }

    // --- NEW: Tests for the latest batch of incorrectly parsed messages ---

    @Test
    fun `test ignores Samsung order confirmation`() = runBlocking {
        setupTest()
        val smsBody = "Order Confirmed: Thank you for placing your order with Samsung Shop. The details of Order ID 12107357889 have been sent to your registered email id. View your order details here [http://1kx.in/XRscQ24ApYZ](http://1kx.in/XRscQ24ApYZ) . - Samsung Shop"
        val mockSms = SmsMessage(id = 1L, sender = "VM-SAMSNG", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore order confirmations with Order IDs", result)
    }

    @Test
    fun `test ignores NSDL insurance policy credit`() = runBlocking {
        setupTest()
        val smsBody = "Insurance Policy 113992994 is credited to your e Insurance Account 1000070456279 with NSDL NIR. Login at [https://nir.ndml.in](https://nir.ndml.in) to check the policy."
        val mockSms = SmsMessage(id = 2L, sender = "VM-NSDL", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore non-monetary 'e Insurance Account' credits", result)
    }

    @Test
    fun `test ignores VFS visa application receipt`() = runBlocking {
        setupTest()
        val smsBody = "The processed visa application for GWF ref no. GWF069802268 was received at the UK Visa Application Centre on Jul 03, 2023. If a courier service was purchased from VFS, your processed visa application will be delivered to the chosen address. If not, your documents can be collected during the designated passport collection times. - VFS Global"
        val mockSms = SmsMessage(id = 3L, sender = "VM-VFSGLO", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore VFS visa application messages", result)
    }

    @Test
    fun `test ignores Lenskart refund with missing amount`() = runBlocking {
        setupTest()
        val smsBody = "Action needed for your Vincent Chase Polarized Sunglasses of order #4415082357. Exchange product or get refund of Rs at lnskt.co/E0ZgPPkY . Ignore if done"
        val mockSms = SmsMessage(id = 4L, sender = "VM-LNSKRT", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore 'refund of Rs at' when no amount is present", result)
    }

    @Test
    fun `test ignores Cashify get paid message`() = runBlocking {
        setupTest()
        val smsBody = "Hi! Tell us how you'd like to get paid for order MPMAF14822604. Choose from UPI, vouchers & more. For a quick transfer, update details NOW cashi.fi/7b461502 - Cashify"
        val mockSms = SmsMessage(id = 5L, sender = "VM-CASHFY", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore 'how you'd like to get paid' messages", result)
    }

    @Test
    fun `test ignores CDSL shares credit message`() = runBlocking {
        setupTest()
        val smsBody = "CDSL:CREDITED IN A/C *14644861 SHARES OF ITC HOTELS LIMITED TOWARDS SCHEME OF ARRANGEMENT ON 13/01/2025.CONTACT YOUR DP FOR MORE INFORMATION."
        val mockSms = SmsMessage(id = 6L, sender = "VM-CDSL", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore non-monetary 'SHARES OF' credits", result)
    }

    // --- NEW: Tests for the second batch of non-financial messages ---

    @Test
    fun `test ignores Protean NSDL PRAN contribution message`() = runBlocking {
        setupTest()
        val smsBody = "NSDL e-Gov is now Protean. Click [https://bit.ly/3BFr8M2](https://bit.ly/3BFr8M2) Units against Voluntary Contribution Rs.5,000.00 credited to PRAN XX1084 at NAV-02/09/22 -Protean"
        val mockSms = SmsMessage(id = 7L, sender = "VM-Protean", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore PRAN contribution messages", result)
    }

    @Test
    fun `test ignores NSE cash equity registration message`() = runBlocking {
        setupTest()
        val smsBody = "CTMXXXXX4F registered in Cash Equity segment with broker PAYTM MONEY LTD. with Unique Client Code (UCC)BZ714284 . As per SEBI norms, NSE will start sending SMS/Email alert to you about transactions in your account. If SMS is wrongly sent to you reply by typing Y to 561614 or write to user29@nse.co.in -National Stock Exchange."
        val mockSms = SmsMessage(id = 8L, sender = "VM-NSE", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore NSE registration messages", result)
    }

    @Test
    fun `test ignores HDFC credit card payment received message`() = runBlocking {
        setupTest()
        val smsBody = "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 1.00 RECEIVED TOWARDS YOUR CREDIT CARD ENDING 1335 THROUGH NEFT or RTGS ON 22-10-2022.YOUR AVAILABLE LIMIT IS RS. 410306.98"
        val mockSms = SmsMessage(id = 9L, sender = "AM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore credit card payment received confirmations", result)
    }

    @Test
    fun `test ignores RBI Ombudsman informational message`() = runBlocking {
        setupTest()
        val smsBody = "Complaint not responded to by your bank/NBFC/e-wallet for 30 days/not satisfied with the reply received? Approach RBI Ombudsman at [https://cms.rbi.org.in](https://cms.rbi.org.in). -RBI"
        val mockSms = SmsMessage(id = 10L, sender = "VM-RBI", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore RBI informational messages", result)
    }

    @Test
    fun `test ignores Units against Voluntary Contribution message`() = runBlocking {
        setupTest()
        val smsBody = "Units against Voluntary Contribution Rs.5,000.00 credited to PRAN XX1084 at NAV-02/11/22.Contribute (D-remit) using UPI.Info-[https://bit.ly/3TiEKFJ](https://bit.ly/3TiEKFJ) -Protean"
        val mockSms = SmsMessage(id = 11L, sender = "TM-PTNNPS", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore Units against Voluntary Contribution messages", result)
    }

    @Test
    fun `test ignores delivered by DELHIVERY message`() = runBlocking {
        setupTest()
        val smsBody = "Delivered: Card- ATM / Debit Renewal for ICICI Bank Account XX9709 is delivered by DELHIVERY on 24-NOV-22 & received by PRAJWAL MADHYASTHA K P."
        val mockSms = SmsMessage(id = 12L, sender = "AD-ICICIB", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore delivered by DELHIVERY messages", result)
    }

    @Test
    fun `test ignores has been deactivated message`() = runBlocking {
        setupTest()
        val smsBody = "Tata Play ID2299081965 has been deactivated.\nMonthly charges Rs.260\nClick bit.ly/TPRech to recharge now\n\nIf unable to recharge, give a missed call on 08061999999 from your registered mobile number and get 3 days balance. This Balance will be debited on 4th day.\n\nPlease ignore, if already recharged."
        val mockSms = SmsMessage(id = 13L, sender = "AX-TPPLAY", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore has been deactivated messages", result)
    }

    @Test
    fun `test ignores Simpl bill payment message`() = runBlocking {
        setupTest()
        val smsBody = "Thanks! Your Simpl bill payment was a success :)\n--\nRs.305.35 has been received.\n--\nSimpl Pay"
        val mockSms = SmsMessage(id = 14L, sender = "BP-SmplPL", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore Simpl bill payment messages", result)
    }

    @Test
    fun `test ignores cashless claim message`() = runBlocking {
        setupTest()
        val smsBody = "Bharath Nursing Home has received INR 24728 towards your cashless claim no. 32978128 under policy no. 12100034230400000009 from The New India Assurance Co. Ltd with UTR no. AXISCN0275262045 and your balance sum insured is INR 375272. Team Medi Assist"
        val mockSms = SmsMessage(id = 15L, sender = "TX-MASIST", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore cashless claim messages", result)
    }

    // --- NEW: Tests for informational receipts and requests ---
    @Test
    fun `test ignores MoRTH receipt notification`() = runBlocking {
        setupTest()
        val smsBody = "Payment Received. Your Receipt No:ES/6644609 and Bank Reference Number:2506092552735. MoRTH."
        val mockSms = SmsMessage(id = 107L, sender = "VM-MoRTH", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore MoRTH receipt notifications", result)
    }

    @Test
    fun `test ignores HDFC address change request`() = runBlocking {
        setupTest()
        val smsBody = "Request Received! Address Change request in HDFC Bank SR#CA699084A0AA Check our other Instant Online Services: [https://hdfcbk.io/HDFCBK/v/jynaQl](https://hdfcbk.io/HDFCBK/v/jynaQl)"
        val mockSms = SmsMessage(id = 108L, sender = "VM-HDFCBK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore address change request confirmations", result)
    }

    // --- NEW: Test for Axis Bank UPI mandate request ---
    @Test
    fun `test ignores Axis Bank UPI mandate request`() = runBlocking {
        setupTest()
        val smsBody = "You have received UPI mandate collect request from NETFLIX COM for INR 149.00. Log into Axis Pay app to authorize - Axis Bank"
        val mockSms = SmsMessage(
            id = 9004L,
            sender = "AM-AXISBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Parser should ignore UPI mandate requests", result)
    }

    @Test
    fun `test ignores HDFC future debit alert`() = runBlocking {
        setupTest()
        val smsBody = "Alert: INR.1950.00 will be debited on 27/06/2025 from HDFC Bank Card 4433 for Google Play - Content Purchase Services ID:Xr61pEDASo Act:https://hdfcbk.io/HDFCBK/a/1410ENb"
        val mockSms = SmsMessage(
            id = 9008L,
            sender = "VM-HDFCBK",
            body = smsBody,
            date = System.currentTimeMillis()
        )
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        assertNull("Parser should ignore future-dated debit alerts", result)
    }

    @Test
    fun `test ignores informational NEFT credit to beneficiary`() = runBlocking {
        setupTest()
        val smsBody = "ICICI BANK NEFT Transaction with reference number 480800675 for Rs. 5000.00 has been credited to the beneficiary account on 02-09-2022 at 08:38:47"
        val mockSms = SmsMessage(id = 7L, sender = "VK-ICIBNK", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore informational NEFT credit messages", result)
    }

    // --- NEW: Test for the passbook/contribution false positive ---
    @Test
    fun `test ignores passbook contribution received message`() = runBlocking {
        setupTest() // Uses the updated default ignore rules
        val smsBody = "Dear XXXXXXXX1387, your passbook balance against GRAAN**************0411 is Rs. 3,85,047/-. Contribution of Rs. 2,210/- for due month Aug-25 has been received."
        val mockSms = SmsMessage(id = 9999L, sender = "XX-EPFOHO", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore passbook contribution updates", result)
    }

    @Test
    fun `test ignores share bonus allotment message`() = runBlocking {
        setupTest() // Uses the updated default rules
        val smsBody = "CDSL:CREDITED IN A/C *14123123  SHARES OF HDFC BANK LIMITED TOWARDS BONUS ALLOTMENT ON 29/08/2025.CONTACT YOUR DP FOR MORE INFORMATION."
        val mockSms = SmsMessage(id = 9998L, sender = "XX-SOMEO", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore share bonus allotment", result)
    }

    @Test
    fun `test ignores CDSL message`() = runBlocking {
        setupTest()
        val smsBody = "CDSL: Debit in a/c *14123461 for 78-AXIS ETSF DP GROWTH on 03SEP"
        val mockSms = SmsMessage(id = 9998L, sender = "JD-CDSLTX-S", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore messages from CDSL", result)
    }

    @Test
    fun `test ignores HDFC E-Mandate message`() = runBlocking {
        setupTest()
        val smsBody = "E-Mandate!\nRs.59.00 will be deducted on 07/09/25, 00:00:00\nFor Google Play mandate\nUMN 28a800d66345345gdfgd435e8eee20a6085@okhdfcbank\nMaintain Balance\n-HDFC Bank"
        val mockSms = SmsMessage(id = 9798L, sender = "JD-HDFCB", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore messages from HDFC E-Mandate", result)
    }

    @Test
    fun `test ignores HDFC Reward Points credit message`() = runBlocking {
        setupTest()
        val smsBody = "Reward Points Credited: 368 To HDFC Bank Credit Card 1122 On 01/OCT/2025 Towards Regalia Gold_5XRP_Select_merchantsCheck Here: https://hdfcbk.io/HDFCBK/s/AXyY60LX"
        val mockSms = SmsMessage(id = 9698L, sender = "JD-HDFCB", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore messages from HDFC Reward Points credit", result)
    }

    @Test
    fun `test ignores HDFC future debit message`() = runBlocking {
        setupTest()
        val smsBody = "Alert:\n" +
                "INR.1950.00 will be debited on 27/10/2025 from HDFC Bank Card 1122 for Google Play - Conten..\n" +
                "ID:Xr61pEDASo\n" +
                "Act:https://hdfcbk.io/HDFCBK/a/1410ENb"
        val mockSms = SmsMessage(id = 9691L, sender = "JD-HDFCB", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore messages from HDFC for future debit", result)
    }

    @Test
    fun `test ignores ICICI Credit Card Statement`() = runBlocking {
        setupTest()
        val smsBody = "ICICI Bank Credit Card XX8008 Statement is sent to lk****************33@gmail.com. Total of Rs 691.60 or minimum of Rs 100.00 is due by 07-NOV-25."
        val mockSms = SmsMessage(id = 9611L, sender = "JD-HDFCB", body = smsBody, date = System.currentTimeMillis())
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)
        assertNull("Should ignore messages from ICICI Credit Card STatement", result)
    }
}

