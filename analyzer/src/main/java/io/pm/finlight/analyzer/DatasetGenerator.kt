// =================================================================================
// FILE: ./analyzer/src/main/java/io/pm/finlight/analyzer/DatasetGenerator.kt
// REASON: FIX - The script has been updated to use the new, uniquely named
// data classes (DatasetSmsDump, etc.) from DatasetJsonModels.kt. This resolves
// the "Redeclaration" build error by eliminating the name conflict with
// SmsAnalysisTool.kt.
// =================================================================================
package io.pm.finlight.analyzer

import io.pm.finlight.*
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data classes for JSON Deserialization are now in DatasetJsonModels.kt

// =================================================================================
// Data class for the final labeled output
// =================================================================================

data class LabeledSms(
    val text: String,
    val label: Int // 1 for Transaction, 0 for Non-Transaction
)

/**
 * A standalone command-line tool to generate a labeled CSV dataset from raw
 * SMS JSON dumps for training a text classification model.
 */
suspend fun main() {
    println("Starting Finlight Dataset Generator...")

    // List of JSON files to process. Add all your dump files here.
    val inputFilenames = listOf(
        "anonymized_ap_sms.json",
        "anonymized_ka_sms.json",
        "anonymized_pg_sms.json",
        "anonymized_pm_sms.json"
        // Add other filenames like "anonymized_user2.json" here
    )
    val inputDirectory = "analyzer/src/main/resources/"
    val outputCsvFile = "training_data.csv"

    val allSmsEntries = mutableListOf<DatasetSmsEntry>()
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // --- 1. Read and Parse all JSON files ---
    inputFilenames.forEach { filename ->
        val inputFile = File(inputDirectory + filename)
        if (!inputFile.exists()) {
            println("Warning: Input file not found, skipping: ${inputFile.path}")
            return@forEach
        }
        println("Reading and parsing ${inputFile.path}...")
        try {
            val smsDump: DatasetSmsDump = json.decodeFromString(inputFile.readText())
            allSmsEntries.addAll(smsDump.smses.sms)
        } catch (e: Exception) {
            println("Error parsing JSON from $filename: ${e.message}")
        }
    }

    if (allSmsEntries.isEmpty()) {
        println("Error: No SMS messages were loaded. Make sure your JSON files are in '$inputDirectory'.")
        return
    }

    println("Successfully loaded a total of ${allSmsEntries.size} messages from all files.")
    println("--------------------------------------------------")

    val labeledSmsList = mutableListOf<LabeledSms>()

    // --- 2. Setup Mock Providers for the Standalone Parser ---
    // These providers allow the core SmsParser to run without a real Android database.
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
    val mockCategoryFinderProvider = object : CategoryFinderProvider {
        override fun getCategoryIdByName(name: String): Int? = null
    }
    val mockSmsParseTemplateProvider = object : SmsParseTemplateProvider {
        override suspend fun getAllTemplates(): List<SmsParseTemplate> = emptyList()
    }

    // --- 3. Process each SMS message ---
    allSmsEntries.forEach { sms ->
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
            categoryFinderProvider = mockCategoryFinderProvider,
            smsParseTemplateProvider = mockSmsParseTemplateProvider
        )

        val labeledSms = when (result) {
            is ParseResult.Success -> LabeledSms(text = sms.body, label = 1) // TRANSACTION
            is ParseResult.Ignored, is ParseResult.NotParsed -> LabeledSms(text = sms.body, label = 0) // NON_TRANSACTION
        }
        labeledSmsList.add(labeledSms)
    }

    // --- 4. Generate the final CSV file ---
    generateCsv(outputCsvFile, labeledSmsList)

    // --- 5. Print Summary ---
    printSummary(labeledSmsList)
}

/**
 * Escapes a string for CSV format by adding quotes and escaping internal quotes.
 */
private fun escapeCsvField(field: String): String {
    val escaped = field.replace("\"", "\"\"")
    return if (escaped.contains(',') || escaped.contains('\n') || escaped.contains('"')) {
        "\"$escaped\""
    } else {
        escaped
    }
}

/**
 * Writes the labeled SMS data to a CSV file.
 */
private fun generateCsv(fileName: String, data: List<LabeledSms>) {
    if (data.isEmpty()) {
        println("No data to write to CSV.")
        return
    }

    val file = File(fileName)
    file.printWriter().use { out ->
        // Write header
        out.println("text,label")
        // Write rows
        data.forEach { item ->
            out.println("${escapeCsvField(item.text)},${item.label}")
        }
    }
    println("Successfully generated dataset: $fileName")
}

/**
 * Prints a summary of the dataset generation to the console.
 */
private fun printSummary(data: List<LabeledSms>) {
    val transactionCount = data.count { it.label == 1 }
    val nonTransactionCount = data.count { it.label == 0 }

    println("\n================ DATASET GENERATION COMPLETE ================")
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    println("Report generated on: $timestamp")
    println("--------------------------------------------------")
    println("Total Messages Processed:  ${data.size}")
    println("Labeled 'TRANSACTION':     $transactionCount (${String.format("%.2f", (transactionCount.toDouble() / data.size) * 100)}%)")
    println("Labeled 'NON_TRANSACTION': $nonTransactionCount (${String.format("%.2f", (nonTransactionCount.toDouble() / data.size) * 100)}%)")
    println("\nDataset saved to training_data.csv in the project root.")
    println("============================================================")
}