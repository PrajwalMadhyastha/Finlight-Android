// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/SmsTransactionSaver.kt
// REASON: REFACTOR + BUG FIX — Extracts the duplicated account-resolution and
// transaction-saving logic from SmsReceiver and TransactionViewModel into a
// single, shared helper. This ensures the critical Bug #1 fix (IGNORE conflict
// → findByName fallback) is applied in one place for all callers:
// SmsProcessorWorker, SmsCatchupWorker, and autoSaveSmsTransaction.
// =================================================================================
package io.pm.finlight.utils

import android.util.Log
import io.pm.finlight.Account
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.data.db.AppDatabase

/**
 * A shared utility class that handles the full account-resolution and transaction
 * persistence pipeline for SMS-originated transactions.
 *
 * This is the single source of truth for saving parsed SMS transactions.
 * All callers (SmsProcessorWorker, SmsCatchupWorker, autoSaveSmsTransaction)
 * use this class so that bug fixes and logic changes propagate everywhere.
 *
 * Key fix: AccountDao.insert uses OnConflictStrategy.IGNORE, which returns -1
 * if the account already exists. The previous code passed -1 to getAccountById,
 * which always returned null, silently dropping the transaction. This class
 * correctly falls back to findByName() in that case.
 */
class SmsTransactionSaver(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {
    private val tag = "SmsTransactionSaver"

    /**
     * Resolves or creates the account for a parsed SMS transaction and persists it.
     *
     * @param potentialTxn The parsed transaction data.
     * @param isForeign True if this is a foreign-currency transaction under travel mode.
     * @param travelSettings Active travel mode settings, used for currency conversion.
     * @param source The source label to stamp on the transaction record.
     * @return The new transaction's database row ID, or null if the save failed.
     */
    suspend fun resolveAndSaveTransaction(
        potentialTxn: PotentialTransaction,
        isForeign: Boolean = false,
        travelSettings: TravelModeSettings? = null,
        source: String = "Auto-Captured",
    ): Long? {
        val accountDao = db.accountDao()
        val accountAliasDao = db.accountAliasDao()
        val transactionDao = db.transactionDao()
        val tagRepository = TagRepository(db.tagDao(), transactionDao)
        val transactionRepository = TransactionRepository(transactionDao, settingsRepository, tagRepository, db.deletedSmsHashDao(), db.mergeRecordDao(), db)

        val accountName = potentialTxn.potentialAccount?.formattedName ?: "Unknown Account"
        val accountType = potentialTxn.potentialAccount?.accountType ?: "General"

        // --- Account Resolution (with Bug #1 fix) ---
        val finalAccountId: Int? =
            run {
                val alias = accountAliasDao.findByAlias(accountName)
                if (alias != null) {
                    Log.d(tag, "Found alias for '$accountName' → account ID ${alias.destinationAccountId}")
                    alias.destinationAccountId
                } else {
                    var account = accountDao.findByName(accountName)
                    if (account == null) {
                        val newAccount = Account(name = accountName, type = accountType)
                        val newId = accountDao.insert(newAccount)
                        // BUG FIX: OnConflictStrategy.IGNORE returns -1 if the account already
                        // exists (e.g., created by a concurrent operation). In that case, query
                        // by name to get the already-existing account instead of passing -1 to
                        // getAccountById which would always return null.
                        account =
                            if (newId != -1L) {
                                accountDao.getAccountByIdBlocking(newId.toInt())
                            } else {
                                Log.d(tag, "Account '$accountName' already existed (IGNORE conflict). Fetching by name.")
                                accountDao.findByName(accountName)
                            }
                    }
                    account?.id
                }
            }

        if (finalAccountId == null) {
            Log.e(tag, "Failed to resolve or create account for '$accountName'. Transaction dropped.")
            return null
        }

        val conversionRate = travelSettings?.conversionRate?.toDouble() ?: 1.0
        val transactionToSave =
            if (isForeign && travelSettings != null) {
                Transaction(
                    description = potentialTxn.merchantName ?: "Unknown Merchant",
                    // FIX: Use the raw pre-rename name for originalDescription.
                    // originalMerchantName holds the SMS name before any MerchantRenameRule
                    // was applied. If null, no rename happened so merchantName is already raw.
                    originalDescription = potentialTxn.originalMerchantName ?: potentialTxn.merchantName,
                    amount = potentialTxn.amount * conversionRate,
                    originalAmount = potentialTxn.amount,
                    currencyCode = travelSettings.currencyCode,
                    conversionRate = conversionRate,
                    date = potentialTxn.date,
                    accountId = finalAccountId,
                    categoryId = potentialTxn.categoryId,
                    notes = "",
                    transactionType = potentialTxn.transactionType,
                    sourceSmsId = potentialTxn.sourceSmsId,
                    sourceSmsHash = potentialTxn.sourceSmsHash,
                    source = source,
                    smsSignature = potentialTxn.smsSignature,
                    needsReview = potentialTxn.needsReview,
                )
            } else {
                Transaction(
                    description = potentialTxn.merchantName ?: "Unknown Merchant",
                    // FIX: Use the raw pre-rename name for originalDescription.
                    originalDescription = potentialTxn.originalMerchantName ?: potentialTxn.merchantName,
                    amount = potentialTxn.amount,
                    date = potentialTxn.date,
                    accountId = finalAccountId,
                    categoryId = potentialTxn.categoryId,
                    notes = "",
                    transactionType = potentialTxn.transactionType,
                    sourceSmsId = potentialTxn.sourceSmsId,
                    sourceSmsHash = potentialTxn.sourceSmsHash,
                    source = source,
                    smsSignature = potentialTxn.smsSignature,
                    needsReview = potentialTxn.needsReview,
                )
            }

        // The repository handles travel-mode tag injection automatically.
        val newId = transactionRepository.insertTransactionWithTags(transactionToSave, emptySet())

        // --- NEW: Attempt to detect and link self-transfers ---
        val savedTransaction = transactionToSave.copy(id = newId.toInt())
        transactionRepository.detectAndLinkSelfTransfer(savedTransaction)

        return newId
    }
}
