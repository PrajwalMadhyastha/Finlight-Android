// =================================================================================
// FILE: ./analyzer/src/main/kotlin/SmsAnalysisTool.kt
// REASON: NEW FILE - This is the main entry point for our command-line SMS
// analysis tool. It implements the full Phase 2 logic: reading an SMS dump,
// using the shared SmsParser from the ':core' module, classifying results
// (Parsed, Ignored with reason), and generating timestamped .txt and .csv
// reports with detailed statistics.
// =================================================================================
package io.pm.finlight.analyzer
import io.pm.finlight.*
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.system.measureTimeMillis

// --- Mock Providers for the Parser ---
// These allow the core SmsParser to run without needing a real Android database.
// For the analyzer, we only need the default ignore rules.
object MockCustomSmsRuleProvider : CustomSmsRuleProvider {
    override suspend fun getAllRules(): List<CustomSmsRule> = emptyList()
}

object MockMerchantRenameRuleProvider : MerchantRenameRuleProvider {
    override suspend fun getAllRules(): List<MerchantRenameRule> = emptyList()
}

object MockIgnoreRuleProvider : IgnoreRuleProvider {
    // The analyzer will use the default, hardcoded ignore rules from the core module.
    override suspend fun getEnabledRules(): List<IgnoreRule> = DEFAULT_IGNORE_PHRASES
}

object MockMerchantCategoryMappingProvider : MerchantCategoryMappingProvider {
    override suspend fun getCategoryIdForMerchant(merchantName: String): Int? = null
}

/**
 * The main entry point for the SMS analysis command-line tool.
 */
fun main() = runBlocking {
    println("Starting Finlight SMS Analysis...")

    val executionTime = measureTimeMillis {
        // --- Configuration ---
        val inputFileName = "analyzer/src/main/resources/sms_dump.txt"
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val outputReportFileName = "analysis_report_$timestamp.txt"
        val outputCsvFileName = "ignored_messages_$timestamp.csv"

        // --- Data Processing ---
        val inputFile = File(inputFileName)
        if (!inputFile.exists()) {
            println("ERROR: Input file not found at '$inputFileName'")
            println("Please create the file and populate it with SMS messages, one per line.")
            // Create a placeholder file to guide the user
            inputFile.parentFile.mkdirs()
            inputFile.writeText("Paste your SMS messages here, one per line.\nExample: Sent Rs.100 to John Doe from account 1234.")
            println("A placeholder file has been created for you.")
            return@runBlocking
        }

        val allMessages = inputFile.readLines().filter { it.isNotBlank() }
        val parsedTransactions = mutableListOf<Pair<String, PotentialTransaction>>()
        val ignoredMessages = mutableListOf<Pair<String, String>>()
        val ignoredForCsv = mutableListOf<List<String>>()

        println("Analyzing ${allMessages.size} messages from '$inputFileName'...")

        allMessages.forEachIndexed { index, smsBody ->
            // Create a mock SmsMessage object for the parser.
            val mockSms = SmsMessage(id = index.toLong(), sender = "UNKNOWN", body = smsBody, date = System.currentTimeMillis())

            // Use the enhanced parser which returns a sealed class result.
            when (val result = SmsParser.parseWithReason(mockSms, emptyMap(), MockCustomSmsRuleProvider, MockMerchantRenameRuleProvider, MockIgnoreRuleProvider, MockMerchantCategoryMappingProvider)) {
                is ParseResult.Success -> {
                    parsedTransactions.add(smsBody to result.transaction)
                }
                is ParseResult.Ignored -> {
                    ignoredMessages.add(smsBody to result.reason)
                    ignoredForCsv.add(listOf(mockSms.sender, smsBody, result.reason))
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
    parsed: List<Pair<String, PotentialTransaction>>,
    ignored: List<Pair<String, String>>
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
            out.println("SMS: $original")
            out.println("  -> Amount: ${txn.amount}, Type: ${txn.transactionType}, Merchant: ${txn.merchantName}, Account: ${txn.potentialAccount?.formattedName ?: "N/A"}")
        }
        out.println()

        out.println("========================================")
        out.println(" Ignored Messages ($ignoredCount)")
        out.println("========================================")
        ignored.forEach { (original, reason) ->
            out.println("----------------------------------------")
            out.println("SMS: $original")
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
