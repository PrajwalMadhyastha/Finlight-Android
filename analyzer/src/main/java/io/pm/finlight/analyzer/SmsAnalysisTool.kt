// =================================================================================
// FILE: ./analyzer/src/main/kotlin/SmsAnalysisTool.kt
// REASON: FIX - Corrected build errors related to XML parsing. Replaced the
// incorrect annotations with '@XmlSerialName' and updated the XML parser
// configuration to correctly ignore unknown attributes, resolving all
// unresolved reference and type mismatch errors.
// =================================================================================
package io.pm.finlight.analyzer

import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import io.pm.finlight.*
import kotlinx.coroutines.runBlocking
import nl.adaptivity.xmlutil.serialization.XML
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.measureTimeMillis
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

// --- Mock Providers for the Parser ---
object MockCustomSmsRuleProvider : CustomSmsRuleProvider {
    override suspend fun getAllRules(): List<CustomSmsRule> = emptyList()
}

object MockMerchantRenameRuleProvider : MerchantRenameRuleProvider {
    override suspend fun getAllRules(): List<MerchantRenameRule> = emptyList()
}

object MockIgnoreRuleProvider : IgnoreRuleProvider {
    override suspend fun getEnabledRules(): List<IgnoreRule> = DEFAULT_IGNORE_PHRASES
}

object MockMerchantCategoryMappingProvider : MerchantCategoryMappingProvider {
    override suspend fun getCategoryIdForMerchant(merchantName: String): Int? = null
}

// --- Data classes for XML Deserialization ---
@Serializable
@XmlSerialName("sms")
data class SmsEntry(
    val address: String,
    val body: String,
    val date: Long
)

@Serializable
@XmlSerialName("smses")
data class SmsDump(
    val sms: List<SmsEntry>
)

/**
 * The main entry point for the SMS analysis command-line tool.
 */
fun main() = runBlocking {
    println("Starting Finlight SMS Analysis (XML Mode)...")

    val executionTime = measureTimeMillis {
        // --- Configuration ---
        val inputFileName = "analyzer/src/main/resources/sms_dump.xml"
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val outputReportFileName = "analysis_report_$timestamp.txt"
        val outputCsvFileName = "ignored_messages_$timestamp.csv"

        // --- Data Processing ---
        val inputFile = File(inputFileName)
        if (!inputFile.exists()) {
            println("ERROR: Input file not found at '$inputFileName'")
            inputFile.parentFile.mkdirs()
            inputFile.writeText("""
                <?xml version='1.0' encoding='utf-8'?>
                <smses>
                  <sms protocol="0" address="AX-UPSCEX" date="1627052829864" type="1" subject="null" body="Sample SMS body text here."/>
                </smses>
            """.trimIndent())
            println("A placeholder file has been created for you.")
            return@runBlocking
        }

        val xml = XML {
            ignoreUnknownChildren = true
        }
        val smsDumpText = inputFile.readText()
        val smsDump = xml.decodeFromString<SmsDump>(smsDumpText)
        val allMessages = smsDump.sms

        val parsedTransactions = mutableListOf<Pair<SmsEntry, PotentialTransaction>>()
        val ignoredMessages = mutableListOf<Pair<SmsEntry, String>>()
        val ignoredForCsv = mutableListOf<List<String>>()

        println("Analyzing ${allMessages.size} messages from '$inputFileName'...")

        allMessages.forEachIndexed { index, smsEntry ->
            val mockSms = SmsMessage(id = index.toLong(), sender = smsEntry.address, body = smsEntry.body, date = smsEntry.date)

            when (val result = SmsParser.parseWithReason(mockSms, emptyMap(), MockCustomSmsRuleProvider, MockMerchantRenameRuleProvider, MockIgnoreRuleProvider, MockMerchantCategoryMappingProvider)) {
                is ParseResult.Success -> {
                    parsedTransactions.add(smsEntry to result.transaction)
                }
                is ParseResult.Ignored -> {
                    ignoredMessages.add(smsEntry to result.reason)
                    ignoredForCsv.add(listOf(smsEntry.address, smsEntry.body, result.reason))
                }
            }
        }

        println("Analysis complete. Generating reports...")

        // --- Report Generation ---
        generateTextReport(outputReportFileName, allMessages.size, parsedTransactions, ignoredMessages)
        generateCsvReport(outputCsvFileName, ignoredForCsv)
    }

    println("Analysis finished in ${executionTime / 1000.0} seconds.")
}

/**
 * Generates the detailed, human-readable text report.
 */
private fun generateTextReport(
    fileName: String,
    totalCount: Int,
    parsed: List<Pair<SmsEntry, PotentialTransaction>>,
    ignored: List<Pair<SmsEntry, String>>
) {
    val parsedCount = parsed.size
    val ignoredCount = ignored.size
    val parsedPercent = if (totalCount > 0) (parsedCount.toDouble() / totalCount * 100) else 0.0

    File(fileName).printWriter().use { out ->
        out.println("========================================")
        out.println(" Finlight SMS Parser Analysis Report")
        out.println("========================================")
        out.println("Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}")
        out.println()

        out.println("--- Summary ---")
        out.println("Total Messages Analyzed: $totalCount")
        out.println("Successfully Parsed: $parsedCount (${"%.2f".format(parsedPercent)}%)")
        out.println("Ignored as Noise: $ignoredCount")
        out.println()
        out.println("--- Metrics (Manual Entry for Improvement Tracking) ---")
        out.println("False Negatives Found (Should have been parsed): __")
        out.println("False Positives Found (Parsed incorrectly): __")
        out.println()

        out.println("========================================")
        out.println(" Parsed Transactions ($parsedCount)")
        out.println("========================================")
        parsed.forEach { (original, txn) ->
            out.println("----------------------------------------")
            out.println("SMS: ${original.body}")
            out.println("  -> Amount: ${txn.amount}, Type: ${txn.transactionType}, Merchant: ${txn.merchantName}, Account: ${txn.potentialAccount?.formattedName ?: "N/A"}")
        }
        out.println()

        out.println("========================================")
        out.println(" Ignored Messages ($ignoredCount)")
        out.println("========================================")
        ignored.forEach { (original, reason) ->
            out.println("----------------------------------------")
            out.println("SMS: ${original.body}")
            out.println("  -> Reason: $reason")
        }
    }
    println("Text report generated: '$fileName'")
}

/**
 * Generates a CSV file of ignored messages for easy sorting and filtering.
 */
private fun generateCsvReport(fileName: String, data: List<List<String>>) {
    csvWriter().open(fileName) {
        writeRow("Sender", "Body", "ReasonForIgnore")
        writeRows(data)
    }
    println("CSV report for ignored messages generated: '$fileName'")
}
