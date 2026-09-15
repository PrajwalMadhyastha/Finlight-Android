package io.pm.finlight.domain.usecase

import android.content.Context
import io.pm.finlight.Account
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionRepository
import io.pm.finlight.core.utils.StringSimilarity
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountAliasDao
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/**
 * Domain UseCase to automatically detect and link self-transfers between user accounts.
 *
 * Implements a two-tiered matching heuristic:
 * 1. Strict Time (<= 5 mins): Matches on exact Amount and opposite TransactionType.
 * 2. Loose Time (<= 6 hours): Matches on Amount, opposite TransactionType, AND fuzzy
 *    text validation (account alias digits, alias token overlap > 0.6, bank name token overlap > 0.6,
 *    or presence of transfer keywords like "neft", "imps", "transfer").
 */
class DetectSelfTransferUseCase(
    private val transactionRepository: ITransactionRepository,
    private val accountDao: AccountDao,
    private val accountAliasDao: AccountAliasDao,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) {
    constructor(
        context: Context,
        db: AppDatabase = AppDatabase.getInstance(context),
    ) : this(
        transactionRepository = ServiceLocator.provideTransactionRepository(context),
        accountDao = db.accountDao(),
        accountAliasDao = db.accountAliasDao(),
        dispatcherProvider = ServiceLocator.provideDispatcherProvider(context),
    )

    @Deprecated(
        message = "Pass ITransactionRepository directly to primary constructor or provide Context to resolve via ServiceLocator",
        level = DeprecationLevel.WARNING,
    )
    constructor(
        db: AppDatabase,
        dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    ) : this(
        transactionRepository =
            TransactionRepository(
                transactionWriteDao = db.transactionWriteDao(),
                transactionQueryDao = db.transactionQueryDao(),
                transactionAnalyticsDao = db.transactionAnalyticsDao(),
                transactionReimbursementDao = db.transactionReimbursementDao(),
                db = db,
                dispatcherProvider = dispatcherProvider,
            ),
        accountDao = db.accountDao(),
        accountAliasDao = db.accountAliasDao(),
        dispatcherProvider = dispatcherProvider,
    )

    suspend operator fun invoke(newTxn: Transaction) =
        withContext(dispatcherProvider.io) {
            if (newTxn.sourceSmsId == null || newTxn.linkedTransferId != null || newTxn.isExcluded || newTxn.isSplit) {
                return@withContext
            }

            // 6-hour window
            val windowMs = 6 * 60 * 60 * 1000L
            val startTime = newTxn.date - windowMs
            val endTime = newTxn.date + windowMs

            val candidates =
                transactionRepository.findPotentialTransfers(
                    amount = newTxn.amount,
                    accountId = newTxn.accountId,
                    transactionType = newTxn.transactionType,
                    startTime = startTime,
                    endTime = endTime,
                )

            val newTxnDesc = (newTxn.originalDescription ?: newTxn.description).lowercase(Locale.ROOT)
            var newTxnAliases: List<AccountAlias>? = null
            var newTxnAccount: Account? = null
            var fetchedNewTxnAccount = false

            for (candidate in candidates) {
                if (candidate.isSplit) {
                    continue
                }

                val timeDiff = abs(candidate.date - newTxn.date)
                var isMatch = false

                // Tier 2: Strict Time (<= 5 minutes)
                if (timeDiff <= 5 * 60 * 1000L) {
                    isMatch = true
                } else {
                    // Tier 1: Text Validation
                    val currentNewTxnAliases =
                        newTxnAliases ?: accountAliasDao.getAliasesForAccount(newTxn.accountId).also {
                            newTxnAliases = it
                        }
                    if (!fetchedNewTxnAccount) {
                        newTxnAccount = accountDao.getAccountByIdSync(newTxn.accountId)
                        fetchedNewTxnAccount = true
                    }

                    val candidateAliases = accountAliasDao.getAliasesForAccount(candidate.accountId)
                    val candidateDesc = (candidate.originalDescription ?: candidate.description).lowercase(Locale.ROOT)

                    // Extract digits from alias and check, or use token overlap
                    val candidateAliasMatches =
                        candidateAliases.any { alias ->
                            val digits = alias.aliasName.filter { it.isDigit() }
                            (digits.isNotEmpty() && newTxnDesc.contains(digits)) ||
                                StringSimilarity.calculateTokenOverlapScore(alias.aliasName, newTxnDesc) > 0.6
                        }

                    val newTxnAliasMatches =
                        currentNewTxnAliases.any { alias ->
                            val digits = alias.aliasName.filter { it.isDigit() }
                            (digits.isNotEmpty() && candidateDesc.contains(digits)) ||
                                StringSimilarity.calculateTokenOverlapScore(alias.aliasName, candidateDesc) > 0.6
                        }

                    val candidateAccount = accountDao.getAccountByIdSync(candidate.accountId)

                    val candidateBankNameMatches =
                        candidateAccount?.name?.let {
                            StringSimilarity.calculateTokenOverlapScore(it, newTxnDesc) > 0.6
                        } == true
                    val newTxnBankNameMatches =
                        newTxnAccount?.name?.let {
                            StringSimilarity.calculateTokenOverlapScore(it, candidateDesc) > 0.6
                        } == true

                    // Extra check for keywords we discussed
                    val containsKeywords1 =
                        newTxnDesc.contains("neft") || newTxnDesc.contains("imps") || newTxnDesc.contains("transfer")
                    val containsKeywords2 =
                        candidateDesc.contains("neft") || candidateDesc.contains("imps") || candidateDesc.contains("transfer")

                    if (candidateAliasMatches || newTxnAliasMatches || candidateBankNameMatches || newTxnBankNameMatches || (containsKeywords1 && containsKeywords2)) {
                        isMatch = true
                    }
                }

                if (isMatch) {
                    // Link them atomically
                    transactionRepository.linkTransfer(newTxn.id, candidate.id)
                    break // Only link the first match
                }
            }
        }

    suspend fun detectAndLinkSelfTransfer(newTxn: Transaction) = invoke(newTxn)
}
