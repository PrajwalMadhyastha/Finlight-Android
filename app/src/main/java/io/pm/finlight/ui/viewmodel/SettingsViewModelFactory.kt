// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SettingsViewModelFactory.kt
// REASON: REFACTOR (Testing) - The factory has been updated to instantiate all
// necessary repository dependencies and inject them into the SettingsViewModel's
// constructor, supporting the new dependency injection pattern.
// REASON: FEATURE (SMS Import) - The factory now instantiates and injects the
// SmsClassifier into the SettingsViewModel. This is a critical step to allow the
// bulk importer to use the ML model for pre-filtering non-transactional messages.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.pm.finlight.AccountRepository
import io.pm.finlight.CategoryRepository
import io.pm.finlight.MerchantMappingRepository
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.SmsRepository
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TagRepository
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ml.SmsClassifier

import io.pm.finlight.data.RoomTransactionRunner

class SettingsViewModelFactory(
    private val application: Application,
    private val transactionViewModel: TransactionViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val settingsRepository = SettingsRepository(application)
            val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
            val transactionRepository =
                TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)
            val merchantMappingRepository = MerchantMappingRepository(db.merchantMappingDao())
            val accountRepository = AccountRepository(db)
            val categoryRepository = CategoryRepository(db.categoryDao())
            val smsRepository = SmsRepository(application)
            val smsClassifier = SmsClassifier(application)
            val transactionRunner = RoomTransactionRunner()

            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                application,
                settingsRepository,
                db,
                transactionRepository,
                merchantMappingRepository,
                accountRepository,
                categoryRepository,
                smsRepository,
                transactionViewModel,
                smsClassifier,
                transactionRunner
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}