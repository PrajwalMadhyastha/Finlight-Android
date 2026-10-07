package io.pm.finlight.domain.usecase

import android.content.Context
import android.util.Log
import io.pm.finlight.IAccountRepository
import io.pm.finlight.IMerchantMappingRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.SmsParser
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Domain UseCase that encapsulates SMS transaction deduplication, account resolution,
 * merchant fallback mapping, travel mode tag resolution, and transaction auto-persistence.
 *
 * Extracted from TransactionViewModel to allow ingestion services, background workers,
 * and ViewModels to auto-save transactions cleanly without depending on presentation state.
 */
class AutoSaveSmsTransactionUseCase(
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val merchantMappingRepository: IMerchantMappingRepository,
    private val smsRepository: ISmsRepository? = null,
    private val travelSettingsRepository: ITravelSettingsRepository? = null,
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
        merchantMappingRepository = ServiceLocator.provideMerchantMappingRepository(context, resolvedDb),
        smsRepository = ServiceLocator.provideSmsRepository(context),
        travelSettingsRepository = ServiceLocator.provideTravelSettingsRepository(context),
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
        merchantMappingRepository = merchantMappingRepository,
        smsRepository = smsRepository,
        travelSettingsRepository = null,
        resolveTravelModeTagUseCase = null,
        dispatcherProvider = DefaultDispatcherProvider(),
    )

    /**
     * Executes the auto-save pipeline for a parsed [PotentialTransaction].
     *
     * @param potentialTxn The candidate transaction parsed from an SMS.
     * @param source The origin label to stamp on the transaction (e.g., "Auto-Captured", "Imported").
     * @param travelSettings Optional active travel settings. If null, resolved via [travelSettingsRepository].
     * @return True if the transaction was successfully saved, false if dropped or deduplicated.
     */
    suspend operator fun invoke(
        potentialTxn: PotentialTransaction,
        source: String = "Auto-Captured",
        travelSettings: TravelModeSettings? = null,
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
                    potentialTxn.originalMerchantName?.takeIf { it.isNotBlank() && !it.equals("Unknown Merchant", ignoreCase = true) }
                        ?: potentialTxn.merchantName?.takeIf { it.isNotBlank() && !it.equals("Unknown Merchant", ignoreCase = true) }
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
                                ?: TransactionType.EXPENSE,
                        sourceSmsId = potentialTxn.sourceSmsId,
                        sourceSmsHash = potentialTxn.sourceSmsHash,
                        source = source,
                        smsSignature = potentialTxn.smsSignature,
                        needsReview = potentialTxn.needsReview,
                    )

                val activeTravelSettings =
                    travelSettings ?: travelSettingsRepository?.getCurrentTravelModeSettings()
                val finalTags =
                    resolveTravelModeTagUseCase?.getFinalTags(
                        transactionDate = transactionToSave.date,
                        initialTags = emptySet(),
                        travelSettings = activeTravelSettings,
                    ) ?: emptySet()

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
        val isCurrentDeleted = transactionRepository.existsDeletedSmsHash(hash)
        val isLegacyDeleted = legacyHash.isNotBlank() && transactionRepository.existsDeletedSmsHash(legacyHash)
        if (isCurrentDeleted || isLegacyDeleted) {
            Log.d(TAG, "Transaction with sourceSmsHash was deleted by user. Skipping auto-save.")
            if (isLegacyDeleted) {
                transactionRepository.insertDeletedSmsHash(hash)
            }
            return true
        }

        return false
    }
}
