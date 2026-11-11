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

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.ParseResult
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.TripType
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.SmsProviderHelper
import io.pm.finlight.utils.SmsTransactionSaver
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

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
        if (runAttemptCount >= 3) {
            Log.w(tag, "SmsProcessorWorker exhausted 3 attempts. Failing permanently.")
            return Result.failure()
        }

        return try {
            val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
            val body = inputData.getString(KEY_BODY) ?: return Result.failure()
            val date = inputData.getLong(KEY_DATE, -1L).takeIf { it != -1L } ?: return Result.failure()

            val smsMessage = SmsMessage(id = date, sender = sender, body = body, date = date)

            val settingsRepository = ServiceLocator.provideSettingsRepository(context)
            val tagRepository = ServiceLocator.provideTagRepository(context)
            val resolveTravelModeTagUseCase = ResolveTravelModeTagUseCase(tagRepository)
            val saver = SmsTransactionSaver(context, resolveTravelModeTagUseCase)

            val transactionRepository = ServiceLocator.provideTransactionRepository(context)
            val recurringTransactionRepository = ServiceLocator.provideRecurringTransactionRepository(context)
            val merchantRenameRuleRepository = ServiceLocator.provideMerchantRenameRuleRepository(context)
            val merchantCategoryMappingRepository = ServiceLocator.provideMerchantCategoryMappingRepository(context)
            val customSmsRuleRepository = ServiceLocator.provideCustomSmsRuleRepository(context)
            val ignoreRuleRepository = ServiceLocator.provideIgnoreRuleRepository(context)
            val smsParseTemplateRepository = ServiceLocator.provideSmsParseTemplateRepository(context)
            val merchantMappingRepository = ServiceLocator.provideMerchantMappingRepository(context)

            val existingMappings = merchantMappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })

            // --- Build providers ---
            val categoryFinderProvider = SmsProviderHelper.getCategoryFinderProvider()
            val customSmsRuleProvider = SmsProviderHelper.getCustomSmsRuleProvider(customSmsRuleRepository)
            val merchantRenameRuleProvider = SmsProviderHelper.getMerchantRenameRuleProvider(merchantRenameRuleRepository)
            val ignoreRuleProvider = SmsProviderHelper.getIgnoreRuleProvider(ignoreRuleRepository)
            val merchantCategoryMappingProvider = SmsProviderHelper.getMerchantCategoryMappingProvider(merchantCategoryMappingRepository)
            val smsParseTemplateProvider = SmsProviderHelper.getSmsParseTemplateProvider(smsParseTemplateRepository)

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
                val confidence =
                    MlModelFactory.getClassifier(context).use { classifier ->
                        classifier.classify(body)
                    }

                if (confidence < 0.1) {
                    Log.d(tag, "ML model ignored SMS (confidence=${1 - confidence}). Sender: $sender")
                    return Result.success()
                }

                // --- HIERARCHY STEP 3: NER + main parser ---
                val nerEntities =
                    MlModelFactory.getNerExtractor(context).use { nerExtractor ->
                        nerExtractor.extract(body)
                    }

                Log.d(tag, "NER extraction complete. Entity types found: ${nerEntities.keys}")

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
                merchantRenameRuleRepository.insert(MerchantRenameRule(oldName, newName))
            }
            parseResult.newlyDiscoveredCategoryAlias?.let { (merchant, catId) ->
                Log.d(tag, "Auto-healing category rule: $merchant → $catId")
                merchantCategoryMappingRepository.insert(MerchantCategoryMapping(merchant, catId))
            }

            val potentialTxn = parseResult.transaction

            // --- Duplicate guard ---
            val hash = potentialTxn.sourceSmsHash
            val legacyHash = SmsParser.computeLegacySmsHash(sender, body)
            val isDuplicate =
                (hash != null && (transactionRepository.existsBySmsHash(hash) || transactionRepository.existsDeletedSmsHash(hash))) ||
                    (legacyHash.isNotBlank() && (transactionRepository.existsBySmsHash(legacyHash) || transactionRepository.existsDeletedSmsHash(legacyHash)))

            if (isDuplicate) {
                Log.d(tag, "SMS already processed or intentionally deleted (hash match). Skipping.")
                if (hash != null && legacyHash.isNotBlank()) {
                    if (transactionRepository.existsBySmsHash(legacyHash)) {
                        transactionRepository.updateSmsHashByLegacy(oldHash = legacyHash, newHash = hash)
                    }
                    if (transactionRepository.existsDeletedSmsHash(legacyHash)) {
                        transactionRepository.insertDeletedSmsHash(hash)
                    }
                }
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

            // --- Recurring transaction auto-linking ---
            val savedTxn = transactionRepository.getTransactionSync(newTransactionId.toInt())

            if (savedTxn != null) {
                // --- Smart Transaction Merge Check ---
                val timeWindowStart = savedTxn.date - (3 * 60 * 60 * 1000L) // 3 hours ago
                val recentTxn =
                    transactionRepository.findRecentTransactionForMerge(
                        merchant = savedTxn.description,
                        accountId = savedTxn.accountId,
                        transactionType = savedTxn.transactionType,
                        timeWindowStart = timeWindowStart,
                        newTxnId = savedTxn.id,
                    )

                if (recentTxn != null) {
                    NotificationHelper.showMergeTransactionNotification(context, savedTxn, recentTxn)
                }

                val senderRule = recurringTransactionRepository.getRuleBySmsSenderId(sender)
                if (senderRule != null) {
                    // It's a variable bill match
                    val isAnomaly = abs(savedTxn.amount - senderRule.amount) > senderRule.amount * 0.3

                    transactionRepository.updateRecurringRuleId(savedTxn.id, senderRule.id)
                    recurringTransactionRepository.updateLastRunDate(senderRule.id, savedTxn.date)

                    if (isAnomaly) {
                        NotificationHelper.showVariableBillAnomalyNotification(context, senderRule, savedTxn.amount, senderRule.amount)
                    }
                } else {
                    // Check if pending drafts exist
                    val pendingDrafts = transactionRepository.getPendingTransactions()
                    val match =
                        pendingDrafts.find {
                            it.description == savedTxn.description &&
                                abs(it.amount - savedTxn.amount) < 1.0 &&
                                abs(it.date - savedTxn.date) < 4 * 24 * 60 * 60 * 1000L
                        }

                    if (match != null) {
                        // Auto-link fixed bill
                        transactionRepository.delete(match)
                        match.recurringRuleId?.let { ruleId ->
                            transactionRepository.updateRecurringRuleId(savedTxn.id, ruleId)
                            recurringTransactionRepository.updateLastRunDate(ruleId, savedTxn.date)
                        }
                    }
                }
            }

            // --- Notifications ---
            val canNotify = NotificationHelper.canPostNotifications(context)

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
                    val workScheduler = ServiceLocator.provideWorkScheduler(context)
                    workScheduler.scheduleTransactionNotification(newTransactionId)
                }
            }

            Log.d(tag, "Transaction saved successfully. ID: $newTransactionId, Merchant: ${potentialTxn.merchantName}")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (oom: OutOfMemoryError) {
            Log.e(tag, "SmsProcessorWorker encountered OOM on attempt $runAttemptCount. Failing permanently.", oom)
            Result.failure()
        } catch (e: Exception) {
            Log.e(tag, "SmsProcessorWorker failed (attempt $runAttemptCount): ${e.javaClass.simpleName}", e)
            if (runAttemptCount >= 2) Result.failure() else Result.retry()
        }
    }
}
