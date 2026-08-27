package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface ISplitTransactionRepository {
    fun getSplitsForParent(parentTransactionId: Int): Flow<List<SplitTransactionDetails>>
}
