// =================================================================================
// FILE: ./analyzer/src/main/java/io/pm/finlight/analyzer/SmsAnalysisTool.kt
// REASON: FIX - Added mock implementations for the new CategoryFinderProvider
// and SmsParseTemplateProvider interfaces. This resolves the build error by
// satisfying the updated, decoupled signature of the SmsParser.
// =================================================================================
package io.pm.finlight.analyzer

import io.pm.finlight.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =================================================================================
// Data classes for JSON Deserialization
// REASON: The data classes have been updated with @SerialName annotations to
// flexibly handle the JSON output from the `yq` tool, which prefixes
// attributes with "+@". This makes the analyzer more robust.
// =================================================================================

@Serializable
data class SmsDump(
    val smses: SmsesContainer
)

@Serializable
data class SmsesContainer(
    @SerialName("+@count")
    val count: String,
    val sms: List<Sms>
)

@Serializable
data class Sms(
    @SerialName("+@address")
    val address: String,
    @SerialName("+@date")
    val date: String,
    @SerialName("+@body")
    val body: String,
    @SerialName("+@readable_date")
    val readable_date: String,
    @SerialName("+@contact_name")
    val contact_name: String
)

// =================================================================================
// Data classes for CSV Report Generation
// =================================================================================

data class ParsedTransactionRecord(
    val sender: String,
    val body: String,
    val date: String,
    val amount: Double,
    val type: String,
    val merchant: String?,
    val account: String?
)

data class IgnoredMessageRecord(
    val sender: String,
    val body: String,
    val date: String,
    val reason: String
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

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val inputFile = File("analyzer/src/main/resources/sms_dump.json")
    if (!inputFile.exists()) {
        println("Error: sms_dump.json not found in analyzer/src/main/resources/")
        return
    }

    println("Found sms_dump.json. Reading and parsing file...")

    val smsDump: SmsDump = json.decodeFromString(inputFile.readText())
    val smsList = smsDump.smses.sms
    val totalSmsCount = smsDump.smses.count.toIntOrNull() ?: smsList.size


    println("Successfully parsed $totalSmsCount messages from JSON.")
    println("--------------------------------------------------")

    val parsedTransactions = mutableListOf<ParsedTransactionRecord>()
    val ignoredMessages = mutableListOf<IgnoredMessageRecord>()
    val notParsedMessages = mutableListOf<IgnoredMessageRecord>()


    // --- Create Mock Providers for the Standalone Tool ---
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
    // --- NEW: Add mocks for the new providers ---
    val mockCategoryFinderProvider = object : CategoryFinderProvider {
        override fun getCategoryIdByName(name: String): Int? = null // Not needed for this tool
    }
    val mockSmsParseTemplateProvider = object : SmsParseTemplateProvider {
        override suspend fun getAllTemplates(): List<SmsParseTemplate> = emptyList() // No DB access
        override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> = emptyList()
    }

    smsList.forEach { sms ->
        val smsMessage = SmsMessage(
            id = sms.date.toLongOrNull() ?: 0L,
            sender = sms.address,
            body = sms.body,
            date = sms.date.toLongOrNull() ?: 0L
        )

        val result = SmsParser.parseWithReason(
            sms = smsMessage,
            mappings = emptyMap(),
            customSmsRuleProvider = mockCustomRuleProvider,
            merchantRenameRuleProvider = mockRenameRuleProvider,
            ignoreRuleProvider = mockIgnoreRuleProvider,
            merchantCategoryMappingProvider = mockCategoryMappingProvider,
            categoryFinderProvider = mockCategoryFinderProvider, // Pass mock
            smsParseTemplateProvider = mockSmsParseTemplateProvider // Pass mock
        )

        when (result) {
            is ParseResult.Success -> {
                val txn = result.transaction
                parsedTransactions.add(
                    ParsedTransactionRecord(
                        sender = sms.address,
                        body = sms.body,
                        date = sms.readable_date,
                        amount = txn.amount,
                        type = txn.transactionType,
                        merchant = txn.merchantName,
                        account = txn.potentialAccount?.formattedName
                    )
                )
            }
            is ParseResult.Ignored -> {
                ignoredMessages.add(
                    IgnoredMessageRecord(
                        sender = sms.address,
                        body = sms.body,
                        date = sms.readable_date,
                        reason = result.reason
                    )
                )
            }
            is ParseResult.NotParsed -> {
                notParsedMessages.add(
                    IgnoredMessageRecord(
                        sender = sms.address,
                        body = sms.body,
                        date = sms.readable_date,
                        reason = result.reason
                    )
                )
            }
            is ParseResult.IgnoredByClassifier -> {
                ignoredMessages.add(
                    IgnoredMessageRecord(
                        sender = sms.address,
                        body = sms.body,
                        date = sms.readable_date,
                        reason = result.reason
                    )
                )
            }
        }
    }

    // --- Generate CSV Reports ---
    generateCsvReport("parsed_transactions.csv", parsedTransactions)
    generateCsvReport("ignored_messages.csv", ignoredMessages)
    generateCsvReport("not_parsed_messages.csv", notParsedMessages) // Report for unparsed messages

    // --- Print Console Summary ---
    printSummary(totalSmsCount, parsedTransactions.size, ignoredMessages.size, notParsedMessages.size, ignoredMessages)
}

/**
 * A generic function to generate a CSV report from a list of data objects.
 */
private fun <T> generateCsvReport(fileName: String, data: List<T>) {
    if (data.isEmpty()) return

    val file = File(fileName)
    file.printWriter().use { out ->
        // Write header
        val header = when (data.firstOrNull()) {
            is ParsedTransactionRecord -> "Sender,Date,Amount,Type,Merchant,Account,Body"
            is IgnoredMessageRecord -> "Sender,Date,Reason,Body"
            else -> return
        }
        out.println(header)

        // Write rows
        data.forEach { item ->
            val row = when (item) {
                is ParsedTransactionRecord -> with(item) {
                    "\"$sender\",\"$date\",$amount,$type,\"${merchant ?: ""}\",\"${account ?: ""}\",\"${body.replace("\"", "\"\"")}\""
                }
                is IgnoredMessageRecord -> with(item) {
                    "\"$sender\",\"$date\",\"$reason\",\"${body.replace("\"", "\"\"")}\""
                }
                else -> ""
            }
            out.println(row)
        }
    }
    println("Successfully generated report: $fileName")
}

/**
 * Prints a summary of the analysis to the console.
 */
private fun printSummary(totalCount: Int, parsedCount: Int, ignoredCount: Int, notParsedCount: Int, ignoredMessages: List<IgnoredMessageRecord>) {
    val ignoredReasons = ignoredMessages.groupingBy { it.reason }.eachCount()

    println("\n================ ANALYSIS COMPLETE ================")
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    println("Report generated on: $timestamp")
    println("--------------------------------------------------")
    println("Total Messages Analyzed: $totalCount")
    println("Successfully Parsed:     $parsedCount (${String.format("%.2f", (parsedCount.toDouble() / totalCount) * 100)}%)")
    println("Ignored:                 $ignoredCount (${String.format("%.2f", (ignoredCount.toDouble() / totalCount) * 100)}%)")
    println("Not Parsed:              $notParsedCount (${String.format("%.2f", (notParsedCount.toDouble() / totalCount) * 100)}%)")
    println("\n--- Ignored Reasons Breakdown (Top 15) ---")
    ignoredReasons.entries.sortedByDescending { it.value }.take(15).forEach { (reason, count) ->
        println("${reason.padEnd(50, ' ')}: $count")
    }
    println("\nFull reports saved to parsed_transactions.csv, ignored_messages.csv, and not_parsed_messages.csv")
    println("================================================")
}