package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class SmsParserHashTest : BaseSmsParserTest() {
    @Test
    fun `computeSmsHash produces deterministic 64-character lowercase hex SHA-256 string`() {
        val sender = "VK-HDFCBK"
        val body = "Rs 500.00 debited from A/C XX1234 to Swiggy on 15-09-2026."

        val hash1 = SmsParser.computeSmsHash(sender, body)
        val hash2 = SmsParser.computeSmsHash(sender, body)

        assertEquals("Hash must be deterministic across identical inputs", hash1, hash2)
        assertEquals("SHA-256 hex digest must be exactly 64 characters long", 64, hash1.length)
        assertTrue("Hash must contain only lowercase hexadecimal characters", hash1.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `computeSmsHash preserves alphanumeric senders without digit-only stripping`() {
        val body = "debited by Rs 250.00 for Coffee at Starbucks"

        // Both senders contain zero digits — under the legacy code both reduced to "" and collided
        val hdfcHash = SmsParser.computeSmsHash("VK-HDFCBK", body)
        val axisHash = SmsParser.computeSmsHash("AX-AXISBK", body)
        val sbiHash = SmsParser.computeSmsHash("BW-SBIUPI", body)

        assertNotEquals("HDFC and Axis messages must not collide", hdfcHash, axisHash)
        assertNotEquals("HDFC and SBI messages must not collide", hdfcHash, sbiHash)
        assertNotEquals("Axis and SBI messages must not collide", axisHash, sbiHash)
    }

    @Test
    fun `computeSmsHash produces distinct hashes for alphanumeric vs numeric senders with identical bodies`() {
        val body = "debited by Rs 100.00 at Swiggy"

        val alphanumericSenderHash = SmsParser.computeSmsHash("VK-HDFCBK", body)
        val numericSenderHash = SmsParser.computeSmsHash("9876543210", body)

        assertNotEquals(
            "Alphanumeric bank sender must produce distinct hash from numeric sender",
            alphanumericSenderHash,
            numericSenderHash,
        )
    }

    @Test
    fun `computeSmsHash trims and normalizes sender case`() {
        val body = "debited by Rs 100.00 at Swiggy"

        val upperWithSpaces = SmsParser.computeSmsHash("  VK-HDFCBK  ", body)
        val lowerNoSpaces = SmsParser.computeSmsHash("vk-hdfcbk", body)
        val mixedCase = SmsParser.computeSmsHash("Vk-HdfcBk", body)

        assertEquals("Trimming and lowercasing should make sender normalization robust", upperWithSpaces, lowerNoSpaces)
        assertEquals("Mixed case sender should produce identical hash", lowerNoSpaces, mixedCase)
    }

    @Test
    fun `computeSmsHash matches expected SHA-256 golden test vector`() {
        val sender = "VK-HDFCBK"
        val body = "debited by Rs 100.00 at Swiggy"

        // Preimage: "9:vk-hdfcbk|debited by Rs 100.00 at Swiggy"
        // SHA-256 hex digest: e1b57d7506d31d31f105ffb2f91fe750f14cced5ce2df9541e76f17df20e780e
        val expectedGoldenVector = "e1b57d7506d31d31f105ffb2f91fe750f14cced5ce2df9541e76f17df20e780e"
        val actualHash = SmsParser.computeSmsHash(sender, body)

        assertEquals("Computed hash must match exact SHA-256 golden test vector", expectedGoldenVector, actualHash)
    }

    @Test
    fun `computeSmsHash length prefix prevents delimiter injection collisions`() {
        // Without length prefixing, ("ab|c", "d") and ("ab", "c|d") would produce the same concatenated preimage "ab|c|d"
        val hash1 = SmsParser.computeSmsHash("ab|c", "d")
        val hash2 = SmsParser.computeSmsHash("ab", "c|d")
        assertNotEquals("Length prefixing prevents delimiter injection collisions", hash1, hash2)

        val hash3 = SmsParser.computeSmsHash("a|b", "c")
        val hash4 = SmsParser.computeSmsHash("a", "b|c")
        assertNotEquals("Delimiter injection across boundaries must produce distinct hashes", hash3, hash4)
    }

    @Test
    fun `computeSmsHash normalizes internal whitespace directly`() {
        val cleanBody = "debited by Rs 100.00 at Swiggy"
        val messyBody = "   debited   by   Rs   100.00   at   Swiggy   \n\t"

        val hashClean = SmsParser.computeSmsHash("VK-HDFCBK", cleanBody)
        val hashMessy = SmsParser.computeSmsHash("VK-HDFCBK", messyBody)

        assertEquals("Whitespace normalization inside computeSmsHash must produce identical hash", hashClean, hashMessy)
    }

    @Test
    fun `computeLegacySmsHash matches pre-upgrade 32-bit hashCode implementation`() {
        val alphanumericSender = "VK-HDFCBK"
        val numericSender = "9876543210"
        val body = "debited by Rs 100.00 at Swiggy"

        val legacyAlpha = SmsParser.computeLegacySmsHash(alphanumericSender, body)
        val legacyNumeric = SmsParser.computeLegacySmsHash(numericSender, body)

        // Legacy code: (sender.filter { it.isDigit() }.takeLast(10) + normalized).hashCode().toString()
        val expectedAlpha = ("" + "debited by Rs 100.00 at Swiggy").hashCode().toString()
        val expectedNumeric = ("9876543210" + "debited by Rs 100.00 at Swiggy").hashCode().toString()

        assertEquals(expectedAlpha, legacyAlpha)
        assertEquals(expectedNumeric, legacyNumeric)
    }

    @Test
    fun `computeSmsHash handles empty and whitespace-only sender robustly`() {
        val body = "debited by Rs 100.00 at Swiggy"
        val emptySenderHash = SmsParser.computeSmsHash("", body)
        val whitespaceSenderHash = SmsParser.computeSmsHash("   ", body)

        assertEquals("Empty and blank sender must produce identical hash", emptySenderHash, whitespaceSenderHash)
        assertEquals(64, emptySenderHash.length)
        assertTrue(emptySenderHash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `computeSmsHash handles empty and whitespace-only body robustly`() {
        val sender = "VK-HDFCBK"
        val emptyBodyHash = SmsParser.computeSmsHash(sender, "")
        val whitespaceBodyHash = SmsParser.computeSmsHash(sender, "   \n\t  ")

        assertEquals("Empty and blank body must produce identical hash", emptyBodyHash, whitespaceBodyHash)
        assertEquals(64, emptyBodyHash.length)
        assertTrue(emptyBodyHash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `enrichTransaction in SmsParser parse generates valid SHA-256 hash in sourceSmsHash`() =
        runBlocking {
            setupTest()
            val rawBody = "  Spent   Rs. 450.00   at Zomato\n  on 10-09-2026   from HDFC Bank A/c XX4321  "
            val sender = "VM-HDFCBK"
            val sms =
                SmsMessage(
                    id = 42L,
                    sender = sender,
                    body = rawBody,
                    date = 1773000000000L,
                )

            val parsedTxn =
                SmsParser.parse(
                    sms = sms,
                    mappings = emptyMappings,
                    customSmsRuleProvider = customSmsRuleProvider,
                    merchantRenameRuleProvider = merchantRenameRuleProvider,
                    ignoreRuleProvider = ignoreRuleProvider,
                    merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                    categoryFinderProvider = categoryFinderProvider,
                    smsParseTemplateProvider = smsParseTemplateProvider,
                )

            assertNotNull("Transaction must parse successfully", parsedTxn)
            val sourceSmsHash = parsedTxn!!.sourceSmsHash
            assertNotNull("sourceSmsHash must not be null", sourceSmsHash)
            assertEquals("sourceSmsHash length must be 64 characters", 64, sourceSmsHash!!.length)
            assertTrue("sourceSmsHash must be hex string", sourceSmsHash.matches(Regex("^[0-9a-f]{64}$")))

            val expectedHash = SmsParser.computeSmsHash(sender, rawBody)
            val expectedNormalizedHash = SmsParser.computeSmsHash(sender, rawBody.replace(Regex("\\s+"), " ").trim())
            assertEquals("computeSmsHash must produce same hash for raw and normalized body", expectedHash, expectedNormalizedHash)
            assertEquals("sourceSmsHash must match computeSmsHash", expectedHash, sourceSmsHash)
        }
}
