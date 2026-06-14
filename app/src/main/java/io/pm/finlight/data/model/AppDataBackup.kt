// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/model/AppDataBackup.kt
// REASON: FEATURE (Issue #104) - Added `goalTransactionLinks` field to include
// the new goal-transaction junction table in backup/restore operations.
// =================================================================================
package io.pm.finlight.data.model

import io.pm.finlight.*
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.data.db.entity.GoalTransactionLink
import io.pm.finlight.data.db.entity.Trip
import kotlinx.serialization.Serializable

/**
 * A top-level container for all application data to be exported.
 * This class is designed to be easily converted to a single JSON object.
 */
@Serializable
data class AppDataBackup(
    val transactions: List<Transaction>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val budgets: List<Budget>,
    val merchantMappings: List<MerchantMapping>,
    val splitTransactions: List<SplitTransaction> = emptyList(),
    // --- Phase 1: Core Parsing Intelligence ---
    val customSmsRules: List<CustomSmsRule> = emptyList(),
    val merchantRenameRules: List<MerchantRenameRule> = emptyList(),
    val merchantCategoryMappings: List<MerchantCategoryMapping> = emptyList(),
    val ignoreRules: List<IgnoreRule> = emptyList(),
    val smsParseTemplates: List<SmsParseTemplate> = emptyList(),
    // --- Phase 2: Remaining User & App Intelligence ---
    val tags: List<Tag> = emptyList(),
    val transactionTagCrossRefs: List<TransactionTagCrossRef> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalTransactionLinks: List<GoalTransactionLink> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val accountAliases: List<AccountAlias> = emptyList(),
    // --- Phase 3: App-Learned Recurring Patterns ---
    val recurringPatterns: List<RecurringPattern> = emptyList(),
)
