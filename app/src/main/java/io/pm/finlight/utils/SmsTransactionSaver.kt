// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/SmsTransactionSaver.kt
// REASON: REFACTOR + BUG FIX — Extracts the duplicated account-resolution and
// transaction-saving logic from SmsReceiver and TransactionViewModel into a
// single, shared helper. This ensures the critical Bug #1 fix (IGNORE conflict
// → findByName fallback) is applied in one place for all callers:
// SmsProcessorWorker, SmsCatchupWorker, and autoSaveSmsTransaction.
// =================================================================================
package io.pm.finlight.utils

import android.content.Context
import android.util.Log
import io.pm.finlight.Account
import io.pm.finlight.AccountRepository
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.SmsParser
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TransactionType
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.DetectSelfTransferUseCase
import io.pm.finlight.domain.usecase.ResolveTravelModeTagUseCase

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
    private val accountRepository: IAccountRepository,
    private val transactionRepository: ITransactionRepository,
    private val resolveTravelModeTagUseCase: ResolveTravelModeTagUseCase,
    private val detectSelfTransferUseCase: DetectSelfTransferUseCase,
) {
    constructor(
        context: Context,
        resolveTravelModeTagUseCase: ResolveTravelModeTagUseCase,
    ) : this(
        accountRepository = ServiceLocator.provideAccountRepository(context),
        transactionRepository = ServiceLocator.provideTransactionRepository(context),
        resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
        detectSelfTransferUseCase = DetectSelfTransferUseCase(context),
    )

    @Deprecated("Temporary transition constructor for WP-08b -> WP-08")
    constructor(
        context: Context,
        resolveTravelModeTagUseCase: ResolveTravelModeTagUseCase,
        db: AppDatabase,
    ) : this(
        accountRepository = ServiceLocator.provideAccountRepository(context),
        transactionRepository = ServiceLocator.provideTransactionRepository(context),
        resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
        detectSelfTransferUseCase = DetectSelfTransferUseCase(context),
    )

    @Deprecated("Temporary transition constructor for test stability")
    constructor(
        db: AppDatabase,
        resolveTravelModeTagUseCase: ResolveTravelModeTagUseCase,
        detectSelfTransferUseCase: DetectSelfTransferUseCase = DetectSelfTransferUseCase(db),
    ) : this(
        accountRepository =
            AccountRepository(
                accountDao = db.accountDao(),
                accountAliasDao = db.accountAliasDao(),
                db = db,
            ),
        transactionRepository =
            TransactionRepository(
                transactionWriteDao = db.transactionWriteDao(),
                transactionQueryDao = db.transactionQueryDao(),
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
                transactionReimbursementDao = db.transactionReimbursementDao(),
                db = db,
            ),
        resolveTravelModeTagUseCase = resolveTravelModeTagUseCase,
        detectSelfTransferUseCase = detectSelfTransferUseCase,
    )

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
        if (potentialTxn.amount <= 0.0 || potentialTxn.amount.isNaN() || potentialTxn.amount.isInfinite()) {
            Log.e(tag, "Invalid transaction amount: ${potentialTxn.amount}. Transaction dropped.")
            return null
        }

        val accountName = SmsParser.sanitizeAccountName(potentialTxn.potentialAccount?.formattedName)
        val accountType = potentialTxn.potentialAccount?.accountType ?: "General"

        // --- Account Resolution (with Bug #1 fix) ---
        val finalAccountId: Int? =
            run {
                val alias = accountRepository.findAliasByName(accountName)
                if (alias != null) {
                    Log.d(tag, "Found alias for '$accountName' → account ID ${alias.destinationAccountId}")
                    alias.destinationAccountId
                } else {
                    var account = accountRepository.findByName(accountName)
                    if (account == null) {
                        val newAccount = Account(name = accountName, type = accountType)
                        val newId = accountRepository.insert(newAccount)
                        // BUG FIX: OnConflictStrategy.IGNORE returns -1 if the account already
                        // exists (e.g., created by a concurrent operation). In that case, query
                        // by name to get the already-existing account instead of passing -1 to
                        // getAccountById which would always return null.
                        account =
                            if (newId != -1L) {
                                accountRepository.getAccountByIdSync(newId.toInt())
                            } else {
                                Log.d(tag, "Account '$accountName' already existed (IGNORE conflict). Fetching by name.")
                                accountRepository.findByName(accountName)
                            }
                    }
                    account?.id
                }
            }

        if (finalAccountId == null) {
            Log.e(tag, "Failed to resolve or create account for '$accountName'. Transaction dropped.")
            return null
        }

        val rawRate = travelSettings?.conversionRate?.toDouble()
        val conversionRate =
            if (rawRate != null && rawRate > 0.0 && !rawRate.isNaN() && !rawRate.isInfinite()) {
                rawRate
            } else {
                1.0
            }
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
                    transactionType = TransactionType.fromStringOrNull(potentialTxn.transactionType) ?: TransactionType.EXPENSE,
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
                    transactionType = TransactionType.fromStringOrNull(potentialTxn.transactionType) ?: TransactionType.EXPENSE,
                    sourceSmsId = potentialTxn.sourceSmsId,
                    sourceSmsHash = potentialTxn.sourceSmsHash,
                    source = source,
                    smsSignature = potentialTxn.smsSignature,
                    needsReview = potentialTxn.needsReview,
                )
            }

        // --- Pre-save duplicate guard ---
        potentialTxn.sourceSmsHash?.let { hash ->
            if (transactionRepository.existsBySmsHash(hash)) {
                Log.d(tag, "Transaction with sourceSmsHash '$hash' already exists. Dropping duplicate save.")
                return null
            }
            val legacyHash = SmsParser.computeLegacySmsHash(potentialTxn.smsSender, potentialTxn.originalMessage)
            if (transactionRepository.existsBySmsHash(legacyHash)) {
                Log.d(tag, "Transaction with legacy sourceSmsHash '$legacyHash' already exists. Upgrading to new hash and dropping duplicate save.")
                transactionRepository.updateSmsHashByLegacy(oldHash = legacyHash, newHash = hash)
                return null
            }
            if (transactionRepository.existsDeletedSmsHash(hash) || transactionRepository.existsDeletedSmsHash(legacyHash)) {
                Log.d(tag, "Transaction with sourceSmsHash '$hash' was deleted by user. Dropping duplicate save.")
                if (transactionRepository.existsDeletedSmsHash(legacyHash)) {
                    transactionRepository.insertDeletedSmsHash(hash)
                }
                return null
            }
        }

        val finalTags = resolveTravelModeTagUseCase.getFinalTags(potentialTxn.date, emptySet(), travelSettings)
        val newId = transactionRepository.insertTransactionWithTags(transactionToSave, finalTags)
        if (newId <= 0L) {
            Log.d(tag, "Transaction insert ignored due to unique constraint conflict on sourceSmsHash.")
            return null
        }

        // --- Attempt to detect and link self-transfers ---
        val savedTransaction = transactionToSave.copy(id = newId.toInt())
        detectSelfTransferUseCase(savedTransaction)

        return newId
    }
}
