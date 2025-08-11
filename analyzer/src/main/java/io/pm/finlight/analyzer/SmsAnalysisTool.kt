package io.pm.finlight.analyzer

import io.pm.finlight.CustomSmsRule
import io.pm.finlight.CustomSmsRuleProvider
import io.pm.finlight.DEFAULT_IGNORE_PHRASES
import io.pm.finlight.IgnoreRule
import io.pm.finlight.IgnoreRuleProvider
import io.pm.finlight.MerchantCategoryMappingProvider
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.MerchantRenameRuleProvider
import io.pm.finlight.ParseResult
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import io.pm.finlight.core.*
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.io.File

// =================================================================================
// Data classes for XML Deserialization
// These classes model the structure of the sms_dump.xml file.
// =================================================================================

@Serializable
@XmlSerialName("sms")
data class Sms(
    val protocol: String,
    val address: String,
    val date: String,
    val type: String,
    val subject: String?,
    val body: String,
    @XmlElement(true)
    val toa: String?,
    @XmlElement(true)
    val sc_toa: String?,
    @XmlElement(true)
    val service_center: String?,
    val read: String,
    val status: String,
    val locked: String,
    val date_sent: String,
    val readable_date: String,
    val contact_name: String
)

@Serializable
@XmlSerialName("smses")
data class Smses(
    val count: Int,
    @XmlElement(true)
    val sms: List<Sms>
)

// =================================================================================
// Main Analysis Tool
// =================================================================================

/**
 * A standalone command-line tool to analyze a dump of SMS messages and test
 * the accuracy of the SmsParser.
 */
suspend fun main() {
    println("Starting Finlight SMS Parser Analysis...")

    val xml = XML {
        ignoreUnknownChildren()
    }

    val inputFile = File("sms_dump.xml")
    if (!inputFile.exists()) {
        println("Error: sms_dump.xml not found in the project root directory.")
        return
    }

    println("Found sms_dump.xml. Reading and parsing file...")

    val smses: Smses = xml.decodeFromReader(inputFile.reader())

    println("Successfully parsed ${smses.count} messages from XML.")
    println("--------------------------------------------------")

    var parsedCount = 0
    var ignoredCount = 0
    val ignoredReasons = mutableMapOf<String, Int>()

    // Mock providers since we are not running on Android and have no access to Room DB.
    // This allows the core SmsParser to run in a pure JVM environment.
    val mockCustomRuleProvider = object : CustomSmsRuleProvider {
        override suspend fun getAllRules(): List<CustomSmsRule> = emptyList()
    }
    val mockRenameRuleProvider = object : MerchantRenameRuleProvider {
        override suspend fun getAllRules(): List<MerchantRenameRule> = emptyList()
    }
    val mockIgnoreRuleProvider = object : IgnoreRuleProvider {
        override suspend fun getEnabledRules(): List<IgnoreRule> = DEFAULT_IGNORE_PHRASES
    }
    val mockCategoryMappingProvider = object : MerchantCategoryMappingProvider {
        override suspend fun getCategoryIdForMerchant(merchantName: String): Int? = null
    }

    // Analyze each message
    smses.sms.forEach { sms ->
        val smsMessage = SmsMessage(
            id = sms.date.toLongOrNull() ?: 0L,
            sender = sms.address,
            body = sms.body,
            date = sms.date.toLongOrNull() ?: 0L
        )

        val result = SmsParser.parse(
            sms = smsMessage,
            mappings = emptyMap(), // No pre-existing mappings for this test
            customSmsRuleProvider = mockCustomRuleProvider,
            merchantRenameRuleProvider = mockRenameRuleProvider,
            ignoreRuleProvider = mockIgnoreRuleProvider,
            merchantCategoryMappingProvider = mockCategoryMappingProvider
        )

        when (result) {
            is ParseResult.Success -> {
                parsedCount++
                println("[SUCCESS] Parsed: ${result.transaction.merchantName} - ${result.transaction.amount}")
            }
            is ParseResult.Ignored -> {
                ignoredCount++
                val reason = result.reason
                ignoredReasons[reason] = (ignoredReasons[reason] ?: 0) + 1
            }
            null -> {
                // This case is for when the parser simply doesn't find a match,
                // which is different from being explicitly ignored.
                ignoredCount++
                val reason = "No financial pattern found"
                ignoredReasons[reason] = (ignoredReasons[reason] ?: 0) + 1
            }
        }
    }

    // Print the final report
    println("\n================ ANALYSIS COMPLETE ================")
    println("Total Messages Analyzed: ${smses.count}")
    println("Successfully Parsed: $parsedCount")
    println("Ignored / Not Parsed: $ignoredCount")
    println("\n--- Ignored Reasons Breakdown ---")
    ignoredReasons.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
        println("${reason.padEnd(40, ' ')}: $count")
    }
    println("================================================")
}
