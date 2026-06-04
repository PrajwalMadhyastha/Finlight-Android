// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/SmsCatchupWorker.kt
// REASON: FEATURE (Reliability Safety Net) — A periodic CoroutineWorker that
// automatically recovers SMS transactions that were missed by SmsReceiver for
// any reason (OS kill, Doze mode, process death, etc.).
//
// Runs every 4 hours. On each run it:
//  1. Queries the SMS inbox for messages from the last 48 hours.
//  2. Loads the set of already-known sourceSmsHashes from the DB.
//  3. Runs each SMS through the full parsing pipeline.
//  4. For any ParseResult.Success whose hash is not in the DB → saves it silently.
//
// Duplicates are NEVER created: the hash check (same guard used by SmsReceiver)
// ensures idempotency. Saves are completely silent — no notifications fired.
// =================================================================================
package io.pm.finlight.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.CategoryFinderProvider
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.CustomSmsRuleProvider
import io.pm.finlight.IgnoreRule
import io.pm.finlight.IgnoreRuleProvider
import io.pm.finlight.MerchantCategoryMappingProvider
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.MerchantRenameRuleProvider
import io.pm.finlight.ParseResult
import io.pm.finlight.SettingsRepository
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.SmsParseTemplateProvider
import io.pm.finlight.SmsParser
import io.pm.finlight.SmsRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.SmsTransactionSaver
import kotlinx.coroutines.flow.first

class SmsCatchupWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    private val tag = "SmsCatchupWorker"

    /** Look back 48 hours for potentially missed SMS messages. */
    private val lookbackMs = 48L * 60 * 60 * 1000

    override suspend fun doWork(): Result {
        Log.d(tag, "Starting catch-up scan for missed SMS transactions...")

        val db = AppDatabase.getInstance(context)
        val settingsRepository = SettingsRepository(context)
        val saver = SmsTransactionSaver(db, settingsRepository)
        val smsRepository = SmsRepository(context)

        val startDate = System.currentTimeMillis() - lookbackMs
        val recentSms: List<SmsMessage> = smsRepository.fetchAllSms(startDate)

        if (recentSms.isEmpty()) {
            Log.d(tag, "No SMS messages found in the last 48 hours. Nothing to catch up.")
            return Result.success()
        }

        // Load current hashes once — this is our duplicate guard.
        val existingSmsHashes = db.transactionDao().getAllSmsHashes().first().toSet()

        val mappingRepository = MerchantMappingRepository(db.merchantMappingDao())
        val existingMappings = mappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })

        // --- Build providers (same as SmsProcessorWorker) ---
        val categoryFinderProvider =
            object : CategoryFinderProvider {
                override fun getCategoryIdByName(name: String): Int? = CategoryIconHelper.getCategoryIdByName(name)
            }
        val customSmsRuleProvider =
            object : CustomSmsRuleProvider {
                override suspend fun getAllRules(): List<CustomSmsRule> = db.customSmsRuleDao().getAllRules().first()
            }
        val merchantRenameRuleProvider =
            object : MerchantRenameRuleProvider {
                override suspend fun getAllRules(): List<MerchantRenameRule> = db.merchantRenameRuleDao().getAllRules().first()

                override suspend fun getAllRulesMap(): Map<String, String> =
                    db.merchantRenameRuleDao().getAllRulesList().associateBy(
                        { it.originalName.lowercase() },
                        { it.newName },
                    )
            }
        val ignoreRuleProvider =
            object : IgnoreRuleProvider {
                override suspend fun getEnabledRules(): List<IgnoreRule> = db.ignoreRuleDao().getEnabledRules()
            }
        val merchantCategoryMappingProvider =
            object : MerchantCategoryMappingProvider {
                override suspend fun getCategoryIdForMerchant(merchantName: String): Int? =
                    db.merchantCategoryMappingDao().getCategoryIdForMerchant(merchantName)

                override suspend fun getAllMappings(): Map<String, Int> =
                    db.merchantCategoryMappingDao().getAll().associateBy(
                        { it.parsedName.lowercase() },
                        { it.categoryId },
                    )
            }
        val smsParseTemplateProvider =
            object : SmsParseTemplateProvider {
                override suspend fun getAllTemplates(): List<SmsParseTemplate> = db.smsParseTemplateDao().getAllTemplates()

                override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> =
                    db.smsParseTemplateDao().getTemplatesBySignature(signature)
            }

        // Load ML models once for the entire scan batch (more efficient than per-SMS).
        val classifier = MlModelFactory.getClassifier(context)
        val nerExtractor = MlModelFactory.getNerExtractor(context)

        // Track hashes we save during this run so we don't double-save within one batch.
        val savedHashesThisRun = mutableSetOf<String>()
        var savedCount = 0

        try {
            for (sms in recentSms) {
                // --- HIERARCHY STEP 1: Custom rules ---
                var parseResult =
                    SmsParser.parseWithOnlyCustomRules(
                        sms = sms,
                        customSmsRuleProvider = customSmsRuleProvider,
                        merchantRenameRuleProvider = merchantRenameRuleProvider,
                        merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                        categoryFinderProvider = categoryFinderProvider,
                    )

                // --- HIERARCHY STEP 2: ML pre-filter ---
                if (parseResult == null) {
                    val confidence = classifier.classify(sms.body)
                    if (confidence < 0.1) continue // Not a transaction

                    // --- HIERARCHY STEP 3: NER + main parser ---
                    val nerEntities = nerExtractor.extract(sms.body)
                    parseResult =
                        SmsParser.parseWithReason(
                            sms = sms,
                            mappings = existingMappings,
                            customSmsRuleProvider = customSmsRuleProvider,
                            merchantRenameRuleProvider = merchantRenameRuleProvider,
                            ignoreRuleProvider = ignoreRuleProvider,
                            merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                            categoryFinderProvider = categoryFinderProvider,
                            smsParseTemplateProvider = smsParseTemplateProvider,
                            nerEntities = nerEntities,
                        )
                }

                if (parseResult !is ParseResult.Success) continue

                val potentialTxn = parseResult.transaction
                val hash = potentialTxn.sourceSmsHash ?: continue

                // Skip if already in DB or already saved during this run.
                if (hash in existingSmsHashes || hash in savedHashesThisRun) continue

                // Save silently — no notifications for catch-up transactions.
                val newId =
                    saver.resolveAndSaveTransaction(
                        potentialTxn = potentialTxn,
                        source = "Auto-Recovered",
                    )

                if (newId != null) {
                    savedHashesThisRun.add(hash)
                    savedCount++
                    Log.d(tag, "Recovered missed transaction: ${potentialTxn.merchantName} (₹${potentialTxn.amount})")
                }
            }
        } finally {
            classifier.close()
            nerExtractor.close()
        }

        if (savedCount > 0) {
            Log.i(tag, "Catch-up scan complete. Recovered $savedCount missed transaction(s).")
        } else {
            Log.d(tag, "Catch-up scan complete. No missed transactions found.")
        }

        return Result.success()
    }
}
