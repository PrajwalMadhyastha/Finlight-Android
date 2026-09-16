package io.pm.finlight.core.parser

import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import io.pm.finlight.Transaction
import io.pm.finlight.data.db.entity.DeletedSmsHash
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
    fun `computeSmsHash delimiter prevents cross-boundary ambiguity`() {
        // Without a delimiter, ("ab", "cd") and ("a", "bcd") would produce the same concatenated preimage
        val hash1 = SmsParser.computeSmsHash("ab", "cd")
        val hash2 = SmsParser.computeSmsHash("a", "bcd")

        assertNotEquals("Pipe delimiter prevents preimage ambiguity across boundaries", hash1, hash2)
    }

    @Test
    fun `enrichTransaction in SmsParser parse generates valid SHA-256 hash in sourceSmsHash`() =
        runBlocking {
            setupTest()
            val rawBody = "Spent Rs. 450.00 at Zomato on 10-09-2026 from HDFC Bank A/c XX4321"
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

            // Verify it exactly matches computeSmsHash with normalized body
            val normalizedBody = rawBody.replace(Regex("\\s+"), " ").trim()
            val expectedHash = SmsParser.computeSmsHash(sender, normalizedBody)
            assertEquals("sourceSmsHash must match computeSmsHash", expectedHash, sourceSmsHash)
        }

    @Test
    fun `duplicate guard entities support 64-character SHA-256 hashes`() {
        val sha256Hash = SmsParser.computeSmsHash("VK-HDFCBK", "Spent Rs 100 at Swiggy")

        // DeletedSmsHash entity check
        val deletedHash = DeletedSmsHash(sha256Hash)
        assertEquals(sha256Hash, deletedHash.smsHash)

        // Transaction entity check
        val txn =
            Transaction(
                id = 1,
                description = "Swiggy",
                categoryId = null,
                amount = 100.0,
                date = System.currentTimeMillis(),
                accountId = 1,
                notes = null,
                sourceSmsHash = sha256Hash,
            )
        assertEquals(sha256Hash, txn.sourceSmsHash)
    }
}
