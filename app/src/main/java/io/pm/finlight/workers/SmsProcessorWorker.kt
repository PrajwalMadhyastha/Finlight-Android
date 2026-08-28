// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/SmsProcessorWorker.kt
// REASON: FEATURE + BUG FIX — Moves the heavy SMS processing pipeline (ML
// classification, NER extraction, regex parsing, DB writes) out of
// SmsReceiver's BroadcastReceiver context into a CoroutineWorker.
//
// This fixes Bug #2: a BroadcastReceiver using goAsync() can be killed by the
// Android OS (Doze mode, memory pressure) before the ML inference and DB writes
// complete. A CoroutineWorker is granted up to 10 minutes of guaranteed
// execution time by WorkManager.
//
// Uses SmsTransactionSaver which contains the Bug #1 fix (IGNORE → findByName
// account fallback).
// =================================================================================
package io.pm.finlight.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.ParseResult
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import io.pm.finlight.TagRepository
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionNotificationWorker
import kotlinx.coroutines.flow.first
import io.pm.finlight.TransactionType
import io.pm.finlight.TripType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.SmsProviderHelper
import io.pm.finlight.utils.SmsTransactionSaver
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Date

@Suppress("DEPRECATION")
class SmsProcessorWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val KEY_SENDER = "sms_sender"
        const val KEY_BODY = "sms_body"
        const val KEY_DATE = "sms_date"
    }

    private val tag = "SmsProcessorWorker"

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val date = inputData.getLong(KEY_DATE, -1L).takeIf { it != -1L } ?: return Result.failure()

        val smsMessage = SmsMessage(id = date, sender = sender, body = body, date = date)

        val db = AppDatabase.getInstance(context)
        val settingsRepository = ServiceLocator.provideSettingsRepository(context)
        val tagRepository = TagRepository(db.tagDao(), db.transactionQueryDao())
        val resolveTravelModeTagUseCase = ResolveTravelModeTagUseCase(tagRepository)
        val saver = SmsTransactionSaver(db, resolveTravelModeTagUseCase)

        val mappingRepository = MerchantMappingRepository(db.merchantMappingDao())
        val existingMappings = mappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })
        val existingSmsHashes = db.transactionQueryDao().getAllSmsHashes().first().toSet()
        // Permanently skipped hashes (user deliberately deleted these transactions).
        val deletedHashes = db.deletedSmsHashDao().getAllHashes().toSet()

        // --- Build providers ---
        val categoryFinderProvider = SmsProviderHelper.getCategoryFinderProvider()
        val customSmsRuleProvider = SmsProviderHelper.getCustomSmsRuleProvider(db)
        val merchantRenameRuleProvider = SmsProviderHelper.getMerchantRenameRuleProvider(db)
        val ignoreRuleProvider = SmsProviderHelper.getIgnoreRuleProvider(db)
        val merchantCategoryMappingProvider = SmsProviderHelper.getMerchantCategoryMappingProvider(db)
        val smsParseTemplateProvider = SmsProviderHelper.getSmsParseTemplateProvider(db)

        // --- HIERARCHY STEP 1: Check custom rules ---
        var parseResult =
            SmsParser.parseWithOnlyCustomRules(
                sms = smsMessage,
                customSmsRuleProvider = customSmsRuleProvider,
                merchantRenameRuleProvider = merchantRenameRuleProvider,
                merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                categoryFinderProvider = categoryFinderProvider,
            )

        // --- HIERARCHY STEP 2: ML pre-filter ---
        if (parseResult == null) {
            val classifier = MlModelFactory.getClassifier(context)
            val confidence = classifier.classify(body)
            classifier.close()

            if (confidence < 0.1) {
                Log.d(tag, "ML model ignored SMS (confidence=${1 - confidence}). Sender: $sender")
                return Result.success()
            }

            // --- HIERARCHY STEP 3: NER + main parser ---
            val nerExtractor = MlModelFactory.getNerExtractor(context)
            val nerEntities = nerExtractor.extract(body)
            nerExtractor.close()

            Log.d(tag, "NER entities: $nerEntities")

            parseResult =
                SmsParser.parseWithReason(
                    sms = smsMessage,
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

        if (parseResult !is ParseResult.Success) {
            Log.d(tag, "SMS not parsed as a transaction. Result: $parseResult")
            return Result.success()
        }

        // --- Auto-healing: persist newly discovered rename/category aliases ---
        parseResult.newlyDiscoveredRenameAlias?.let { (oldName, newName) ->
            Log.d(tag, "Auto-healing rename rule: $oldName → $newName")
            db.merchantRenameRuleDao().insert(MerchantRenameRule(oldName, newName))
        }
        parseResult.newlyDiscoveredCategoryAlias?.let { (merchant, catId) ->
            Log.d(tag, "Auto-healing category rule: $merchant → $catId")
            db.merchantCategoryMappingDao().insert(MerchantCategoryMapping(merchant, catId))
        }

        val potentialTxn = parseResult.transaction

        // --- Duplicate guard ---
        val hash = potentialTxn.sourceSmsHash
        if (hash == null || hash in existingSmsHashes || hash in deletedHashes) {
            Log.d(tag, "SMS already processed or intentionally deleted (hash match). Skipping.")
            return Result.success()
        }

        // --- Travel mode routing ---
        val travelSettings = settingsRepository.getCurrentTravelModeSettings()
        val homeCurrency = settingsRepository.getHomeCurrency().first()
        val isTravelModeActive =
            travelSettings?.isEnabled == true &&
                Date().time in travelSettings.startDate..travelSettings.endDate

        val newTransactionId: Long? =
            if (isTravelModeActive &&
                travelSettings.tripType == TripType.INTERNATIONAL
            ) {
                when (potentialTxn.detectedCurrencyCode) {
                    travelSettings.currencyCode ->
                        saver.resolveAndSaveTransaction(potentialTxn, isForeign = true, travelSettings = travelSettings)
                    homeCurrency ->
                        saver.resolveAndSaveTransaction(potentialTxn, isForeign = false, travelSettings = travelSettings)
                    else -> {
                        NotificationHelper.showTravelModeSmsNotification(context, potentialTxn, travelSettings)
                        null
                    }
                }
            } else {
                saver.resolveAndSaveTransaction(potentialTxn, isForeign = false, travelSettings = travelSettings)
            }

        newTransactionId ?: return Result.success()

        // --- NEW: Recurring transaction auto-linking ---
        val recurringDao = db.recurringTransactionDao()
        val transactionQueryDao = db.transactionQueryDao()
        val transactionWriteDao = db.transactionWriteDao()
        val savedTxn = transactionQueryDao.getTransactionByIdSync(newTransactionId.toInt())

        if (savedTxn != null) {
            // --- NEW: Smart Transaction Merge Check ---
            val timeWindowStart = savedTxn.date - (3 * 60 * 60 * 1000L) // 3 hours ago
            val recentTxn =
                transactionQueryDao.findRecentTransactionForMerge(
                    merchant = savedTxn.description,
                    accountId = savedTxn.accountId,
                    transactionType = savedTxn.transactionType,
                    timeWindowStart = timeWindowStart,
                    newTxnId = savedTxn.id
                )

            if (recentTxn != null) {
                NotificationHelper.showMergeTransactionNotification(context, savedTxn, recentTxn)
            }

            val senderRule = recurringDao.getRuleBySmsSenderId(sender)
            if (senderRule != null) {
                // It's a variable bill match
                val isAnomaly = Math.abs(savedTxn.amount - senderRule.amount) > senderRule.amount * 0.3

                transactionWriteDao.updateRecurringRuleId(savedTxn.id, senderRule.id)
                recurringDao.updateLastRunDate(senderRule.id, savedTxn.date)

                if (isAnomaly) {
                    NotificationHelper.showVariableBillAnomalyNotification(context, senderRule, savedTxn.amount, senderRule.amount)
                }
            } else {
                // Check if pending drafts exist
                val pendingDrafts = transactionQueryDao.getPendingTransactionsSync()
                val match =
                    pendingDrafts.find {
                        it.description == savedTxn.description &&
                            Math.abs(it.amount - savedTxn.amount) < 1.0 &&
                            Math.abs(it.date - savedTxn.date) < 4 * 24 * 60 * 60 * 1000L
                    }

                if (match != null) {
                    // Auto-link fixed bill
                    transactionWriteDao.delete(match)
                    match.recurringRuleId?.let { ruleId ->
                        transactionWriteDao.updateRecurringRuleId(savedTxn.id, ruleId)
                        recurringDao.updateLastRunDate(ruleId, savedTxn.date)
                    }
                }
            }
        }

        // --- Notifications ---
        val canNotify =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        if (canNotify) {
            if (potentialTxn.needsReview) {
                val savedTxn =
                    Transaction(
                        id = newTransactionId.toInt(),
                        description = potentialTxn.merchantName ?: "Unknown Merchant",
                        // FIX: Use the raw pre-rename name for originalDescription.
                        originalDescription = potentialTxn.originalMerchantName ?: potentialTxn.merchantName,
                        amount = potentialTxn.amount,
                        date = potentialTxn.date,
                        // placeholder, not used in the notification
                        accountId = 0,
                        categoryId = potentialTxn.categoryId,
                        notes = "",
                        transactionType = TransactionType.fromStringOrNull(potentialTxn.transactionType) ?: TransactionType.EXPENSE,
                        sourceSmsId = potentialTxn.sourceSmsId,
                        sourceSmsHash = potentialTxn.sourceSmsHash,
                    )
                NotificationHelper.showSuspiciousAmountNotification(
                    context,
                    savedTxn,
                    potentialTxn.suspicionReason ?: "Amount flagged for review.",
                )
            } else if (settingsRepository.getAutoCaptureNotificationEnabled().first()) {
                val workRequest =
                    OneTimeWorkRequestBuilder<TransactionNotificationWorker>()
                        .setInputData(workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to newTransactionId.toInt()))
                        .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }

        Log.d(tag, "Transaction saved successfully. ID: $newTransactionId, Merchant: ${potentialTxn.merchantName}")
        return Result.success()
    }
}
