// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/receiver/SmsReceiver.kt
// REASON: FIX - Resolved build error by correctly instantiating and using the
// TransactionRepository to save transactions with tags, ensuring the auto-tagging
// logic for travel mode works correctly for auto-captured SMS.
// REFACTOR - The parsing logic has been updated to follow a strict hierarchy of
// trust: 1) User-defined custom rules, 2) ML model pre-filter, 3) Main parser
// (heuristics then generic regex). This ensures a user's custom rule always
// overrides the ML model, fixing the "escape hatch" functionality.
// =================================================================================
package io.pm.finlight

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

class SmsReceiver : BroadcastReceiver() {
    private val tag = "SmsReceiver"
    private lateinit var smsClassifier: SmsClassifier

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()

            if (!::smsClassifier.isInitialized) {
                smsClassifier = SmsClassifier(context)
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    val messagesBySender = messages.groupBy { it.originatingAddress }

                    for ((sender, parts) in messagesBySender) {
                        if (sender == null) continue

                        val fullBody = parts.joinToString("") { it.messageBody }
                        val smsId = parts.first().timestampMillis

                        val db = AppDatabase.getInstance(context)
                        val settingsRepository = SettingsRepository(context)
                        val transactionDao = db.transactionDao()
                        val mappingRepository = MerchantMappingRepository(db.merchantMappingDao())

                        val existingMappings = mappingRepository.allMappings.first().associateBy({ it.smsSender }, { it.merchantName })
                        val existingSmsHashes = transactionDao.getAllSmsHashes().first().toSet()
                        val smsMessage = SmsMessage(id = smsId, sender = sender, body = fullBody, date = smsId)

                        // --- Instantiate all providers needed for the parsing stages ---
                        val categoryFinderProvider = object : CategoryFinderProvider {
                            override fun getCategoryIdByName(name: String): Int? = CategoryIconHelper.getCategoryIdByName(name)
                        }
                        val customSmsRuleProvider = object : CustomSmsRuleProvider {
                            override suspend fun getAllRules(): List<CustomSmsRule> = db.customSmsRuleDao().getAllRules().first()
                        }
                        val merchantRenameRuleProvider = object : MerchantRenameRuleProvider {
                            override suspend fun getAllRules(): List<MerchantRenameRule> = db.merchantRenameRuleDao().getAllRules().first()
                        }
                        val ignoreRuleProvider = object : IgnoreRuleProvider {
                            override suspend fun getEnabledRules(): List<IgnoreRule> = db.ignoreRuleDao().getEnabledRules()
                        }
                        val merchantCategoryMappingProvider = object : MerchantCategoryMappingProvider {
                            override suspend fun getCategoryIdForMerchant(merchantName: String): Int? = db.merchantCategoryMappingDao().getCategoryIdForMerchant(merchantName)
                        }
                        val smsParseTemplateProvider = object : SmsParseTemplateProvider {
                            override suspend fun getAllTemplates(): List<SmsParseTemplate> = db.smsParseTemplateDao().getAllTemplates()
                        }

                        // --- HIERARCHY STEP 1: Check for User-Defined Custom Rules First ---
                        var parseResult = SmsParser.parseWithOnlyCustomRules(
                            sms = smsMessage,
                            customSmsRuleProvider = customSmsRuleProvider,
                            merchantRenameRuleProvider = merchantRenameRuleProvider,
                            merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                            categoryFinderProvider = categoryFinderProvider
                        )

                        // --- HIERARCHY STEP 2: If no custom rule matched, run ML pre-filter ---
                        if (parseResult == null) {
                            val transactionConfidence = smsClassifier.classify(fullBody)
                            if (transactionConfidence < 0.1) {
                                Log.d(tag, "ML model ignored SMS with confidence: ${1 - transactionConfidence}. Body: $fullBody")
                                continue // Skip to the next message
                            }

                            // --- HIERARCHY STEP 3: Run the main parser (Heuristics -> Generic) ---
                            parseResult = SmsParser.parseWithReason(
                                sms = smsMessage,
                                mappings = existingMappings,
                                customSmsRuleProvider = customSmsRuleProvider,
                                merchantRenameRuleProvider = merchantRenameRuleProvider,
                                ignoreRuleProvider = ignoreRuleProvider,
                                merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                                categoryFinderProvider = categoryFinderProvider,
                                smsParseTemplateProvider = smsParseTemplateProvider
                            )
                        }


                        // --- FINAL STEP: Process the result from whichever step succeeded ---
                        if (parseResult is ParseResult.Success) {
                            val potentialTxn = parseResult.transaction
                            if (potentialTxn.sourceSmsHash != null && !existingSmsHashes.contains(potentialTxn.sourceSmsHash)) {
                                val travelSettings = settingsRepository.getTravelModeSettings().first()
                                val homeCurrency = settingsRepository.getHomeCurrency().first()
                                val isTravelModeActive = travelSettings?.isEnabled == true &&
                                        Date().time in travelSettings.startDate..travelSettings.endDate

                                if (isTravelModeActive && travelSettings != null && travelSettings.tripType == TripType.INTERNATIONAL) {
                                    when (potentialTxn.detectedCurrencyCode) {
                                        travelSettings.currencyCode -> {
                                            saveTransaction(context, potentialTxn, isForeign = true, travelSettings = travelSettings)
                                        }
                                        homeCurrency -> {
                                            saveTransaction(context, potentialTxn, isForeign = false, travelSettings = null)
                                        }
                                        else -> {
                                            NotificationHelper.showTravelModeSmsNotification(context, potentialTxn, travelSettings)
                                        }
                                    }
                                } else {
                                    saveTransaction(context, potentialTxn, isForeign = false, travelSettings = null)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing SMS", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun saveTransaction(
        context: Context,
        potentialTxn: PotentialTransaction,
        isForeign: Boolean,
        travelSettings: TravelModeSettings?
    ) {
        val db = AppDatabase.getInstance(context)
        val accountDao = db.accountDao()
        val transactionDao = db.transactionDao()
        val tagRepository = TagRepository(db.tagDao(), transactionDao)
        val settingsRepository = SettingsRepository(context)
        val transactionRepository = TransactionRepository(transactionDao)

        val accountName = potentialTxn.potentialAccount?.formattedName ?: "Unknown Account"
        val accountType = potentialTxn.potentialAccount?.accountType ?: "General"

        var account = accountDao.findByName(accountName)
        if (account == null) {
            val newAccount = Account(name = accountName, type = accountType)
            accountDao.insert(newAccount)
            account = accountDao.findByName(accountName)
        }

        if (account != null) {
            val currentTravelSettings = settingsRepository.getTravelModeSettings().first()
            val isTravelModeActive = currentTravelSettings?.isEnabled == true &&
                    potentialTxn.date >= currentTravelSettings.startDate &&
                    potentialTxn.date <= currentTravelSettings.endDate

            val tagsToSave = mutableSetOf<Tag>()
            if (isTravelModeActive) {
                val tripTag = tagRepository.findOrCreateTag(currentTravelSettings!!.tripName)
                tagsToSave.add(tripTag)
            }

            val conversionRate = travelSettings?.conversionRate?.toDouble() ?: 1.0
            val transactionToSave = if (isForeign && travelSettings != null) {
                Transaction(
                    description = potentialTxn.merchantName ?: "Unknown Merchant",
                    originalDescription = potentialTxn.merchantName,
                    amount = potentialTxn.amount * conversionRate,
                    originalAmount = potentialTxn.amount,
                    currencyCode = travelSettings.currencyCode,
                    conversionRate = conversionRate,
                    date = potentialTxn.date,
                    accountId = account.id,
                    categoryId = potentialTxn.categoryId,
                    notes = "",
                    transactionType = potentialTxn.transactionType,
                    sourceSmsId = potentialTxn.sourceSmsId,
                    sourceSmsHash = potentialTxn.sourceSmsHash,
                    source = "Auto-Captured",
                    smsSignature = potentialTxn.smsSignature
                )
            } else {
                Transaction(
                    description = potentialTxn.merchantName ?: "Unknown Merchant",
                    originalDescription = potentialTxn.merchantName,
                    amount = potentialTxn.amount,
                    date = potentialTxn.date,
                    accountId = account.id,
                    categoryId = potentialTxn.categoryId,
                    notes = "",
                    transactionType = potentialTxn.transactionType,
                    sourceSmsId = potentialTxn.sourceSmsId,
                    sourceSmsHash = potentialTxn.sourceSmsHash,
                    source = "Auto-Captured",
                    smsSignature = potentialTxn.smsSignature
                )
            }

            val newTransactionId = transactionRepository.insertTransactionWithTags(transactionToSave, tagsToSave)

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
                settingsRepository.isAutoCaptureNotificationEnabledBlocking()) {
                val workRequest = OneTimeWorkRequestBuilder<TransactionNotificationWorker>()
                    .setInputData(workDataOf(TransactionNotificationWorker.KEY_TRANSACTION_ID to newTransactionId.toInt()))
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        } else {
            Log.e(tag, "Failed to find or create an account for the transaction.")
        }
    }
}