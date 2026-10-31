package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface ISplitTransactionRepository {
    fun getSplitsForParent(parentTransactionId: Int): Flow<List<SplitTransactionDetails>>

    suspend fun insertAll(splits: List<SplitTransaction>)

    suspend fun saveSplits(
        parentTransactionId: Int,
        splits: List<SplitTransaction>,
    )

    suspend fun unmarkSplit(
        parentTransactionId: Int,
        originalDescription: String,
    )
}
