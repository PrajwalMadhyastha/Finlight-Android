// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SplitTransactionRepository.kt
// REASON: NEW FILE - This repository abstracts the data source for split
// transactions. It provides a clean API for the ViewModel to fetch split items
// with their full category details from the SplitTransactionDao.
// =================================================================================
package io.pm.finlight

import kotlinx.coroutines.flow.Flow

class SplitTransactionRepository(private val splitTransactionDao: SplitTransactionDao) {
    fun getSplitsForParent(parentTransactionId: Int): Flow<List<SplitTransactionDetails>> {
        return splitTransactionDao.getSplitsForParent(parentTransactionId)
    }
}
