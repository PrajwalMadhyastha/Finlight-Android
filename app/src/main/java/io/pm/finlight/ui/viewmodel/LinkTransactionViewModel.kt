// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/LinkTransactionViewModel.kt
// REASON: REFACTOR - The transaction matching logic has been updated. Instead
// of searching for specific matches, the ViewModel now fetches all transactions
// from the last two days (today and yesterday), providing the user with a
// broader, more useful list of candidates to link their recurring payment to.
// FIX - The instantiation of TransactionRepository has been updated to include
// the required dependencies, resolving a build error.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class LinkTransactionViewModelFactory(
    private val application: Application,
    private val potentialTransaction: PotentialTransaction
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LinkTransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LinkTransactionViewModel(application, potentialTransaction) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class LinkTransactionViewModel(
    application: Application,
    val potentialTransaction: PotentialTransaction
) : AndroidViewModel(application) {

    private val transactionRepository: TransactionRepository
    private val recurringTransactionDao: RecurringTransactionDao

    private val _linkableTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val linkableTransactions = _linkableTransactions.asStateFlow()

    init {
        val db = AppDatabase.getInstance(application)
        val settingsRepository = SettingsRepository(application)
        val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
        transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)
        recurringTransactionDao = db.recurringTransactionDao()
        findMatches()
    }

    private fun findMatches() {
        viewModelScope.launch {
            val todayEnd = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.timeInMillis

            val yesterdayStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis

            // Use the existing DAO method to get all transactions in the date range.
            // This returns a Flow, so we collect it to update our state.
            transactionRepository.getAllTransactionsForRange(yesterdayStart, todayEnd)
                .collect { transactions ->
                    _linkableTransactions.value = transactions
                }
        }
    }

    fun linkTransaction(selectedTransactionId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Link the transaction by setting its hash
            potentialTransaction.sourceSmsHash?.let { hash ->
                transactionRepository.setSmsHash(selectedTransactionId, hash)
            }
            // Update the last run date of the rule to today
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            recurringTransactionDao.updateLastRunDate(ruleId, System.currentTimeMillis())
            onComplete()
        }
    }

    fun remindTomorrow(onComplete: () -> Unit) {
        viewModelScope.launch {
            // To "remind tomorrow", we simply update the last run date to yesterday.
            // This makes the rule due again starting today.
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            recurringTransactionDao.updateLastRunDate(ruleId, yesterday)
            onComplete()
        }
    }
}