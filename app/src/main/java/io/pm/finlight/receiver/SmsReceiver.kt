// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/receiver/SmsReceiver.kt
// REASON: FEATURE - The receiver now implements the heuristic parsing engine.
// If the primary `SmsParser` fails, it fetches all learned `SmsParseTemplate`s
// from the database. It then uses a Levenshtein distance algorithm to find the
// most structurally similar template. If a high-confidence match is found, it
// applies the template's known data positions to extract the merchant and amount
// from the new SMS, enabling the app to parse previously unknown formats.
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
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.min

class SmsReceiver : BroadcastReceiver() {
    private val tag = "SmsReceiver"

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()
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

                        var potentialTxn = SmsParser.parse(
                            sms = smsMessage,
                            mappings = existingMappings,
                            customSmsRuleProvider = object : CustomSmsRuleProvider {
                                override suspend fun getAllRules(): List<CustomSmsRule> = db.customSmsRuleDao().getAllRules().first()
                            },
                            merchantRenameRuleProvider = object : MerchantRenameRuleProvider {
                                override suspend fun getAllRules(): List<MerchantRenameRule> = db.merchantRenameRuleDao().getAllRules().first()
                            },
                            ignoreRuleProvider = object : IgnoreRuleProvider {
                                override suspend fun getEnabledRules(): List<IgnoreRule> = db.ignoreRuleDao().getEnabledRules()
                            },
                            merchantCategoryMappingProvider = object : MerchantCategoryMappingProvider {
                                override suspend fun getCategoryIdForMerchant(merchantName: String): Int? = db.merchantCategoryMappingDao().getCategoryIdForMerchant(merchantName)
                            }
                        )

                        // --- NEW HEURISTIC FALLBACK LOGIC ---
                        if (potentialTxn == null) {
                            Log.d(tag, "Primary parser failed. Attempting heuristic match...")
                            val smsParseTemplateDao = db.smsParseTemplateDao()
                            val allTemplates = smsParseTemplateDao.getAllTemplates()

                            if (allTemplates.isNotEmpty()) {
                                val newSmsBody = smsMessage.body
                                var bestMatch: SmsParseTemplate? = null
                                var highestScore = 0.0

                                for (template in allTemplates) {
                                    val score = calculateSimilarity(newSmsBody, template.originalSmsBody)
                                    if (score > highestScore) {
                                        highestScore = score
                                        bestMatch = template
                                    }
                                }

                                val SIMILARITY_THRESHOLD = 0.85 // 85% similar
                                if (bestMatch != null && highestScore >= SIMILARITY_THRESHOLD) {
                                    Log.d(tag, "Found heuristic match with score $highestScore. Template ID: ${bestMatch.id}")
                                    potentialTxn = applyTemplate(newSmsBody, bestMatch, smsMessage)
                                }
                            }
                        }


                        if (potentialTxn != null && !existingSmsHashes.contains(potentialTxn.sourceSmsHash)) {

                            val travelSettings = settingsRepository.getTravelModeSettings().first()
                            val homeCurrency = settingsRepository.getHomeCurrency().first()
                            val isTravelModeActive = travelSettings?.isEnabled == true &&
                                    Date().time in travelSettings.startDate..travelSettings.endDate

                            if (isTravelModeActive && travelSettings != null) {
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
        val settingsRepository = SettingsRepository(context)

        val accountName = potentialTxn.potentialAccount?.formattedName ?: "Unknown Account"
        val accountType = potentialTxn.potentialAccount?.accountType ?: "General"

        var account = accountDao.findByName(accountName)
        if (account == null) {
            val newAccount = Account(name = accountName, type = accountType)
            accountDao.insert(newAccount)
            account = accountDao.findByName(accountName)
        }

        if (account != null) {
            val transactionToSave = if (isForeign && travelSettings != null) {
                Transaction(
                    description = potentialTxn.merchantName ?: "Unknown Merchant",
                    originalDescription = potentialTxn.merchantName,
                    amount = potentialTxn.amount * travelSettings.conversionRate,
                    originalAmount = potentialTxn.amount,
                    currencyCode = travelSettings.currencyCode,
                    conversionRate = travelSettings.conversionRate.toDouble(),
                    date = System.currentTimeMillis(),
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
                    date = System.currentTimeMillis(),
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

            val newTransactionId = transactionDao.insert(transactionToSave)

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

    // --- NEW HELPER FUNCTIONS FOR HEURISTIC PARSING ---

    private fun applyTemplate(newSmsBody: String, template: SmsParseTemplate, originalSms: SmsMessage): PotentialTransaction? {
        try {
            // Extract merchant and amount based on stored indices, with bounds checking
            val merchant = newSmsBody.substring(
                template.originalMerchantStartIndex,
                min(template.originalMerchantEndIndex, newSmsBody.length)
            )
            val amountStr = newSmsBody.substring(
                template.originalAmountStartIndex,
                min(template.originalAmountEndIndex, newSmsBody.length)
            )
            val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null

            val transactionType = when {
                SmsParser.EXPENSE_KEYWORDS_REGEX.containsMatchIn(template.originalSmsBody) -> "expense"
                SmsParser.INCOME_KEYWORDS_REGEX.containsMatchIn(template.originalSmsBody) -> "income"
                else -> return null
            }

            val potentialAccount = SmsParser.parseAccount(newSmsBody, originalSms.sender)
            val smsHash = (originalSms.sender.filter { it.isDigit() }.takeLast(10) + newSmsBody).hashCode().toString()
            val smsSignature = SmsParser.generateSmsSignature(newSmsBody)

            return PotentialTransaction(
                sourceSmsId = originalSms.id,
                smsSender = originalSms.sender,
                amount = amount,
                transactionType = transactionType,
                merchantName = merchant,
                originalMessage = newSmsBody,
                potentialAccount = potentialAccount,
                sourceSmsHash = smsHash,
                smsSignature = smsSignature,
                date = originalSms.date
            )
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error applying heuristic template", e)
            return null
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        if (longer.isEmpty()) return 1.0
        val distance = levenshteinDistance(longer, shorter)
        return (longer.length - distance) / longer.length.toDouble()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (s1[i - 1] != s2[j - 1]) {
                            newValue = min(min(newValue, lastValue), costs[j]) + 1
                        }
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) {
                costs[s2.length] = lastValue
            }
        }
        return costs[s2.length]
    }
}
