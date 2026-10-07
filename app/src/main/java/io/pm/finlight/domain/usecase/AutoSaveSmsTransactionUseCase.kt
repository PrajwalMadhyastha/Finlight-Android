package io.pm.finlight.domain.usecase

import android.content.Context
import android.util.Log
import io.pm.finlight.IAccountRepository
import io.pm.finlight.IMerchantMappingRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.SmsParser
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Domain UseCase that encapsulates SMS transaction deduplication, account resolution,
 * merchant fallback mapping, and transaction auto-persistence.
 *
 * Extracted from TransactionViewModel to allow ingestion services, background workers,
 * and ViewModels to auto-save transactions cleanly without depending on presentation state.
 */
class AutoSaveSmsTransactionUseCase(
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val smsRepository: ISmsRepository,
    private val merchantMappingRepository: IMerchantMappingRepository,
    private val db: AppDatabase? = null,
    private val deletedSmsHashDao: DeletedSmsHashDao? = db?.deletedSmsHashDao(),
    private val resolveTravelModeTagUseCase: ResolveTravelModeTagUseCase? = null,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) {
    companion object {
        private const val TAG = "AutoSaveSmsTxnUseCase"
    }

    constructor(
        context: Context,
        resolvedDb: AppDatabase? = null,
    ) : this(
        transactionRepository = ServiceLocator.provideTransactionRepository(context),
        accountRepository = ServiceLocator.provideAccountRepository(context),
        smsRepository = ServiceLocator.provideSmsRepository(context),
        merchantMappingRepository = ServiceLocator.provideMerchantMappingRepository(context, resolvedDb),
        db = resolvedDb ?: AppDatabase.getInstance(context),
        deletedSmsHashDao = (resolvedDb ?: AppDatabase.getInstance(context)).deletedSmsHashDao(),
        resolveTravelModeTagUseCase = ResolveTravelModeTagUseCase(ServiceLocator.provideTagRepository(context)),
        dispatcherProvider = ServiceLocator.provideDispatcherProvider(context),
    )

    constructor(
        transactionRepository: ITransactionRepository,
        accountRepository: IAccountRepository,
        smsRepository: ISmsRepository,
        merchantMappingRepository: IMerchantMappingRepository,
    ) : this(
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        smsRepository = smsRepository,
        merchantMappingRepository = merchantMappingRepository,
        db = null,
        deletedSmsHashDao = null,
        resolveTravelModeTagUseCase = null,
        dispatcherProvider = DefaultDispatcherProvider(),
    )

    /**
     * Executes the auto-save pipeline for a parsed [PotentialTransaction].
     *
     * @param potentialTxn The candidate transaction parsed from an SMS.
     * @param source The origin label to stamp on the transaction (e.g., "Auto-Captured", "Imported").
     * @return True if the transaction was successfully saved, false if dropped or deduplicated.
     */
    suspend operator fun invoke(
        potentialTxn: PotentialTransaction,
        source: String = "Auto-Captured",
    ): Boolean {
        return withContext(dispatcherProvider.io) {
            try {
                if (potentialTxn.amount <= 0.0 || potentialTxn.amount.isNaN() || potentialTxn.amount.isInfinite()) {
                    Log.e(TAG, "Invalid transaction amount: ${potentialTxn.amount}. Auto-save dropped.")
                    return@withContext false
                }

                if (isDuplicateOrDeleted(potentialTxn)) {
                    return@withContext false
                }

                val accountName = SmsParser.sanitizeAccountName(potentialTxn.potentialAccount?.formattedName)
                val accountType = potentialTxn.potentialAccount?.accountType ?: "General"

                val account =
                    try {
                        accountRepository.findOrCreateByName(accountName, accountType)
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto-save failed: Could not find or create account '$accountName'", e)
                        return@withContext false
                    }

                val finalAccountId = account.id

                val rawMerchant = potentialTxn.merchantName?.trim()
                val merchantName =
                    if (!rawMerchant.isNullOrBlank() && !rawMerchant.equals("Unknown Merchant", ignoreCase = true)) {
                        rawMerchant
                    } else {
                        // Merchant mapping fallback
                        val senderMapping =
                            try {
                                merchantMappingRepository.allMappings.first().firstOrNull {
                                    it.smsSender.equals(potentialTxn.smsSender, ignoreCase = true)
                                }?.merchantName?.trim()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to resolve merchant mapping for ${potentialTxn.smsSender}", e)
                                null
                            }
                        senderMapping?.takeIf { it.isNotBlank() } ?: rawMerchant?.takeIf { it.isNotBlank() } ?: "Unknown Merchant"
                    }

                val originalDescription =
                    potentialTxn.originalMerchantName?.takeIf { it.isNotBlank() }
                        ?: potentialTxn.merchantName?.takeIf { it.isNotBlank() }
                        ?: merchantName

                val transactionToSave =
                    Transaction(
                        description = merchantName,
                        originalDescription = originalDescription,
                        categoryId = potentialTxn.categoryId,
                        amount = potentialTxn.amount,
                        date = potentialTxn.date,
                        accountId = finalAccountId,
                        notes = null,
                        transactionType =
                            TransactionType.fromStringOrNull(potentialTxn.transactionType)
                                ?: TransactionType.fromString(potentialTxn.transactionType),
                        sourceSmsId = potentialTxn.sourceSmsId,
                        sourceSmsHash = potentialTxn.sourceSmsHash,
                        source = source,
                    )

                val finalTags = resolveTravelModeTagUseCase?.getFinalTags(transactionToSave.date, emptySet(), null) ?: emptySet()
                val newId = transactionRepository.insertTransactionWithTags(transactionToSave, finalTags)
                if (newId <= 0L) {
                    Log.d(TAG, "Auto-save ignored or failed due to conflict (newId: $newId).")
                    return@withContext false
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-save SMS transaction", e)
                false
            }
        }
    }

    private suspend fun isDuplicateOrDeleted(potentialTxn: PotentialTransaction): Boolean {
        val hash = potentialTxn.sourceSmsHash ?: return false

        // 1. Check existing transaction by current hash
        if (transactionRepository.existsBySmsHash(hash)) {
            Log.d(TAG, "Transaction with sourceSmsHash '$hash' already exists. Skipping auto-save.")
            return true
        }

        // 2. Check existing transaction by legacy hash
        val legacyHash = SmsParser.computeLegacySmsHash(potentialTxn.smsSender, potentialTxn.originalMessage)
        if (legacyHash.isNotBlank() && transactionRepository.existsBySmsHash(legacyHash)) {
            Log.d(TAG, "Transaction with legacy sourceSmsHash '$legacyHash' already exists. Upgrading to '$hash' and skipping auto-save.")
            transactionRepository.updateSmsHashByLegacy(oldHash = legacyHash, newHash = hash)
            return true
        }

        // 3. Check deny-list (deleted transactions)
        val activeDeletedDao = deletedSmsHashDao ?: db?.deletedSmsHashDao()
        if (activeDeletedDao != null) {
            val isCurrentDeleted = activeDeletedDao.existsByHash(hash)
            val isLegacyDeleted = legacyHash.isNotBlank() && activeDeletedDao.existsByHash(legacyHash)
            if (isCurrentDeleted || isLegacyDeleted) {
                Log.d(TAG, "Transaction with sourceSmsHash was deleted by user. Skipping auto-save.")
                if (isLegacyDeleted) {
                    activeDeletedDao.insert(DeletedSmsHash(hash))
                }
                return true
            }
        }

        return false
    }
}
