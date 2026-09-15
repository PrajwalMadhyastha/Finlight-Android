package io.pm.finlight.domain.usecase

import androidx.room.withTransaction
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Domain UseCase encapsulating reimbursement lifecycle operations:
 * - Linking an income transaction as reimbursement for an expense (handling exact, partial,
 *   and over-repayment surplus transaction creation).
 * - Unlinking a reimbursement (restoring parent expense amount, deleting any synthesized
 *   surplus transaction, and restoring full income amount).
 *
 * All state modifications run atomically inside a Room database transaction.
 */
class ManageReimbursementUseCase(
    private val transactionQueryDao: TransactionQueryDao,
    private val transactionWriteDao: TransactionWriteDao,
    private val transactionReimbursementDao: TransactionReimbursementDao,
    private val db: AppDatabase,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) {
    constructor(
        db: AppDatabase,
        dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    ) : this(
        transactionQueryDao = db.transactionQueryDao(),
        transactionWriteDao = db.transactionWriteDao(),
        transactionReimbursementDao = db.transactionReimbursementDao(),
        db = db,
        dispatcherProvider = dispatcherProvider,
    )

    /**
     * Links [incomeId] as a reimbursement for [expenseId]:
     * - If incomeTxn.amount > expenseTxn.amount (over-repayment):
     *   - Offsets expenseTxn to 0.0 (fully settled).
     *   - Adjusts incomeTxn amount to the offset portion, marks it isExcluded = true.
     *   - Creates an active surplus INCOME transaction for (incomeTxn.amount - offset).
     *   - Links the surplus transaction to the reimbursement income via linkedSurplusTxnId.
     * - Else:
     *   - Deducts the full income amount from the expense.
     *   - Marks incomeTxn as isExcluded = true and parentReimbursementId = expenseId.
     */
    suspend fun linkReimbursement(
        incomeId: Int,
        expenseId: Int,
    ) = withContext(dispatcherProvider.io) {
        db.withTransaction {
            if (incomeId == expenseId) return@withTransaction
            val incomeTxn = transactionQueryDao.getTransactionByIdSync(incomeId) ?: return@withTransaction
            val expenseTxn = transactionQueryDao.getTransactionByIdSync(expenseId) ?: return@withTransaction

            if (incomeTxn.transactionType != TransactionType.INCOME || expenseTxn.transactionType != TransactionType.EXPENSE) {
                return@withTransaction
            }
            if (incomeTxn.parentReimbursementId != null || incomeTxn.linkedSurplusTxnId != null) {
                return@withTransaction
            }

            if (expenseTxn.amount <= 0.0 || incomeTxn.amount <= 0.0) {
                return@withTransaction
            }

            if (incomeTxn.amount > expenseTxn.amount) {
                val offset = expenseTxn.amount
                val surplus = incomeTxn.amount - offset

                val surplusTxn =
                    Transaction(
                        description = "${incomeTxn.description} (Surplus)",
                        amount = surplus,
                        date = incomeTxn.date,
                        accountId = incomeTxn.accountId,
                        categoryId = incomeTxn.categoryId,
                        transactionType = TransactionType.INCOME,
                        isExcluded = false,
                        notes = "Surplus from repayment for ${expenseTxn.description}",
                        source = "Surplus Allocation",
                        status = TransactionStatus.CONFIRMED,
                    )
                val surplusId = transactionWriteDao.insert(surplusTxn).toInt()

                transactionWriteDao.updateAmount(incomeId, offset)
                transactionReimbursementDao.linkReimbursement(incomeId, expenseId, surplusId)
                transactionWriteDao.updateAmount(expenseId, 0.0)
            } else {
                transactionReimbursementDao.linkReimbursement(incomeId, expenseId, null)
                val newExpenseAmount = expenseTxn.amount - incomeTxn.amount
                transactionWriteDao.updateAmount(expenseId, newExpenseAmount)
            }
        }
    }

    /**
     * Removes the reimbursement link from [incomeId]:
     * - If a linked surplus transaction exists, deletes it and merges its amount back.
     * - Clears parentReimbursementId, linkedSurplusTxnId and removes the excluded flag.
     * - Adds the offset amount back onto the parent expense (if parent still exists).
     */
    suspend fun unlinkReimbursement(incomeId: Int) =
        withContext(dispatcherProvider.io) {
            db.withTransaction {
                val incomeTxn = transactionQueryDao.getTransactionByIdSync(incomeId) ?: return@withTransaction
                val parentId = incomeTxn.parentReimbursementId ?: return@withTransaction
                val expenseTxn = transactionQueryDao.getTransactionByIdSync(parentId)

                var totalIncomeToRestore = incomeTxn.amount
                val surplusId = incomeTxn.linkedSurplusTxnId
                if (surplusId != null) {
                    val surplusTxn = transactionQueryDao.getTransactionByIdSync(surplusId)
                    if (surplusTxn != null) {
                        totalIncomeToRestore += surplusTxn.amount
                        transactionWriteDao.delete(surplusTxn)
                    }
                }

                transactionWriteDao.updateAmount(incomeId, totalIncomeToRestore)
                transactionReimbursementDao.unlinkReimbursement(incomeId)
                if (expenseTxn != null) {
                    // Use incomeTxn.amount (read before any modification above) as the offset
                    // to add back onto the expense. At link time, exactly incomeTxn.amount was
                    // deducted from the expense (the "offset" portion). In an over-repayment
                    // scenario the surplus was split into a separate transaction, so
                    // incomeTxn.amount here already reflects just the offset — not the full
                    // original income. Adding it back precisely reverses the original deduction.
                    val restoredExpenseAmount = expenseTxn.amount + incomeTxn.amount
                    transactionWriteDao.updateAmount(parentId, restoredExpenseAmount)
                }
            }
        }
}
