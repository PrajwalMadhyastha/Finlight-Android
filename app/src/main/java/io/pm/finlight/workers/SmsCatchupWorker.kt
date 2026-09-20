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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.CustomSmsRuleProvider
import io.pm.finlight.IgnoreRule
import io.pm.finlight.IgnoreRuleProvider
import io.pm.finlight.MerchantCategoryMappingProvider
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.MerchantRenameRuleProvider
import io.pm.finlight.ParseResult
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.SmsParseTemplateProvider
import io.pm.finlight.SmsParser
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.utils.SmsProviderHelper
import io.pm.finlight.utils.SmsTransactionSaver
import kotlinx.coroutines.flow.first

class SmsCatchupWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val MAX_BATCH_SIZE = 150
    }

    private val tag = "SmsCatchupWorker"

    /** Look back 48 hours for potentially missed SMS messages. */
    private val lookbackMs = 48L * 60 * 60 * 1000

    /** Cooldown buffer: leave real-time SMS from last 10 minutes to SmsProcessorWorker. */
    private val cooldownMs = 10L * 60 * 1000

    override suspend fun doWork(): Result {
        Log.d(tag, "Starting catch-up scan for missed SMS transactions...")

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(tag, "READ_SMS permission not granted. Skipping catch-up scan.")
            return Result.success()
        }

        val db = AppDatabase.getInstance(context)
        val settingsRepository = ServiceLocator.provideSettingsRepository(context)
        val tagRepository = ServiceLocator.provideTagRepository(context)
        val resolveTravelModeTagUseCase = ResolveTravelModeTagUseCase(tagRepository)
        val saver = SmsTransactionSaver(context, resolveTravelModeTagUseCase, db)
        val smsRepository = ServiceLocator.provideSmsRepository(context)

        val now = System.currentTimeMillis()
        val endDate = now - cooldownMs
        val startDate = endDate - lookbackMs
        val recentSms: List<SmsMessage> =
            smsRepository.fetchAllSms(startDate = startDate, endDate = endDate).take(MAX_BATCH_SIZE)

        if (recentSms.isEmpty()) {
            Log.d(tag, "No SMS messages found in the catch-up window. Nothing to catch up.")
            return Result.success()
        }

        // Load current hashes once — this is our duplicate guard.
        val existingSmsHashes = db.transactionQueryDao().getAllSmsHashes().first().toMutableSet()

        // Load deleted hashes — transactions the user intentionally removed should
        // never be re-created by this worker, even if their SMS reappears in the inbox.
        val deletedHashes = db.deletedSmsHashDao().getAllHashes().toMutableSet()

        val mappingRepository = MerchantMappingRepository(db.merchantMappingDao())
        val existingMappings = mappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })

        // Pre-load all rules, mappings, and templates once before the loop to avoid redundant SQLite queries.
        val customRules = db.customSmsRuleDao().getAllRules().first()
        val renameRules = db.merchantRenameRuleDao().getAllRulesList()
        val renameRulesMap = renameRules.associateBy({ it.originalName.lowercase() }, { it.newName })
        val ignoreRules = db.ignoreRuleDao().getEnabledRules()
        val categoryMappings = db.merchantCategoryMappingDao().getAll()
        val categoryMappingsMap = categoryMappings.associateBy({ it.parsedName.lowercase() }, { it.categoryId })
        val allTemplates = db.smsParseTemplateDao().getAllTemplates()
        val templatesBySignature = allTemplates.groupBy { it.templateSignature }

        val categoryFinderProvider = SmsProviderHelper.getCategoryFinderProvider()
        val customSmsRuleProvider =
            object : CustomSmsRuleProvider {
                override suspend fun getAllRules(): List<CustomSmsRule> = customRules
            }
        val merchantRenameRuleProvider =
            object : MerchantRenameRuleProvider {
                override suspend fun getAllRules(): List<MerchantRenameRule> = renameRules

                override suspend fun getAllRulesMap(): Map<String, String> = renameRulesMap
            }
        val ignoreRuleProvider =
            object : IgnoreRuleProvider {
                override suspend fun getEnabledRules(): List<IgnoreRule> = ignoreRules
            }
        val merchantCategoryMappingProvider =
            object : MerchantCategoryMappingProvider {
                override suspend fun getCategoryIdForMerchant(merchantName: String): Int? =
                    categoryMappingsMap[merchantName.lowercase()]

                override suspend fun getAllMappings(): Map<String, Int> = categoryMappingsMap
            }
        val smsParseTemplateProvider =
            object : SmsParseTemplateProvider {
                override suspend fun getAllTemplates(): List<SmsParseTemplate> = allTemplates

                override suspend fun getTemplatesBySignature(signature: String): List<SmsParseTemplate> =
                    templatesBySignature[signature] ?: emptyList()
            }

        // Load ML models once for the entire scan batch (more efficient than per-SMS).
        val classifier = MlModelFactory.getClassifier(context)
        val nerExtractor = MlModelFactory.getNerExtractor(context)

        // Load travel settings once for the entire scan batch.
        val travelSettings = settingsRepository.getCurrentTravelModeSettings()

        // Track hashes we save during this run so we don't double-save within one batch.
        val savedHashesThisRun = mutableSetOf<String>()
        var savedCount = 0

        try {
            for (sms in recentSms) {
                if (isStopped) {
                    Log.i(tag, "Worker stopped. Exiting catch-up processing loop early.")
                    break
                }

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
                    Log.d(tag, "NER extraction complete. Entity types found: ${nerEntities.keys}")
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
                val currentHash = potentialTxn.sourceSmsHash ?: continue
                val legacyHash = SmsParser.computeLegacySmsHash(sms.sender, sms.body)

                // Skip if already in DB, already saved during this run,
                // or intentionally deleted by the user.
                if (currentHash in existingSmsHashes || legacyHash in existingSmsHashes ||
                    currentHash in deletedHashes || legacyHash in deletedHashes ||
                    currentHash in savedHashesThisRun
                ) {
                    if (legacyHash in existingSmsHashes) {
                        db.transactionWriteDao().updateSmsHashByLegacy(oldHash = legacyHash, newHash = currentHash)
                        existingSmsHashes.remove(legacyHash)
                        existingSmsHashes.add(currentHash)
                    }
                    if (legacyHash in deletedHashes) {
                        db.deletedSmsHashDao().insert(DeletedSmsHash(currentHash))
                        deletedHashes.add(currentHash)
                    }
                    continue
                }

                // Dynamic TOCTOU check against DB in case real-time worker saved it concurrently
                if (db.transactionQueryDao().existsBySmsHash(currentHash) || db.transactionQueryDao().existsBySmsHash(legacyHash)) {
                    if (db.transactionQueryDao().existsBySmsHash(legacyHash)) {
                        db.transactionWriteDao().updateSmsHashByLegacy(oldHash = legacyHash, newHash = currentHash)
                    }
                    existingSmsHashes.add(currentHash)
                    continue
                }

                // Save silently — no notifications for catch-up transactions.
                val newId =
                    saver.resolveAndSaveTransaction(
                        potentialTxn = potentialTxn,
                        travelSettings = travelSettings,
                        source = "Auto-Recovered",
                    )

                if (newId != null) {
                    existingSmsHashes.add(currentHash)
                    savedHashesThisRun.add(currentHash)
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
