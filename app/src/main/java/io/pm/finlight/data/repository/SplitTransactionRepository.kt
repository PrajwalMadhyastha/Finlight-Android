package io.pm.finlight

import io.pm.finlight.data.TransactionRunner
import io.pm.finlight.data.db.dao.TransactionWriteDao
import kotlinx.coroutines.flow.Flow

class SplitTransactionRepository(
    private val splitTransactionDao: SplitTransactionDao,
    private val transactionWriteDao: TransactionWriteDao? = null,
    private val transactionRunner: TransactionRunner =
        object : TransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        },
) : ISplitTransactionRepository {
    override fun getSplitsForParent(parentTransactionId: Int): Flow<List<SplitTransactionDetails>> {
        return splitTransactionDao.getSplitsForParent(parentTransactionId)
    }

    override suspend fun insertAll(splits: List<SplitTransaction>) {
        splitTransactionDao.insertAll(splits)
    }

    override suspend fun saveSplits(
        parentTransactionId: Int,
        splits: List<SplitTransaction>,
    ) {
        transactionRunner.run {
            transactionWriteDao?.markAsSplit(parentTransactionId, true)
            splitTransactionDao.deleteSplitsForParent(parentTransactionId)
            splitTransactionDao.insertAll(splits)
        }
    }

    override suspend fun unmarkSplit(
        parentTransactionId: Int,
        originalDescription: String,
    ) {
        transactionRunner.run {
            val splits = splitTransactionDao.getSplitsForParentSimple(parentTransactionId)
            val firstSplitCategory = splits?.firstOrNull()?.splitTransaction?.categoryId
            splitTransactionDao.deleteSplitsForParent(parentTransactionId)
            transactionWriteDao?.unmarkAsSplit(parentTransactionId, originalDescription, firstSplitCategory)
        }
    }
}
